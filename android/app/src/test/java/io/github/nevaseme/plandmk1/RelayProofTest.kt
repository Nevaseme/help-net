package io.github.nevaseme.plandmk1

import io.github.nevaseme.plandmk1.cloud.*
import io.github.nevaseme.plandmk1.relay.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec

class RelayProofTest {
    private val group="relay-test"
    private val signer=object:RelaySigner {
        private val pair=KeyPairGenerator.getInstance("EC").apply {initialize(ECGenParameterSpec("secp256r1"))}.generateKeyPair()
        override val publicKeySpki=encode64(pair.public.encoded)
        override fun sign(body:ByteArray)=Signature.getInstance("SHA256withECDSA").run {initSign(pair.private);update(body);sign()}
    }
    private fun record(text:String="Original")=CloudRecord("residents",buildJsonObject {
        put("groupId",group);put("originId","author-a");put("revision",1);put("name",text)
    })
    @Test fun cloudUploadAlwaysIncludesProofVersionEvenWhenDefaultValuesAreOmitted() {
        val envelope=RelayProofs.create(group,"author-a",record(),signer)
        val wire=Json.encodeToJsonElement(CloudPush(listOf(RelayProofs.verify(group,envelope)))).jsonObject
        assertEquals(1,wire.getValue("records").jsonArray.single().jsonObject.getValue("relayProof").jsonObject["version"]?.jsonPrimitive?.int)
    }
    @Test fun oversizedRecordCannotBecomeAnUnuploadableSignedProof() {
        assertFalse(RelayProofs.eligible(record("x".repeat(24001))))
        try {RelayProofs.create(group,"author-a",record("x".repeat(24001)),signer);fail("oversize")}catch(_:IllegalArgumentException){}
    }
    @Test fun signaturePreservesOriginalAuthorBodyAndRejectsModificationAndCrossGroupReplay() {
        val envelope=RelayProofs.create(group,"author-a",record(),signer)
        assertEquals("author-a",RelayProofs.verify(group,envelope).owner())
        assertEquals("Original",RelayProofs.verify(group,envelope).record["name"]!!.jsonPrimitive.content)
        val altered=envelope.copy(proof=envelope.proof.copy(body=envelope.proof.body.replace("Original","Altered")))
        for(candidate in listOf(altered,envelope.copy(publicKeySpki=signer.publicKeySpki.dropLast(4)))) {
            try {RelayProofs.verify(group,candidate);fail("invalid proof accepted")}catch(_:Exception){}
        }
        try {RelayProofs.verify("other-group",envelope);fail("cross-group replay")}catch(_:IllegalArgumentException){}
        try {RelayProofs.create(group,"relay-b",record(),signer);fail("foreign signing")}catch(_:IllegalArgumentException){}
    }
    private class Repo(val items:MutableList<RelayEnvelope>):RelayRepository {
        override suspend fun envelopes()=items.toList()
        override suspend fun get(hash:String)=items.find {it.id()==hash}
        override suspend fun accept(envelope:RelayEnvelope):Boolean {RelayProofs.verify("relay-test",envelope);if(get(envelope.id())!=null)return false;items+=envelope;return true}
    }
    @Test fun largeProofUsesBoundedFramesAndMissingChunkRecoversFromRepeatedSummary()=runTest {
        val original=RelayProofs.create(group,"author-a",record("\\".repeat(10000)),signer)
        val a=Repo(mutableListOf(original));val b=Repo(mutableListOf())
        val packets=ArrayDeque<Pair<Boolean,ByteArray>>();var receipts=0
        val sender=RelayExchange(group,a,{_,bytes->assertTrue(bytes.size<=24000);packets.add(true to bytes)})
        val receiver=RelayExchange(group,b,{_,bytes->assertTrue(bytes.size<=24000);packets.add(false to bytes)},{receipts++})
        suspend fun drain(drop:Boolean) {
            var lost=false
            while(packets.isNotEmpty()) {
                val (fromA,bytes)=packets.removeFirst()
                if(drop && fromA && !lost && String(bytes).contains("RELAY_CHUNK")){lost=true;continue}
                if(fromA)receiver.receive("a",bytes) else sender.receive("b",bytes)
            }
        }
        sender.summary("b");drain(true)
        assertTrue(b.items.isEmpty());assertEquals(0,receipts)
        sender.summary("b");drain(false)
        assertEquals(original,b.items.single());assertEquals(1,receipts)
        sender.summary("b");drain(false);assertEquals(1,receipts)
    }
}
