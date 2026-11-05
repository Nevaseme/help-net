package io.github.nevaseme.plandmk1

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import io.github.nevaseme.plandmk1.cloud.*
import io.github.nevaseme.plandmk1.core.*
import io.github.nevaseme.plandmk1.data.*
import io.github.nevaseme.plandmk1.nearby.*
import io.github.nevaseme.plandmk1.relay.*
import io.github.nevaseme.plandmk1.sharing.SharingSession
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

/** Fictional A/B installations, real Keystore, Room, sharing actor and deployed HTTPS endpoint. */
class RelayHttpsTest {
    private fun id()=UUID.randomUUID().toString()
    private class Port:NearbyPort {
        override val state=MutableStateFlow(NearbyState())
        override var onConnected:suspend (String)->Unit={}
        override var onDisconnected:(String)->Unit={}
        override var onBytes:(String,ByteArray)->Unit={_,_->}
        lateinit var other:Port
        val errors=java.util.concurrent.CopyOnWriteArrayList<String>()
        override fun start(name:String){state.value=NearbyState(active=true)}
        override fun setVisible(visible:Boolean){}
        override fun stop(){state.value=NearbyState()}
        override fun connect(peer:Peer){}
        override fun verify(id:String,accept:Boolean){}
        override suspend fun send(peer:String,bytes:ByteArray){check(bytes.size<=24000);other.onBytes("peer",bytes)}
        override fun error(message:String){errors+=message}
        override fun receipt(){}
    }
    @Test fun offlineAuthorLeavesRecipientRestartsAndRelaysSafetyAndReportToHttps()=runBlocking<Unit> {
        val endpoint=InstrumentationRegistry.getArguments().getString("cloudEndpoint").orEmpty()
        assumeTrue("Explicit fictional server endpoint required",endpoint.isNotBlank())
        val context=ApplicationProvider.getApplicationContext<Context>();val api=CloudHttpApi();val signer=AndroidRelaySigner()
        val a=id();val b=id();val names=listOf("relay-https-a-${id()}","relay-https-b-${id()}")
        val prefsA=CloudSettings(context,names[0]);val prefsB=CloudSettings(context,names[1])
        val dbA=Room.databaseBuilder(context,PlanDatabase::class.java,names[0]+".db").build()
        fun openB()=Room.databaseBuilder(context,PlanDatabase::class.java,names[1]+".db").build()
        var dbB=openB();val actors=CoroutineScope(SupervisorJob()+Dispatchers.Default)
        var sharingA:SharingSession?=null;var sharingB:SharingSession?=null
        try {
            val requestB=prefsB.begin(endpoint,b,"架空中継B","署名中継・自動試験")
            val sessionB=prefsB.submit(requestB,api).session(endpoint);prefsB.complete(sessionB)
            val invitation=api.invitation(sessionB)
            val requestA=prefsA.begin(endpoint,a,"架空作成者A",groupId=sessionB.groupId,code=invitation.code)
            val sessionA=prefsA.submit(requestA,api).session(endpoint);prefsA.complete(sessionA)
            val group=sessionB.groupId
            api.registerRelayKey(sessionA,signer.keyId(),signer.publicKeySpki)
            assertEquals(signer.keyId(),api.relayKeys(sessionB).keys.single {it.deviceId==a}.keyId)
            val at=System.currentTimeMillis()
            RoomRecordStore(dbA,a).merge(Resident(group,a,1,at,"架空作成者A",35.0,135.0,"1階","架空",Safety.UNREPORTED))
            RoomRecordStore(dbB,b).merge(Resident(group,b,1,at,"架空中継B",35.0,135.0,"1階","架空",Safety.UNREPORTED))
            suspend fun syncA()=CloudSyncEngine(sessionA,api,RoomCloudJournal(dbA,group,a),prefsA.checkpoint(sessionA)).sync()
            suspend fun syncB()=CloudSyncEngine(sessionB,api,RoomCloudJournal(dbB,group,b),prefsB.checkpoint(sessionB)).sync()
            syncB();syncA();syncB()
            QuakeStore(dbB).command(QuakeEvent(group,id(),b,at+1,QuakeAction.TRAINING_START,true,title="架空中継訓練",area="架空地域"))
            syncB();syncA()
            val incident=QuakeStore(dbA).states(group).single().formation.incidentId
            // A loses Internet here. Only nearby frames are allowed until A is removed.
            val answer=RoomRecordStore(dbA,a).appendIncident(IncidentEvent(group,id(),incident,a,"架空作成者A",1,at+2,IncidentKind.ANSWER,safety=Safety.SAFE))
            val reportId=id()
            val report=ResponseStore(dbA).append(ResponseEvent(group,reportId,a,1,at+3,incident,
                ResponseChange.Report(reportId,"倒木",35.0,135.0,"架空の倒木。署名中継の自動試験です。")))
            val portA=Port();val portB=Port();portA.other=portB;portB.other=portA
            val receipts=AtomicInteger()
            sharingA=SharingSession(group,a,portA,RoomRecordStore(dbA,a),actors,{}, {},automatic=true,
                relayRepository=RoomRelayRepository(dbA,group,a,signer))
            sharingB=SharingSession(group,b,portB,RoomRecordStore(dbB,b),actors,{}, {},automatic=true,
                relayRepository=RoomRelayRepository(dbB,group,b,signer,canSign={false}),onDurableReceipt={receipts.incrementAndGet()})
            sharingA.start("A");sharingB.start("B");portA.onConnected("peer");portB.onConnected("peer")
            withTimeout(30_000) {
                while(true) {
                    val outgoing=RoomCloudJournal(dbB,group,b).records()
                    if(outgoing.count {it.key() in setOf(answer.eventId,report.eventId)&&it.relayProof!=null}==2)break
                    delay(100)
                }
            }
            assertTrue(receipts.get()>=2)
            assertTrue(portA.errors.toString(),portA.errors.isEmpty());assertTrue(portB.errors.toString(),portB.errors.isEmpty())
            sharingA.stop();sharingB.stop();actors.coroutineContext[Job]!!.cancelAndJoin()
            dbA.close() // A is absent for every subsequent HTTPS operation.
            dbB.close();dbB=openB()
            val pending=RoomCloudJournal(dbB,group,b).records().filter {it.key() in setOf(answer.eventId,report.eventId)}
            assertEquals(2,pending.size);assertTrue(pending.all {it.owner()==a&&it.relayProof!=null})
            val result=syncB();assertEquals(0,result.rejected);assertEquals(0,result.unsentForeign);assertTrue(result.uploaded>=2)
            val remote=api.pull(sessionB,0).records
            val storedAnswer=remote.single {it.collection=="incidents"&&it.record["eventId"]?.jsonPrimitive?.content==answer.eventId}
            val storedReport=remote.single {it.collection=="responses"&&it.record["eventId"]?.jsonPrimitive?.content==report.eventId}
            assertEquals(a,storedAnswer.record["authorId"]!!.jsonPrimitive.content)
            assertEquals("SAFE",storedAnswer.record["safety"]!!.jsonPrimitive.content)
            assertEquals(a,storedReport.record["actorDeviceId"]!!.jsonPrimitive.content)
            // Private test output lets the companion browser test inspect this same fictional group.
            File(context.filesDir,"relay-https-fixture.json").writeText(buildJsonObject {
                put("endpoint",endpoint);put("groupId",group);put("deviceId",b);put("memberId",sessionB.memberId);put("token",sessionB.token)
                put("authorId",a);put("answerId",answer.eventId);put("reportId",report.eventId);put("incidentId",incident)
            }.toString())
        }finally {
            sharingA?.stop();sharingB?.stop();actors.cancel();dbA.close();dbB.close()
            names.forEach {context.deleteDatabase(it+".db");context.getSharedPreferences(it,0).edit().clear().commit()}
        }
    }
}
