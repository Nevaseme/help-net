package io.github.nevaseme.plandmk1

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import io.github.nevaseme.plandmk1.cloud.*
import io.github.nevaseme.plandmk1.core.*
import io.github.nevaseme.plandmk1.data.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.UUID

/** Opt-in deployed API test: two fictional installations, independent Room and private prefs. */
class CloudHttpsTest {
    private fun id()=UUID.randomUUID().toString()
    @Test fun twoInstallationsShareProfileEewAnswerAndAdministratorEndOverHttps()=runBlocking<Unit> {
        val endpoint=InstrumentationRegistry.getArguments().getString("cloudEndpoint").orEmpty()
        assumeTrue("Explicit deployed test endpoint required",endpoint.isNotBlank())
        val context=ApplicationProvider.getApplicationContext<Context>();val api=CloudHttpApi()
        val names=List(2){"cloud-https-${id()}"};val ids=List(2){id()}
        val dbs=names.map {Room.databaseBuilder(context,PlanDatabase::class.java,"$it.db").build()}
        val settings=names.map {CloudSettings(context,it)}
        try {
            val pending=settings[0].begin(endpoint,ids[0],"架空管理者","自動試験・架空グループ")
            val admin=api.register(endpoint,pending.path,pending.body)
            assertEquals(ids[0],admin.deviceId);assertEquals(pending.body.getValue("requestId").jsonPrimitive.content,admin.groupId)
            assertEquals(admin,api.register(endpoint,pending.path,pending.body))
            val invite=api.invitation(admin.session(endpoint))
            val join=settings[1].begin(endpoint,ids[1],"架空参加者",groupId=admin.groupId,code=invite.code)
            val member=api.register(endpoint,join.path,join.body);assertEquals(MemberRole.MEMBER,member.role)
            val sessions=listOf(admin.session(endpoint),member.session(endpoint));val group=admin.groupId
            val journals=dbs.indices.map {RoomCloudJournal(dbs[it],group,ids[it])}
            for(i in dbs.indices) {
                settings[i].complete(sessions[i]);journals[i].updateRoster(api.roster(sessions[i]),ids[i])
                RoomRecordStore(dbs[i]).merge(Resident(group,ids[i],1,System.currentTimeMillis(),if(i==0)"架空管理者" else "架空参加者",35.0,135.0,"1階","架空試験",Safety.UNREPORTED))
            }
            suspend fun sync(index:Int)=CloudSyncEngine(sessions[index],api,journals[index],settings[index].checkpoint(sessions[index])).sync().also {assertEquals("No own fixture record may be rejected",0,it.rejected)}
            sync(0);sync(1);sync(0)
            assertEquals(2,RoomRecordStore(dbs[0]).all(group).size);assertEquals(2,RoomRecordStore(dbs[1]).all(group).size)
            val at=System.currentTimeMillis();val evidence=EewEvidence("wolfx","20991030120000",at,at+1,true)
            QuakeStore(dbs[0]).merge(QuakeEvent(group,id(),ids[0],at+1,QuakeAction.EEW,false,targetId=evidence.incidentId(),eew=evidence))
            sync(0);sync(1)
            val active=QuakeStore(dbs[1]).states(group).single();assertFalse(active.closed)
            val answer=IncidentEvent(group,id(),active.formation.incidentId,ids[1],"架空参加者",1,at+2,IncidentKind.ANSWER,safety=Safety.SAFE)
            RoomRecordStore(dbs[1]).appendIncident(answer);sync(1);sync(0)
            assertEquals(Safety.SAFE,RoomRecordStore(dbs[0]).incidents(group).single {it.kind==IncidentKind.ANSWER}.safety)
            QuakeStore(dbs[0]).command(QuakeEvent(group,id(),ids[0],at+3,QuakeAction.END,false,targetId=active.formation.incidentId))
            sync(0);sync(1)
            assertTrue(QuakeStore(dbs[1]).states(group).single().closed)
            assertEquals(1,RoomRecordStore(dbs[1]).incidents(group).count {it.kind==IncidentKind.ANSWER})
            assertEquals(ids[0],sessions[0].deviceId);assertEquals(ids[1],sessions[1].deviceId)
        }finally {dbs.forEach {it.close()};names.forEach {context.deleteDatabase("$it.db");context.getSharedPreferences(it,0).edit().clear().commit()}}
    }
}
