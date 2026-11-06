package io.github.nevaseme.plandmk1
import android.os.Bundle
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.Assert.*
import java.io.File

/** Read-only navigation/search regression; preserves residents, groups and records. */
class MenuSearchDeviceTest {
 private val inst get()=InstrumentationRegistry.getInstrumentation()
 private fun nodes(n:AccessibilityNodeInfo?):List<AccessibilityNodeInfo> = if(n==null)emptyList() else listOf(n)+(0 until n.childCount).flatMap {nodes(n.getChild(it))}
 private fun all():List<AccessibilityNodeInfo>{if(android.os.Build.VERSION.SDK_INT>=33)inst.uiAutomation.clearCache();return nodes(inst.uiAutomation.rootInActiveWindow)}
 private fun find(label:String)=all().lastOrNull {it.text?.toString()==label||it.contentDescription?.toString()==label}
 private fun waitFor(label:String):AccessibilityNodeInfo {val end=android.os.SystemClock.elapsedRealtime()+20000;while(android.os.SystemClock.elapsedRealtime()<end){find(label)?.let {return it};android.os.SystemClock.sleep(200)};throw AssertionError("Not visible: $label; "+all().mapNotNull {it.text?.toString()})}
 private fun tap(label:String){val n=waitFor(label);val r=Rect();n.getBoundsInScreen(r);val time=android.os.SystemClock.uptimeMillis();for(action in listOf(android.view.MotionEvent.ACTION_DOWN,android.view.MotionEvent.ACTION_UP)){val e=android.view.MotionEvent.obtain(time,android.os.SystemClock.uptimeMillis(),action,r.exactCenterX(),r.exactCenterY(),0);try{assertTrue(inst.uiAutomation.injectInputEvent(e,true))}finally{e.recycle()}};android.os.SystemClock.sleep(800)}
 private fun absent(label:String){val end=android.os.SystemClock.elapsedRealtime()+5000;while(find(label)!=null&&android.os.SystemClock.elapsedRealtime()<end)android.os.SystemClock.sleep(100);assertNull(find(label))}

 private fun capture(name:String){inst.uiAutomation.takeScreenshot().let {b->File(inst.targetContext.getExternalFilesDir(null),"menu-search-$name.png").outputStream().use {b.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};b.recycle()}}
 @Test fun actualMenuAndSearch() {
  // Shell launch avoids vivo startActivitySync waiting for idle.
  inst.uiAutomation.executeShellCommand("am start -n io.github.nevaseme.plandmk1/.MainActivity").use { android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes() }
  if(InstrumentationRegistry.getArguments().getString("fictionalGroup")=="true"){
   check(android.os.Build.FINGERPRINT.contains("generic")||android.os.Build.MODEL.contains("sdk"))
   val app=inst.targetContext.applicationContext as PlanDApplication
   inst.runOnMainSync{app.cloud.create("https://tasukeai-sync.tomodachi-takumi.workers.dev/","架空管理者","検索確認・架空Android","SearchCheck123")}
   var end=android.os.SystemClock.elapsedRealtime()+60000
   while((app.cloud.state.value.groupName!="検索確認・架空Android"||app.cloud.state.value.busy)&&android.os.SystemClock.elapsedRealtime()<end)android.os.SystemClock.sleep(200)
   assertEquals("検索確認・架空Android",app.cloud.state.value.groupName)
   inst.runOnMainSync{app.cloud.setMunicipality("26100")};end=android.os.SystemClock.elapsedRealtime()+30000
   while(app.cloud.state.value.municipality?.code!="26100"&&android.os.SystemClock.elapsedRealtime()<end)android.os.SystemClock.sleep(200)
   assertEquals("26100",app.cloud.state.value.municipality?.code)
   kotlinx.coroutines.runBlocking {app.store.updateOwn(app.installation.group.value,app.installation.localId){io.github.nevaseme.plandmk1.core.Resident(app.installation.group.value,app.installation.localId,1,1,"架空管理者",35.0145,135.7625,"","")}}
   inst.runOnMainSync{app.recordsChanged()}

  }
  waitFor("メニューを開く");tap("メニューを開く");tap("アプリ設定");waitFor("ライト");capture("settings")
  tap("メニューを開く");waitFor("自分の登録情報");absent("ライト");tap("メニューを開く");absent("自分の登録情報")
  tap("メニューを開く");tap("アプリ設定");tap("パネルを閉じる");absent("ライト")
  tap("メニューを開く");tap("自分の登録情報");tap("住んでいる建物・住戸を選ぶ")
  waitFor("住所・建物名で探す")
  fun query(text:String){val field=all().first {it.isEditable};assertTrue(field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,Bundle().apply {putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,text)}))}
  query("左京区");waitFor("住所・施設の候補");assertTrue(all().any {it.text?.toString()?.contains("京都府京都市左京区")==true});capture("ward")
  query("京都タワー");waitFor("施設 · 地図で建物を確認");waitFor("京都タワー");capture("tower");tap("京都タワー");waitFor("この位置にする")
  val mapRef=java.util.concurrent.atomic.AtomicReference<org.maplibre.android.maps.MapLibreMap>()
  val bounds=Rect()
  inst.runOnMainSync {
   fun maps(v:android.view.View):List<org.maplibre.android.maps.MapView> = if(v is org.maplibre.android.maps.MapView)listOf(v) else if(v is android.view.ViewGroup)(0 until v.childCount).flatMap{maps(v.getChildAt(it))}else emptyList()
   val view=android.view.inspector.WindowInspector.getGlobalWindowViews().flatMap{maps(it)}.last{it.isShown}
   view.getGlobalVisibleRect(bounds);view.getMapAsync{mapRef.set(it)}
  }
  val until=android.os.SystemClock.elapsedRealtime()+10000
  while(mapRef.get()==null&&android.os.SystemClock.elapsedRealtime()<until)android.os.SystemClock.sleep(100)
  val readyDeadline=android.os.SystemClock.elapsedRealtime()+45000;var featureCount=0
  while(featureCount==0&&android.os.SystemClock.elapsedRealtime()<readyDeadline){inst.runOnMainSync{featureCount=if(mapRef.get().style?.isFullyLoaded==true)mapRef.get().queryRenderedFeatures(android.graphics.RectF(0f,0f,bounds.width().toFloat(),bounds.height().toFloat())).size else 0};android.os.SystemClock.sleep(200)}
  assertTrue("Background map must be visible",featureCount>0);capture("map")
  var before:org.maplibre.android.geometry.LatLng?=null
  inst.runOnMainSync {before=mapRef.get().cameraPosition.target}
  val start=android.os.SystemClock.uptimeMillis()
  for(i in 0..16){val event=android.view.MotionEvent.obtain(start,android.os.SystemClock.uptimeMillis(),if(i==0)0 else if(i==16)1 else 2,bounds.exactCenterX()+bounds.width()*.2f*i/16,bounds.exactCenterY()+bounds.height()*.1f*i/16,0);inst.uiAutomation.injectInputEvent(event,true);event.recycle();android.os.SystemClock.sleep(20)}
  android.os.SystemClock.sleep(1000)
  inst.runOnMainSync {assertTrue(kotlin.math.abs(mapRef.get().cameraPosition.target!!.longitude-before!!.longitude)>1e-6)}
  capture("pin-adjusted");tap("戻る");tap("閉じる")
  repeat(3){if(find("メニューを開く")==null)inst.uiAutomation.executeShellCommand("input keyevent 4").close();android.os.SystemClock.sleep(300)}
  tap("メニューを開く");tap("訓練・地震");if(find("訓練を実施中です。安否画面から回答できます。")!=null){capture("training-active");return};tap("訓練を開始");waitFor("京都に架空の位置を割り当てる");val checkbox=all().first{it.isCheckable};val checkBounds=Rect();checkbox.getBoundsInScreen(checkBounds)
  inst.uiAutomation.executeShellCommand("input tap ${checkBounds.centerX()} ${checkBounds.centerY()}").use{android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes()};android.os.SystemClock.sleep(2500);assertTrue(all().first{it.isCheckable}.isChecked);capture("training")
  if(InstrumentationRegistry.getArguments().getString("fictionalGroup")=="true"){
   tap("訓練を開始");val app=inst.targetContext.applicationContext as PlanDApplication;val deadline=android.os.SystemClock.elapsedRealtime()+60000
   while(app.cloud.state.value.venue==null&&android.os.SystemClock.elapsedRealtime()<deadline)android.os.SystemClock.sleep(200)
   assertNotNull(app.cloud.state.value.venue);assertTrue(app.cloud.state.value.venue!!.placements.any{it.deviceId==app.installation.localId})
   kotlinx.coroutines.runBlocking {assertEquals(35.0145,app.store.get(app.installation.group.value,app.installation.localId)!!.latitude,1e-9)}
  }else tap("閉じる")
 }
}


