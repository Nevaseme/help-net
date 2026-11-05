package io.github.nevaseme.plandmk1

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage

/** Launch explicitly through adb's instrumentation shell; avoids OEM-blocked ActivityScenario launches. */
fun shellComposeContent(content:@Composable ()->Unit){
 val inst=InstrumentationRegistry.getInstrumentation()
 inst.uiAutomation.executeShellCommand("am start -n ${inst.targetContext.packageName}/androidx.activity.ComponentActivity").use{android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes()}
 var activity:ComponentActivity?=null
 val deadline=android.os.SystemClock.elapsedRealtime()+15000
 while(activity==null&&android.os.SystemClock.elapsedRealtime()<deadline){
  inst.runOnMainSync{activity=ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).filterIsInstance<ComponentActivity>().lastOrNull{it.javaClass==ComponentActivity::class.java}}
  if(activity==null)android.os.SystemClock.sleep(100)
 }
 checkNotNull(activity){"Test activity did not become visible"}.let{host->inst.runOnMainSync{host.setContent(content=content)}}
}
