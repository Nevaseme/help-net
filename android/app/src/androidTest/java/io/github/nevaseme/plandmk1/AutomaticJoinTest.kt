package io.github.nevaseme.plandmk1

import android.os.Build
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import io.github.nevaseme.plandmk1.cloud.*
import io.github.nevaseme.plandmk1.nearby.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

/** Real membership API and production join/start path, with only the radio mocked. */
class AutomaticJoinTest {
    @Test fun invitationJoinStartsSharingWithoutProfileOrConnectButton() {
        val i=InstrumentationRegistry.getInstrumentation()
        assertTrue(Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("sdk"))
        val app=i.targetContext.applicationContext as PlanDApplication
        fun shell(cmd:String){i.uiAutomation.executeShellCommand(cmd).use {android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes()}}
        (NearbyTransport.permissions()+arrayOf(android.Manifest.permission.POST_NOTIFICATIONS)).forEach {shell("pm grant ${app.packageName} $it")}
        shell("svc bluetooth enable");shell("svc wifi enable")
        val api=CloudHttpApi()
        val admin=runBlocking {api.register(BuildConfig.CLOUD_SYNC_URL,"groups",buildJsonObject {
            put("requestId",UUID.randomUUID().toString());put("deviceId",UUID.randomUUID().toString())
            put("activityName","Automatic test admin");put("groupName","Automatic-join-test")
            put("registrationToken",UUID.randomUUID().toString().replace("-","")+UUID.randomUUID().toString().replace("-",""))
        }).session(BuildConfig.CLOUD_SYNC_URL)}
        val invitation=runBlocking {api.invitation(admin)}
        val previous=app.installation.group.value;val factory=app.sharingPortFactory
        val port=Port();var scenario:ActivityScenario<MainActivity>?=null
        try {
            i.runOnMainSync {app.sharing.stop();app.installation.setGroup("join-test-${UUID.randomUUID().toString().take(8)}");app.sharingPortFactory={port}}
            scenario=ActivityScenario.launch(MainActivity::class.java)
            i.runOnMainSync {app.cloud.join(BuildConfig.CLOUD_SYNC_URL,"Automatic member",admin.groupId+":"+invitation.code)}
            val end=SystemClock.elapsedRealtime()+60_000
            while(SystemClock.elapsedRealtime()<end && port.starts==0)SystemClock.sleep(100)
            assertEquals(admin.groupId,app.installation.group.value)
            assertTrue(app.cloud.state.value.nearbyReady)
            assertTrue(port.configured);assertEquals(1,port.starts)
            assertTrue(app.sharing.state.value.active)
            assertNull(runBlocking {app.store.get(admin.groupId,app.installation.localId)})
        }finally {
            i.runOnMainSync {app.sharing.stop();app.installation.setGroup(previous);app.sharingPortFactory=factory}
            scenario?.close()
        }
    }
    private class Port:NearbyPort {
        override val state=MutableStateFlow(NearbyState())
        override var onConnected:suspend(String)->Unit={}
        override var onDisconnected:(String)->Unit={}
        override var onBytes:(String,ByteArray)->Unit={_,_->}
        @Volatile var starts=0
        @Volatile var configured=false
        override fun configureAutomatic(link:GroupLink){configured=true}
        override fun start(name:String){starts++;state.value=NearbyState(active=true,discovering=true)}
        override fun stop(){state.value=NearbyState()}
        override fun setVisible(visible:Boolean){}
        override fun connect(peer:Peer){}
        override fun verify(id:String,accept:Boolean){}
        override suspend fun send(peer:String,bytes:ByteArray){}
        override fun error(message:String){state.value=state.value.copy(error=message)}
        override fun receipt(){}
    }
}
