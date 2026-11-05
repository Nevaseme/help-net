package io.github.nevaseme.plandmk1

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import io.github.nevaseme.plandmk1.cloud.*
import io.github.nevaseme.plandmk1.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.UUID

/** Separate fictional credentials and Room databases; no changes to the user's active group. */
class DemoAccessDeviceTest {
    private fun id()=UUID.randomUUID().toString()
    @Test fun actualModeAndRecoveryUseTheSameMemberAcrossFreshInstallations()=runBlocking<Unit> {
        val endpoint=InstrumentationRegistry.getArguments().getString("cloudEndpoint").orEmpty();assumeTrue(endpoint.isNotBlank())
        val context=ApplicationProvider.getApplicationContext<Context>();val api=CloudHttpApi()
        val names=List(3){"demo-access-${id()}"};val settings=names.map {CloudSettings(context,it)}
        val db=Room.databaseBuilder(context,PlanDatabase::class.java,names[2]+".db").build()
        try {
            val groupName="架空復元Android-${id().take(8)}";val pass="Demo-Android-9726"
            val a=settings[0].begin(endpoint,id(),"管理者",groupName);val admin=api.register(endpoint,a.path,a.body)
            val started=api.startVenue(admin.session(endpoint),id(),pass,positionMode="ACTUAL")
            assertEquals("ACTUAL",started.venue!!.positionMode);assertTrue(started.venue!!.placements.all {it.latitude==null&&it.longitude==null});started.venue!!.validate(started.roster)
            val b=settings[1].beginVenue(endpoint,id(),"架空参加者",groupName)
            val submitted=b.copy(body=kotlinx.serialization.json.JsonObject(b.body+("password" to kotlinx.serialization.json.JsonPrimitive(pass))))
            val original=api.register(endpoint,submitted.path,submitted.body)
            val code=api.issueRecovery(original.session(endpoint),original.memberId)
            val c=settings[2].beginRecovery(endpoint,id(),groupName,code.code)
            val recovered=api.register(endpoint,c.path,c.body)
            assertEquals(original.memberId,recovered.memberId);assertEquals(2,recovered.roster.members.size);assertEquals(2,recovered.venue!!.placements.size)
            assertEquals(recovered,api.register(endpoint,c.path,c.body))
            var refused=false;try{api.roster(original.session(endpoint))}catch(e:CloudHttpException){refused=e.status==401};assertTrue(refused)
            val journal=RoomCloudJournal(db,admin.groupId,recovered.deviceId)
            journal.updateRoster(recovered.envelope(),recovered.deviceId)
            settings[2].complete(recovered.session(endpoint));settings[2].saveVenue(recovered.envelope())
            assertEquals("ACTUAL",CloudSettings(context,names[2]).venue(admin.groupId)!!.positionMode)
            assertEquals(0,RoomRecordStore(db).all(admin.groupId).size)
            val stopped=api.revokeMember(admin.session(endpoint),recovered.memberId)
            assertEquals(1,stopped.venue!!.placements.size);assertTrue(recovered.memberId in stopped.venue!!.inactiveMemberIds)
        }finally{db.close();context.deleteDatabase(names[2]+".db");names.forEach {context.getSharedPreferences(it,0).edit().clear().commit()}}
    }
}
