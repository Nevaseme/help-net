package io.github.nevaseme.plandmk1

import android.graphics.Rect
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Read-only UI navigation and ordinary sync. Never creates/ends a drill or saves a form. */
class PeriodicSyncScreenTest {
 private val inst get()=InstrumentationRegistry.getInstrumentation()
 private val app get()=inst.targetContext.applicationContext as PlanDApplication
 private fun nodes(n:AccessibilityNodeInfo?):List<AccessibilityNodeInfo> = if(n==null)emptyList() else listOf(n)+(0 until n.childCount).flatMap {nodes(n.getChild(it))}
 private fun all():List<AccessibilityNodeInfo>{if(android.os.Build.VERSION.SDK_INT>=33)inst.uiAutomation.clearCache();return nodes(inst.uiAutomation.rootInActiveWindow)}
 private fun find(label:String)=all().lastOrNull {it.text?.toString()==label||it.contentDescription?.toString()==label}
 private fun waitFor(label:String):AccessibilityNodeInfo {val end=SystemClock.elapsedRealtime()+20000;while(SystemClock.elapsedRealtime()<end){find(label)?.let {return it};SystemClock.sleep(150)};throw AssertionError("Not visible: $label; "+all().mapNotNull {it.text?.toString()})}
 private fun tap(label:String){val r=Rect();waitFor(label).getBoundsInScreen(r);shell("input tap ${r.centerX()} ${r.centerY()}");SystemClock.sleep(600)}
 private fun shell(command:String){inst.uiAutomation.executeShellCommand(command).use {android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes()}}
 private fun capture(name:String){val b=inst.uiAutomation.takeScreenshot();File(inst.targetContext.getExternalFilesDir(null),"polling-$name.png").outputStream().use {b.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};b.recycle()}
 private fun geometry():List<String> = all().filter {it.packageName==inst.targetContext.packageName&&(it.isClickable||it.isEditable||!it.text.isNullOrEmpty())}.map {val r=Rect();it.getBoundsInScreen(r);"${it.text}|${it.contentDescription}|${it.isEnabled}|${it.isFocused}|$r"}
 private fun syncOnce():Job {lateinit var job:Job;inst.runOnMainSync {app.cloud.sync();job=app.cloud.javaClass.getDeclaredField("syncing").apply {isAccessible=true}.get(app.cloud) as Job};return job}
 private fun stable(label:String,natural:Boolean=false) {
  runBlocking {withTimeout(60000){syncOnce().join()}}
  SystemClock.sleep(500)
  val expected=geometry();assertTrue(expected.isNotEmpty());capture("$label-before")
  val baseline=app.cloud.state.value
  val cycles=java.util.concurrent.atomic.AtomicInteger()
  val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
  inst.runOnMainSync {scope.launch {app.cloud.synchronization.collect {if(it)cycles.incrementAndGet()}}}
  val until=SystemClock.elapsedRealtime()+if(natural)33000 else 1800
  val job=if(natural)null else syncOnce()
  var samples=0
  while(SystemClock.elapsedRealtime()<until||job?.isActive==true){
   assertEquals("$label: sync must not change visible controls, focus, or positions",expected,geometry())
   assertFalse("$label: sync must not lock forms",app.cloud.state.value.busy)
   assertEquals("$label: unchanged sync must not alter global UI state",baseline,app.cloud.state.value)
   samples++;SystemClock.sleep(80)
  }
  scope.cancel()
  assertTrue("$label: synchronization must actually run",cycles.get()>=if(natural)2 else 1)
  capture("$label-after");println("$label stable: $samples samples, sync cycles=${cycles.get()}, natural=$natural")
 }
 private fun menu(label:String){if(find("メニューを開く")==null)tap("戻る");tap("メニューを開く");tap(label)}
 @Test fun groupAndOtherScreensKeepControlsStable() {
  shell("am start -n io.github.nevaseme.plandmk1/.MainActivity")
  waitFor("メニューを開く")
  menu("グループ");waitFor("参加中のグループ");tap("別のグループに接続")
  stable("group",natural=true)
  tap("入力を閉じる")
  menu("自分の登録情報");waitFor("住んでいる建物・住戸を選ぶ");stable("profile")
  menu("訓練・地震");waitFor("訓練を開始");stable("training")
  menu("アプリ設定");waitFor("ライト");stable("settings")
  tap("パネルを閉じる")
  if(find("メニューを開く")==null)tap("戻る")
  for(label in listOf("安否","一覧","地図","対応")) {tap(label);stable("tab-$label")}
  menu("グループ");waitFor("参加中のグループ")
 }
}
