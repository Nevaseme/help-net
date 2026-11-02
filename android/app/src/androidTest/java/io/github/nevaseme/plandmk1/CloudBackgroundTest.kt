package io.github.nevaseme.plandmk1

import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkInfo
import androidx.work.WorkManager
import io.github.nevaseme.plandmk1.cloud.*
import io.github.nevaseme.plandmk1.core.*
import io.github.nevaseme.plandmk1.data.*
import io.github.nevaseme.plandmk1.eew.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.TimeUnit

/** Real queued worker without opening Activity. All writes belong to a new fictional group. */
class CloudBackgroundTest {
    @Test fun savedWarningQueuesAndUploadsWhileAnotherGroupRemainsSelected()=runBlocking<Unit> {
        val endpoint=InstrumentationRegistry.getArguments().getString("cloudEndpoint").orEmpty()
        assumeTrue("Explicit fictional server fixture endpoint required",endpoint.isNotBlank())
        val app=ApplicationProvider.getApplicationContext<PlanDApplication>();val previous=app.installation.group.value
        val name="cloud-work-${UUID.randomUUID()}";val directory=File(app.cacheDir,name);val device=UUID.randomUUID().toString()
        val fixtureSettings=CloudSettings(app,name);val api=CloudHttpApi();var group:String?=null;var work:UUID?=null
        try {
            val pending=fixtureSettings.begin(endpoint,device,"架空受信者","架空バックグラウンド試験")
            val registered=fixtureSettings.submit(pending,api);val session=registered.session(endpoint);group=registered.groupId
            CloudSettings(app).complete(session)
            RoomCloudJournal(app.database,session.groupId,device).updateRoster(registered.envelope(),device)
            app.store.merge(Resident(session.groupId,device,1,System.currentTimeMillis(),"架空受信者",35.0,135.0,"1階","架空試験",Safety.UNREPORTED))
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                assertFalse(androidx.lifecycle.ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED))
            }
            val receiver=EewReceiver(EewReceiptStore(directory),{EewTarget(session.groupId,device)},
                {runBlocking {QuakeStore(app.database).receiveEew(it)}},{_,_->},afterDurableSave={receipt->work=CloudSyncWork.enqueue(app,receipt.target!!.groupId,afterEew=true)})
            val at=System.currentTimeMillis();val time=SimpleDateFormat("yyyy/MM/dd HH:mm:ss",Locale.ROOT).apply {timeZone=TimeZone.getTimeZone("Asia/Tokyo")}.format(at)
            val data=mapOf("schema" to "pland-eew-1","source" to "wolfx","region" to "京都府南部","raw" to
                """{"type":"jma_eew","EventID":"20991030120000","Serial":1,"AnnouncedTime":"$time","Issue":{"Status":"通常"},"isTraining":false,"isWarn":true,"isCancel":false,"WarnArea":[{"Chiiki":"京都府南部","Type":"警報"}]}""")
            assertNotNull(receiver.receive(data,at));assertNotNull(work)
            val manager=WorkManager.getInstance(app)
            val finished=withTimeout(60_000) {
                var info:WorkInfo
                while(true){info=checkNotNull(manager.getWorkInfoById(work!!).get(5,TimeUnit.SECONDS));if(info.state.isFinished)break;delay(250)}
                info
            }
            assertEquals(WorkInfo.State.SUCCEEDED,finished.state)
            assertTrue(finished.outputData.getInt("uploaded",0)>=1)
            val page=api.pull(session,0)
            assertTrue(page.records.any {it.collection=="quakes"&&it.record["action"]?.jsonPrimitive?.content=="EEW"})
            assertEquals(previous,app.installation.group.value)
            assertTrue(EewReceiptStore(directory).receipts().none {EewReceiptStore(directory).pending(it)})
        }finally {
            work?.let {WorkManager.getInstance(app).cancelWorkById(it).result.get(5,TimeUnit.SECONDS)}
            group?.let {fixtureGroup->
                app.database.withTransaction {
                    for(table in listOf("residents","quake_events","incident_events","member_rosters"))
                        app.database.openHelper.writableDatabase.execSQL("DELETE FROM $table WHERE groupId = ?",arrayOf(fixtureGroup))
                }
                val prefs=app.getSharedPreferences("cloud-sharing",0);val editor=prefs.edit()
                prefs.all.keys.filter {it=="session:$fixtureGroup"||it.startsWith("$endpoint:$fixtureGroup:")}.forEach {editor.remove(it)};editor.commit()
                app.getSharedPreferences("installation",0).edit().remove("incident:$fixtureGroup").commit()
            }
            app.getSharedPreferences(name,0).edit().clear().commit();directory.deleteRecursively()
        }
    }
}
