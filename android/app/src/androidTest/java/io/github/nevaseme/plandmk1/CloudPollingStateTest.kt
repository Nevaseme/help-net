package io.github.nevaseme.plandmk1

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import io.github.nevaseme.plandmk1.cloud.CloudState

/** Existing fictional emulator session only; sync remains enabled and uses the real transport. */
class CloudPollingStateTest {
 @Test fun automaticSyncDoesNotLockGroupForms() {
  check(android.os.Build.FINGERPRINT.contains("generic") || android.os.Build.MODEL.contains("sdk"))
  val inst=InstrumentationRegistry.getInstrumentation()
  val app=inst.targetContext.applicationContext as PlanDApplication
  val ready=android.os.SystemClock.elapsedRealtime()+15000
  while(!app.cloud.state.value.connected&&android.os.SystemClock.elapsedRealtime()<ready)android.os.SystemClock.sleep(100)
  assertTrue(app.cloud.state.value.connected)
  val states=CopyOnWriteArrayList<CloudState>()
  val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
  lateinit var job:Job
  inst.runOnMainSync {
   app.cloud.setVisible(false)
   scope.launch { app.cloud.state.collect { states.add(it) } }
   app.cloud.sync()
   job=app.cloud.javaClass.getDeclaredField("syncing").apply {isAccessible=true}.get(app.cloud) as Job
  }
  try {
   runBlocking { withTimeout(60000) {job.join()} }
   assertTrue("A real sync must finish successfully: ${app.cloud.state.value.status} / ${app.cloud.state.value.syncError}",app.cloud.state.value.status in listOf("同期済み","未送信の情報があります"))
   assertFalse("Automatic sync must not set the group form's busy state",states.any {it.busy})
   states.clear()
   inst.runOnMainSync {
    app.cloud.sync()
    job=app.cloud.javaClass.getDeclaredField("syncing").apply {isAccessible=true}.get(app.cloud) as Job
   }
   runBlocking { withTimeout(60000) {job.join()} }
   assertTrue("An unchanged sync must emit no global UI state",states.isEmpty())
   // Reproduce a tap while transport is waiting: accept it once, then run it
   // after sync without dropping the operation or permitting duplicate taps.
   val mutex=app.cloud.javaClass.getDeclaredField("lock").apply {isAccessible=true}.get(app.cloud) as kotlinx.coroutines.sync.Mutex
   runBlocking {mutex.lock()}
   try {
    inst.runOnMainSync {
     app.cloud.sync()
     app.cloud.invite()
     assertTrue("Queued user action must reserve the form immediately",app.cloud.state.value.busy)
     app.cloud.invite()
    }
   }finally {mutex.unlock()}
   val deadline=android.os.SystemClock.elapsedRealtime()+60000
   while(app.cloud.state.value.busy&&android.os.SystemClock.elapsedRealtime()<deadline)android.os.SystemClock.sleep(100)
   assertFalse(app.cloud.state.value.busy)
   assertNull(app.cloud.state.value.error)
   assertNotNull("Tap made during sync must complete",app.cloud.state.value.invitation)
  }finally {scope.cancel()}
 }
}
