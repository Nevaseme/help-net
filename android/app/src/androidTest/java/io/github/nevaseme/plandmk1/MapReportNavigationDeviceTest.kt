package io.github.nevaseme.plandmk1

import android.os.Build
import android.os.SystemClock
import android.view.*
import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import io.github.nevaseme.plandmk1.core.*
import io.github.nevaseme.plandmk1.ui.*
import io.github.nevaseme.plandmk1.ui.theme.PlanDMk1Theme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.*
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMap
import java.io.File
import java.util.UUID

class MapReportNavigationDeviceTest {
 @get:Rule val compose=createEmptyComposeRule()
 private lateinit var mapView:MapView
 private fun currentMap():MapLibreMap {
  fun find(v:View):MapView?=if(v is MapView)v else if(v is ViewGroup)(0 until v.childCount).firstNotNullOfOrNull {find(v.getChildAt(it))} else null
  var result:MapLibreMap?=null
  compose.waitUntil(20000){compose.runOnIdle {val activity=ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).first();find(activity.window.decorView)?.let {mapView=it;it.getMapAsync {result=it}}};result!=null}
  return result!!
 }
 private fun shot(name:String){val inst=InstrumentationRegistry.getInstrumentation();val f=File(inst.targetContext.filesDir,"map-review/$name.png");f.parentFile!!.mkdirs();inst.uiAutomation.takeScreenshot().let {b->f.outputStream().use {b.compress(Bitmap.CompressFormat.PNG,100,it)};b.recycle()}}
 private fun diagnostic(name:String,command:String){val inst=InstrumentationRegistry.getInstrumentation();val f=File(inst.targetContext.filesDir,"map-review/$name.txt");f.parentFile!!.mkdirs();inst.uiAutomation.executeShellCommand(command).use {fd->android.os.ParcelFileDescriptor.AutoCloseInputStream(fd).use {input->f.outputStream().use {input.copyTo(it)}}}}
 @Test fun reportOpensAndClosesOnMapWithOutlinesBelowMarkers()=runBlocking {
  assertTrue(Build.FINGERPRINT.contains("generic")||Build.MODEL.contains("sdk"))
  val app=ApplicationProvider.getApplicationContext<PlanDApplication>();val previous=app.installation.group.value;val me=app.installation.localId
  val group="map-review-${UUID.randomUUID().toString().take(8)}";val incident=UUID.randomUUID().toString();val report=UUID.randomUUID().toString();val now=System.currentTimeMillis()
  app.store.updateOwn(group,me){Resident(group,me,1,0,"Review administrator",35.01193557,135.75823951,"2","")}
  app.quakes.importRoster(MemberRoster(groupId=group,members=listOf(GroupMember(me,"Review administrator",MemberRole.ADMIN,listOf(me)))).encode(),me)
  app.quakes.command(QuakeEvent(group,incident,me,now-240000,QuakeAction.TRAINING_START,true,title="Map review",area="Kyoto"))
  app.store.answerWithMemo(IncidentEvent(group,UUID.randomUUID().toString(),incident,me,"Review administrator",1,now,IncidentKind.ANSWER,safety=Safety.SAFE),"")
  app.responses.append(ResponseEvent(group,report,me,1,now,incident,ResponseChange.Report(report,"道路閉塞",35.01078,135.75824,"Road blocked by fallen material")))
  val prefs=app.getSharedPreferences("interface-language",0);val lang=prefs.getString("language",null);prefs.edit().putString("language","en").commit()
  val models=ViewModelStore();lateinit var model:PlanViewModel
  try {
   compose.runOnUiThread {org.maplibre.android.MapLibre.getInstance(app);app.installation.setGroup(group);app.installation.selectIncident(group,incident);model=PlanViewModel(app);models.put("map",model)}
   compose.waitUntil(20000){model.responseEvents.value.any {it.eventId==report}&&model.incidentUi.value.current?.answers?.containsKey(me)==true}
   shellComposeContent {PlanDMk1Theme {PlanApp(model)}}
   if(compose.onAllNodesWithContentDescription("Back").fetchSemanticsNodes().isNotEmpty())compose.onNodeWithContentDescription("Back").performClick()
   compose.onNodeWithText("Map",useUnmergedTree=true).performClick()
   val map=currentMap()
   compose.waitUntil(20000){var ready=false;compose.runOnIdle {ready=map.style?.getLayer("kyoto-risk-registered")!=null};ready}
   compose.runOnIdle {
    val layers=map.style!!.layers
    val outline=layers.indexOfFirst {it.id=="kyoto-risk-registered"}
    val symbols=layers.indexOfFirst {it is org.maplibre.android.style.layers.SymbolLayer}
    assertTrue("Outline below all symbols",outline in 0 until symbols)
   }
   compose.runOnIdle {map.moveCamera(org.maplibre.android.camera.CameraUpdateFactory.newLatLngZoom(org.maplibre.android.geometry.LatLng(35.01078,135.75824),16.8))}
   compose.onNodeWithContentDescription("Incident report",useUnmergedTree=true).assertIsDisplayed()
   SystemClock.sleep(2000);shot("report-map-before")
   val bounds=compose.onNodeWithContentDescription("Incident report",useUnmergedTree=true).fetchSemanticsNode().boundsInWindow
   compose.runOnIdle {
    val origin=map.projection.toScreenLocation(org.maplibre.android.geometry.LatLng(35.01078,135.75824))
    val offset=IntArray(2);mapView.getLocationInWindow(offset)
    assertEquals("Report longitude is unchanged",origin.x+offset[0],bounds.center.x,2f)
    assertEquals("Report latitude is unchanged",origin.y+offset[1],bounds.center.y,2f)
   }
   val inst=InstrumentationRegistry.getInstrumentation();val p=android.graphics.PointF(bounds.center.x,bounds.center.y);val time=SystemClock.uptimeMillis()
   val rendered=inst.uiAutomation.takeScreenshot()
   val radius=(24*app.resources.displayMetrics.density).toInt()
   var flagPixels=0
   for(y in (p.y.toInt()-radius).coerceAtLeast(0)..(p.y.toInt()+radius).coerceAtMost(rendered.height-1))
    for(x in (p.x.toInt()-radius).coerceAtLeast(0)..(p.x.toInt()+radius).coerceAtMost(rendered.width-1))
     if(rendered.getPixel(x,y) and 0xFFFFFF == 0x68778D)flagPixels++
   rendered.recycle();assertTrue("Visible flag pixels: $flagPixels",flagPixels>100)

   listOf(MotionEvent.ACTION_DOWN,MotionEvent.ACTION_UP).forEach {action->MotionEvent.obtain(time,SystemClock.uptimeMillis(),action,p.x,p.y,0).let {inst.uiAutomation.injectInputEvent(it,true);it.recycle()}}
   SystemClock.sleep(700)
   shot("report-map-after-tap")
   compose.onNodeWithText("Road blocked by fallen material").assertIsDisplayed()
   compose.onNodeWithText("Map").assertIsSelected()
   shot("report-map-open")
   compose.onNodeWithContentDescription("Close",useUnmergedTree=true).performClick()
   compose.onNodeWithText("Map").assertIsSelected()
   compose.onNodeWithText("Road blocked by fallen material").assertDoesNotExist()
   SystemClock.sleep(2000);shot("report-map-closed")
   // Repeated camera gestures used to run the complete footprint search on every frame.
   diagnostic("memory-before","dumpsys meminfo io.github.nevaseme.plandmk1")
   diagnostic("frames-reset","dumpsys gfxinfo io.github.nevaseme.plandmk1 reset")
   val panStarted=SystemClock.elapsedRealtime()
   repeat(16){i->
    compose.runOnIdle {map.animateCamera(org.maplibre.android.camera.CameraUpdateFactory.newLatLngZoom(org.maplibre.android.geometry.LatLng(35.01078+(if(i%4<2).0008 else .012),135.75824),if(i%2==0)15.8 else 17.5),350)}
    SystemClock.sleep(450)
    compose.onNodeWithText("Map").assertIsSelected()
   }
   compose.runOnIdle {map.moveCamera(org.maplibre.android.camera.CameraUpdateFactory.newLatLngZoom(org.maplibre.android.geometry.LatLng(35.01078,135.75824),16.8))}
   SystemClock.sleep(1000)
   compose.onNodeWithContentDescription("Incident report",useUnmergedTree=true).assertIsDisplayed()
   shot("report-map-pan-complete")
   diagnostic("frames-after","dumpsys gfxinfo io.github.nevaseme.plandmk1 framestats")
   diagnostic("memory-after","dumpsys meminfo io.github.nevaseme.plandmk1")
   File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir,"map-review/pan-elapsed.txt").writeText((SystemClock.elapsedRealtime()-panStarted).toString())
   repeat(3){
    compose.onNodeWithText("People",useUnmergedTree=true).performClick()
    compose.onNodeWithText("Map",useUnmergedTree=true).performClick()
    compose.onNodeWithContentDescription("Incident report",useUnmergedTree=true).assertIsDisplayed()
   }

  }finally {compose.runOnUiThread {models.clear();app.installation.setGroup(previous);ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).toList().forEach {it.finish()}};prefs.edit().apply {if(lang==null)remove("language")else putString("language",lang)}.commit()}
 }
}
