package io.github.nevaseme.plandmk1

import io.github.nevaseme.plandmk1.cloud.CloudRecord
import io.github.nevaseme.plandmk1.core.*
import io.github.nevaseme.plandmk1.relay.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.UUID

/** Twenty production proof exchangers over simulated links, not a radio/Room performance test. */
class RelayAudienceTest {
    private val group=id("group")
    private val incident=id("incident")
    private val json=Json {encodeDefaults=true}
    private fun id(text:String)=UUID.nameUUIDFromBytes(text.toByteArray()).toString()
    private fun signer()=object:RelaySigner {
        private val pair=KeyPairGenerator.getInstance("EC").apply {initialize(ECGenParameterSpec("secp256r1"))}.generateKeyPair()
        override val publicKeySpki=encode64(pair.public.encoded)
        override fun sign(body:ByteArray)=Signature.getInstance("SHA256withECDSA").run {initSign(pair.private);update(body);sign()}
    }
    private inner class Repo:RelayRepository {
        val rows=linkedMapOf<String,RelayEnvelope>()
        override suspend fun envelopes()=rows.values.toList()
        override suspend fun get(hash:String)=rows[hash]
        override suspend fun accept(envelope:RelayEnvelope):Boolean {
            RelayProofs.verify(group,envelope)
            if(envelope.id() in rows)return false
            rows[envelope.id()]=envelope
            return true
        }
    }
    private data class Packet(val from:Int,val to:Int,val bytes:ByteArray)
    @Test fun twentyAuthorsKeepSafetyAndReportsAcrossPartitionAndLaterForwarding()=runTest {
        val repos=List(20){Repo()};val keys=List(20){signer()}
        val packets=ArrayDeque<Packet>();val receipts=IntArray(20)
        val links=(0 until 19).map {it to it+1}.toMutableSet()
        fun edge(a:Int,b:Int)=minOf(a,b) to maxOf(a,b)
        fun exchange(n:Int)=RelayExchange(group,repos[n],{peer,bytes->
            val to=peer.toInt();assertTrue(edge(n,to) in links);assertTrue(bytes.size<=24000)
            packets+=Packet(n,to,bytes)
        },{receipts[n]++})
        val engines=MutableList(20){exchange(it)}
        val originals=linkedMapOf<String,RelayEnvelope>()
        suspend fun add(n:Int,record:CloudRecord) {
            val proof=RelayProofs.create(group,id("person$n"),record,keys[n])
            repos[n].accept(proof);originals[proof.id()]=proof
        }
        suspend fun answer(n:Int,seq:Long,safety:Safety) {
            val event=IncidentEvent(group,id("answer$n-$seq"),incident,id("person$n"),"架空$n",seq,1000+seq,IncidentKind.ANSWER,safety=safety)
            add(n,CloudRecord("incidents",json.encodeToJsonElement(event).jsonObject))
        }
        suspend fun settle() {
            repeat(20) {
                for((a,b) in links){engines[a].summary(b.toString());engines[b].summary(a.toString())}
                var delivered=0
                while(packets.isNotEmpty()) {
                    assertTrue("Proof exchange must finish",delivered++<20000)
                    val packet=packets.removeFirst()
                    engines[packet.to].receive(packet.from.toString(),packet.bytes)
                }
            }
        }
        for(n in 0 until 20) {
            answer(n,1,Safety.SOS)
            val report=id("report$n")
            val event=ResponseEvent(group,report,id("person$n"),1,1002,incident,
                ResponseChange.Report(report,"倒木",35.0+n/10000.0,135.0,"架空通報$n"))
            add(n,CloudRecord("responses",json.encodeToJsonElement(event).jsonObject))
        }
        settle()
        repos.forEach {assertEquals(40,it.rows.size)}
        links.remove(9 to 10)
        engines[9].disconnected("10");engines[10].disconnected("9")
        for(n in 0 until 20)answer(n,2,Safety.SAFE)
        settle()
        repos.forEach {assertEquals("Only the ten same-side new answers can arrive while partitioned",50,it.rows.size)}
        // Restart the exchange with its already saved envelope set, then reconnect the two halves.
        engines[9]=exchange(9);links.add(9 to 10)
        settle()
        repos.forEach {repo->
            assertEquals(originals,repo.rows)
            val records=repo.rows.values.map {RelayProofs.verify(group,it)}
            assertEquals(20,records.count {it.collection=="responses"})
            assertEquals(20,records.count {it.record["safety"]?.jsonPrimitive?.content=="SAFE"})
            for(n in 0 until 20)assertEquals(3,records.count {it.owner()==id("person$n")})
        }
        val before=receipts.toList();settle();assertEquals("Repeated announcements must not duplicate durable receipts",before,receipts.toList())
    }
}
