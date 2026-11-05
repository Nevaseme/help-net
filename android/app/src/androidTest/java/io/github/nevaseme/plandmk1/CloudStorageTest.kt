package io.github.nevaseme.plandmk1

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.github.nevaseme.plandmk1.cloud.*
import io.github.nevaseme.plandmk1.core.*
import io.github.nevaseme.plandmk1.data.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.Protocol
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.IOException

/** Unique fictional databases/preferences only; never selects or modifies the user's group. */
class CloudStorageTest {
    private fun id()=UUID.randomUUID().toString()
    @Test fun correctedInvitationCanRetryButUncertainResponseRetainsSameRegistration()=runBlocking {
        val context=ApplicationProvider.getApplicationContext<Context>();val prefsName="cloud-${id()}";val me=id();val group=id()
        try {
            val settings=CloudSettings(context,prefsName)
            fun begin()=settings.begin("https://example.invalid",me,"架空",groupId=group,code="bad-code")
            fun api(status:Int)=CloudHttpApi(OkHttpClient.Builder().addInterceptor {chain->
                if(status==0)throw IOException("offline")
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(status).message("fixture").body("{\"error\":{\"code\":\"fixture\"}}".toResponseBody()).build()
            }.build())
            for(status in listOf(400,403,404,409)) {
                val pending=begin()
                try {settings.submit(pending,api(status));fail()}catch(_:CloudHttpException){}
                assertNull(settings.pending())
            }
            val pending=begin()
            for(status in listOf(0,500,503)) {
                try {settings.submit(pending,api(status));fail()}catch(_:IOException){}
                assertEquals(pending,CloudSettings(context,prefsName).pending())
            }
        }finally {context.getSharedPreferences(prefsName,0).edit().clear().commit()}
    }
    @Test fun incomingActualIncidentRepairsSelectionAfterInterruptionButDuplicatesRespectHistory()=runBlocking {
        val context=ApplicationProvider.getApplicationContext<Context>();val dbName="cloud-${id()}.db";val prefsName="cloud-${id()}"
        val me=id();val group=id();val roster=MemberRoster(groupId=group,members=listOf(GroupMember(me,"架空管理者",MemberRole.ADMIN,listOf(me))))
        val session=CloudSession("https://example.invalid",group,me,me,"0".repeat(64),MemberRole.ADMIN)
        var selected="history";var selectionFails=true
        val db=Room.databaseBuilder(context,PlanDatabase::class.java,dbName).build()
        try {
            RoomCloudJournal(db,group,me).updateRoster(CloudRosterEnvelope(group,1,roster,listOf(CloudRosterSnapshot(1,roster))),me)
            val evidence=EewEvidence("wolfx","20991030120000",100,101,true)
            val proof=QuakeEvent(group,id(),me,101,QuakeAction.EEW,false,targetId=evidence.incidentId(),eew=evidence)
            val row=CloudRecord("quakes",Json.encodeToJsonElement(proof).jsonObject,1)
            fun journal()=RoomCloudJournal(db,group,me,CloudSettings(context,prefsName).selection(session,{selected},{if(selectionFails)error("selection interruption");selected=it}))
            try {journal().mergePage(listOf(row));fail("selection interruption")}catch(_:IllegalStateException){}
            assertEquals(1,QuakeStore(db).events(group).size);assertEquals("history",selected)
            selectionFails=false;journal().mergePage(listOf(row))
            assertEquals(evidence.incidentId(),selected)
            selected="history";journal().mergePage(listOf(row));assertEquals("history",selected)
            val end=QuakeEvent(group,id(),me,102,QuakeAction.END,false,targetId=evidence.incidentId(),endedIncidentIds=listOf(evidence.incidentId()))
            journal().mergePage(listOf(CloudRecord("quakes",Json.encodeToJsonElement(end).jsonObject,1)))
            journal().mergePage(listOf(row));assertEquals("history",selected);assertTrue(QuakeStore(db).states(group).single().closed)
        }finally {db.close();context.deleteDatabase(dbName);context.getSharedPreferences(prefsName,0).edit().clear().commit()}
    }
    @Test fun pageRollbackThenReopenKeepsHistoryAndProfileWithDurableCursor()=runBlocking {
        val context=ApplicationProvider.getApplicationContext<Context>();val dbName="cloud-${id()}.db";val prefsName="cloud-${id()}"
        val me=id();val group=id();val roster=MemberRoster(groupId=group,members=listOf(GroupMember(me,"架空管理者",MemberRole.ADMIN,listOf(me))))
        val envelope=CloudRosterEnvelope(group,1,roster,listOf(CloudRosterSnapshot(1,roster)))
        val session=CloudSession("https://example.invalid",group,me,me,"0".repeat(64),MemberRole.ADMIN)
        fun open()=Room.databaseBuilder(context,PlanDatabase::class.java,dbName).build()
        var db=open()
        try {
            val journal=RoomCloudJournal(db,group,me);journal.updateRoster(envelope,me)
            val profile=Resident(group,me,1,1,"架空管理者",35.0,135.0,"1階","試験")
            val valid=CloudRecord("residents",Json.encodeToJsonElement(profile).jsonObject,1)
            val invalid=CloudRecord("residents",Json.encodeToJsonElement(profile.copy(originId=id(),latitude=999.0)).jsonObject,1)
            try{journal.mergePage(listOf(valid,invalid));fail("invalid page")}catch(_:IllegalArgumentException){}
            assertTrue(RoomRecordStore(db).all(group).isEmpty())
            journal.mergePage(listOf(valid));CloudSettings(context,prefsName).checkpoint(session).commitPage(1,listOf(valid))
            assertEquals("UNREPORTED",journal.records().single {it.collection=="residents"}.record["safety"]?.jsonPrimitive?.content)
            db.close();db=open()
            assertEquals(profile,RoomRecordStore(db).get(group,me));assertEquals(1L,QuakeStore(db).roster(group)!!.revision)
            assertEquals(roster.members,QuakeStore(db).roster(group)!!.membersAt(1));assertEquals(1L,CloudSettings(context,prefsName).checkpoint(session).cursor())
        }finally{db.close();context.deleteDatabase(dbName);context.getSharedPreferences(prefsName,0).edit().clear().commit()}
    }
    @Test fun reversedRevisionsKeepHighestAckAndRegistrationRetryKeepsIdentitySecret() {
        val context=ApplicationProvider.getApplicationContext<Context>();val prefsName="cloud-${id()}";val me=id();val group=id()
        try {
            val settings=CloudSettings(context,prefsName)
            val pending=settings.begin("https://example.invalid",me,"架空","架空グループ")
            assertEquals(pending,CloudSettings(context,prefsName).begin("https://example.invalid",me,"架空","架空グループ"))
            assertEquals(me,pending.body.getValue("deviceId").jsonPrimitive.content)
            val session=CloudSession(pending.endpoint,group,me,me,pending.body.getValue("registrationToken").jsonPrimitive.content,MemberRole.ADMIN)
            settings.complete(session)
            assertNotNull(settings.pending()) // selected-group persistence must finish before removal.
            assertEquals(session,CloudSettings(context,prefsName).session(group))
            val newer=CloudRecord("residents",buildJsonObject{put("originId",me);put("revision",3)})
            val older=CloudRecord("residents",buildJsonObject{put("originId",me);put("revision",2)})
            settings.checkpoint(session).commitPage(2,listOf(newer,older))
            assertTrue(CloudSettings(context,prefsName).checkpoint(session).acknowledged(newer))
            settings.cancelRejectedRegistration();assertNull(settings.pending())
        }finally {context.getSharedPreferences(prefsName,0).edit().clear().commit()}
    }
}
