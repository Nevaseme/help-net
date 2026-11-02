package io.github.nevaseme.plandmk1

import android.os.Bundle
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Real screen search and cancellation; never commits the user's home. */
class AddressPickerDeviceTest {
 private val inst get()=InstrumentationRegistry.getInstrumentation()
 private fun nodes(n:AccessibilityNodeInfo?):List<AccessibilityNodeInfo> = if(n==null)emptyList() else listOf(n)+(0 until n.childCount).flatMap{nodes(n.getChild(it))}
 private fun all():List<AccessibilityNodeInfo>{if(android.os.Build.VERSION.SDK_INT>=33)inst.uiAutomation.clearCache();return nodes(inst.uiAutomation.rootInActiveWindow)}
 private fun waitFor(predicate:(AccessibilityNodeInfo)->Boolean):AccessibilityNodeInfo {val end=SystemClock.elapsedRealtime()+60000;while(SystemClock.elapsedRealtime()<end){all().firstOrNull(predicate)?.let{return it};SystemClock.sleep(200)};error("Screen target missing: "+all().mapNotNull{it.text})}
 private fun tap(label:String){var node:AccessibilityNodeInfo?=waitFor{it.text?.toString()==label||it.contentDescription?.toString()==label};while(node!=null&&!node.isClickable)node=node.parent;assertTrue(node?.performAction(AccessibilityNodeInfo.ACTION_CLICK)==true);SystemClock.sleep(400)}
 @Test fun query203DisplaysHonestAddressAndCancelPreservesHome()=runBlocking<Unit>{
  val app=inst.targetContext.applicationContext as PlanDApplication;val group=app.installation.group.value;val id=app.installation.localId
  val before=app.store.get(group,id);val homes=app.store.places(group)
  inst.uiAutomation.executeShellCommand("am start -n io.github.nevaseme.plandmk1/.MainActivity").use{android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes()}
  waitFor{it.text?.toString()=="住居の位置を選ぶ"||it.contentDescription?.toString()=="メニューを開く"}
  if(all().any{it.text?.toString()=="住居の位置を選ぶ"})tap("戻る")
  tap("メニューを開く");tap("自分の登録情報")
  waitFor{it.text?.toString()=="住所を検索"}
  val query=all().filter{it.isEditable}[1];assertTrue(query.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,Bundle().apply{putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,"左京区讃州寺町203")}));tap("住所を検索")
  waitFor{it.text?.toString()?.contains("讃州寺町203 付近")==true}
  waitFor{it.text?.toString()=="この位置にする"&&it.isEnabled}
  val map=java.util.concurrent.atomic.AtomicReference<org.maplibre.android.maps.MapLibreMap>()
  inst.runOnMainSync {
   fun maps(v:android.view.View):List<org.maplibre.android.maps.MapView> = if(v is org.maplibre.android.maps.MapView)listOf(v) else if(v is android.view.ViewGroup)(0 until v.childCount).flatMap{maps(v.getChildAt(it))} else emptyList()
   android.view.inspector.WindowInspector.getGlobalWindowViews().flatMap{maps(it)}.last{it.isShown}.getMapAsync{map.set(it)}
  }
  var features=0;val deadline=SystemClock.elapsedRealtime()+60000
  while(features==0&&SystemClock.elapsedRealtime()<deadline){inst.runOnMainSync{map.get()?.let{if(it.style?.isFullyLoaded==true)features=it.queryRenderedFeatures(android.graphics.RectF(0f,0f,10000f,10000f)).size}};SystemClock.sleep(250)}
  assertTrue("Street/building map must render before confirmation",features>0)
  inst.runOnMainSync {assertEquals(35.011046,map.get().cameraPosition.target!!.latitude,.0001);assertEquals(135.774524,map.get().cameraPosition.target!!.longitude,.0001)}
  SystemClock.sleep(750)
  inst.uiAutomation.takeScreenshot().let{b->File(inst.targetContext.getExternalFilesDir(null),"address-203-device.png").outputStream().use{b.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
   var blue=0;for(y in b.height*35/100 until b.height*60/100)for(x in b.width*35/100 until b.width*65/100){val c=b.getPixel(x,y);if(android.graphics.Color.red(c)<40&&android.graphics.Color.green(c) in 75..115&&android.graphics.Color.blue(c)>225)blue++}
   b.recycle();assertTrue("Center selection pin must remain visible above rendered map: $blue blue pixels",blue>200)
  }
  tap("戻る")
  assertEquals(before,app.store.get(group,id));assertEquals(homes,app.store.places(group))
 }
}
