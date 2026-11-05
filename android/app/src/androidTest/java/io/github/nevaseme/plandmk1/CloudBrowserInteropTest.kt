package io.github.nevaseme.plandmk1

import android.content.Context
import android.os.Bundle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import io.github.nevaseme.plandmk1.cloud.*
import io.github.nevaseme.plandmk1.core.*
import io.github.nevaseme.plandmk1.data.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.UUID

/** Paired with the real browser client. Only fictional group invitation arguments are accepted. */
class CloudBrowserInteropTest {
    @Test fun browserAdministratorAndAndroidMemberPreserveAnswersThroughEndAndReopen()=runBlocking<Unit> {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val arguments=InstrumentationRegistry.getArguments()
        val endpoint=arguments.getString("cloudEndpoint").orEmpty()
        val invitation=arguments.getString("browserInvite").orEmpty().split(':')
        assumeTrue("Explicit fictional browser fixture invitation required",endpoint.isNotBlank()&&invitation.size==2)
        val context=ApplicationProvider.getApplicationContext<Context>()
        val name="cloud-browser-${UUID.randomUUID()}";val device=UUID.randomUUID().toString();val group=invitation[0]
        fun open()=Room.databaseBuilder(context,PlanDatabase::class.java,"$name.db").build()
        var db=open();val settings=CloudSettings(context,name);val api=CloudHttpApi()
        fun stage(value:String){instrumentation.sendStatus(2,Bundle().apply {putString("cloudStage",value)})}
        try {
            val pending=settings.begin(endpoint,device,"架空Android",groupId=group,code=invitation[1])
            val registration=settings.submit(pending,api);assertEquals(MemberRole.MEMBER,registration.role);assertEquals(device,registration.deviceId)
            val session=registration.session(endpoint);settings.complete(session)
            val journal=RoomCloudJournal(db,group,device);journal.updateRoster(registration.envelope(),device)
            RoomRecordStore(db).merge(Resident(group,device,1,System.currentTimeMillis(),"架空Android",35.0,135.0,"1階","架空相互接続試験",Safety.UNREPORTED))
            QuakeStore(db).command(QuakeEvent(group,UUID.randomUUID().toString(),device,System.currentTimeMillis(),QuakeAction.REPORT,false,title="架空相互接続試験",area="架空地区"))
            suspend fun sync()=CloudSyncEngine(session,api,journal,settings.checkpoint(session)).sync()
            sync();stage("android_profile_report_uploaded")
            suspend fun await(condition:suspend ()->Boolean)=withTimeout(60_000) {
                while(true){sync();if(condition())break;delay(1_000)}
            }
            await {
                RoomRecordStore(db).all(group).any {it.originId!=device}&&
                    QuakeStore(db).states(group).any {!it.closed&&!it.formation.training}&&
                    RoomRecordStore(db).incidents(group).any {it.kind==IncidentKind.ANSWER&&it.authorId!=device&&it.safety==Safety.SOS}
            }
            val state=QuakeStore(db).states(group).single {!it.closed&&!it.formation.training}
            val incident=state.formation.incidentId
            val otherAnswer=RoomRecordStore(db).incidents(group).single {it.kind==IncidentKind.ANSWER&&it.authorId!=device&&it.safety==Safety.SOS}
            assertTrue(otherAnswer.incidentId in state.incidentIds);stage("browser_sos_received")
            val answer=RoomRecordStore(db).appendIncident(IncidentEvent(group,UUID.randomUUID().toString(),incident,device,"架空Android",1,System.currentTimeMillis(),IncidentKind.ANSWER,safety=Safety.SAFE))
            sync();stage("android_safe_uploaded")
            await {QuakeStore(db).states(group).any {incident in it.incidentIds&&it.closed}}
            db.close();db=open()
            val reopened=QuakeStore(db).states(group).single {incident in it.incidentIds}
            assertTrue(reopened.closed);assertEquals(incident,reopened.formation.incidentId)
            val answers=RoomRecordStore(db).incidents(group).filter {it.kind==IncidentKind.ANSWER}
            assertTrue(answers.any {it.eventId==answer.eventId&&it.safety==Safety.SAFE})
            assertTrue(answers.any {it.eventId==otherAnswer.eventId&&it.safety==Safety.SOS})
            assertTrue(RoomRecordStore(db).all(group).size>=2)
            assertTrue(CloudSettings(context,name).checkpoint(session).cursor()>0)
            stage("browser_end_reopened")
        }finally {db.close();context.deleteDatabase("$name.db");context.getSharedPreferences(name,0).edit().clear().commit()}
    }
}
