package io.github.nevaseme.plandmk1

import android.graphics.Bitmap
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import io.github.nevaseme.plandmk1.cloud.*
import io.github.nevaseme.plandmk1.ui.*
import io.github.nevaseme.plandmk1.ui.theme.PlanDMk1Theme
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID

/** Local fixture; restores the user's group and language, with no public-server writes. */
class VenueExperienceDeviceTest {
    @get:Rule val compose=createEmptyComposeRule()
    @Test fun drillUsesEnglishAndRequiresSafetyBeforeEveryDestination()=verify("DRILL")
    @Test fun earthquakePresentationKeepsTrainingBehavior()=verify("EARTHQUAKE")
    private fun verify(mode:String) {
        val inst=InstrumentationRegistry.getInstrumentation()
        val app=inst.targetContext.applicationContext as PlanDApplication
        val previous=app.installation.group.value
        val prefs=app.getSharedPreferences("interface-language",0)
        val previousLanguage=prefs.getString("language",null)
        val codec=Json{ignoreUnknownKeys=true};val models=ViewModelStore()
        val raw=inst.context.assets.open("venue-experience-fixture.json").bufferedReader().use{it.readText()}
        val original=codec.parseToJsonElement(raw).jsonObject.getValue("envelope").jsonObject
        val device=original.getValue("roster").jsonObject.getValue("members").jsonArray.first().jsonObject.getValue("deviceIds").jsonArray.first().jsonPrimitive.content
        val group=UUID.randomUUID().toString()
        val payload=codec.parseToJsonElement(raw.replace(device,app.installation.localId).replace(original.getValue("groupId").jsonPrimitive.content,group).replace("Demo organizer","Organizer").replace("Demo rescue team","Rescue team")).jsonObject
        val source=codec.decodeFromJsonElement<CloudRosterEnvelope>(payload.getValue("envelope"))
        val venue=source.venue!!.copy(presentationMode=mode)
        val envelope=source.copy(venue=venue)
        lateinit var model:PlanViewModel
        fun shot(name:String){val f=File(app.filesDir,"venue-experience/$mode-$name.png");f.parentFile!!.mkdirs();inst.uiAutomation.takeScreenshot().let{b->f.outputStream().use{b.compress(Bitmap.CompressFormat.PNG,100,it)};b.recycle()}}
        fun englishScreen(){
            val found=mutableListOf<String>();val japanese=Regex("[\\u3040-\\u30ff\\u3400-\\u9fff]")
            fun scan(node:AccessibilityNodeInfo){if(node.isVisibleToUser&&node.packageName?.toString()==app.packageName)for(value in listOf(node.text,node.contentDescription))if(value!=null&&japanese.containsMatchIn(value))found.add(value.toString());for(i in 0 until node.childCount)node.getChild(i)?.let(::scan)}
            inst.uiAutomation.rootInActiveWindow?.let(::scan);assertTrue("Japanese UI remains: $found",found.isEmpty())
        }
        try {
            prefs.edit().remove("language").commit()
            runBlocking{val journal=RoomCloudJournal(app.database,group,app.installation.localId);journal.updateRoster(envelope,app.installation.localId);journal.mergePage(codec.decodeFromJsonElement<List<CloudRecord>>(payload.getValue("records")))}
            CloudSettings(app).saveVenue(envelope)
            inst.runOnMainSync{org.maplibre.android.MapLibre.getInstance(app);app.installation.setGroup(group);app.installation.selectIncident(group,venue.incidentId);app.cloud.venueChanges.value++;model=PlanViewModel(app);models.put("venue-experience",model)}
            compose.waitUntil(30000){model.incidentUi.value.canEdit&&model.activeVenue.value!=null}
            shellComposeContent{PlanDMk1Theme{PlanApp(model)}}
            compose.onNodeWithText("Save status and open map").assertIsDisplayed()
            compose.onNodeWithText(if(mode=="DRILL")"Drill" else "Earthquake",useUnmergedTree=true).assertExists()
            for(tab in listOf("Map","People","Tasks")){compose.onNodeWithText(tab,useUnmergedTree=true).performClick();compose.onNodeWithText("Save status and open map").assertIsDisplayed()}
            compose.onNodeWithContentDescription("Open menu").performClick();compose.onNodeWithText("Save status and open map").assertIsDisplayed();englishScreen();shot("safety")
            compose.onNodeWithText("I’m safe",useUnmergedTree=true).performClick();compose.onNodeWithText("Save status and open map").performClick()
            compose.waitUntil(15000){compose.onAllNodesWithText("This is a demo. Your location is set to Kyoto to make the experience easier to follow.").fetchSemanticsNodes().isNotEmpty()}
            englishScreen();shot("notice");compose.onNodeWithText("Continue").performClick()
            compose.onNodeWithText("Map").assertIsSelected()
            compose.waitForIdle();android.os.SystemClock.sleep(2000);englishScreen();shot("map")
            repeat(4){
                compose.onNodeWithContentDescription("Zoom in",useUnmergedTree=true).performClick()
                compose.onRoot().performTouchInput {swipe(androidx.compose.ui.geometry.Offset(width*.65f,height*.5f),androidx.compose.ui.geometry.Offset(width*.35f,height*.5f),400)}
                compose.onRoot().performTouchInput {swipe(androidx.compose.ui.geometry.Offset(width*.35f,height*.5f),androidx.compose.ui.geometry.Offset(width*.65f,height*.5f),400)}
                compose.onNodeWithContentDescription("Zoom out",useUnmergedTree=true).performClick()
            }
            compose.onNodeWithText("Map").assertIsSelected();englishScreen()
            for(tab in listOf("People","Tasks","Safety","Map")){compose.onNodeWithText(tab,useUnmergedTree=true).performClick();compose.waitForIdle();englishScreen()}
            compose.onNodeWithText("Safety",useUnmergedTree=true).performClick()
            val firstAnswerId=model.incidentUi.value.current!!.answers.getValue(model.localId).eventId
            compose.onNodeWithText("I need help",useUnmergedTree=true).performClick()
            compose.onNodeWithText("Update my status").performScrollTo().performClick()
            compose.waitUntil(15000){model.incidentUi.value.current?.answers?.get(model.localId)?.eventId!=firstAnswerId}
            compose.waitUntil(15000){model.saveStates.value["safety"]?.phase==io.github.nevaseme.plandmk1.core.SavePhase.SAVED}
            compose.waitForIdle()
            compose.onNodeWithText("SOS saved on this device.",substring=true).assertExists()
            compose.onNode(hasText("Safety") and hasClickAction()).assertIsSelected()
            compose.onNodeWithText("This is a demo. Your location is set to Kyoto to make the experience easier to follow.").assertDoesNotExist()
            englishScreen();shot("updated-safety")
            compose.onNodeWithText("Map",useUnmergedTree=true).performClick();compose.waitForIdle()
            if(InstrumentationRegistry.getArguments().getString("extendedUi")=="true") {
                fun visibleMap():org.maplibre.android.maps.MapView {
                    fun maps(v:android.view.View):List<org.maplibre.android.maps.MapView> = when(v) {
                        is org.maplibre.android.maps.MapView -> listOf(v)
                        is android.view.ViewGroup -> (0 until v.childCount).flatMap{maps(v.getChildAt(it))}
                        else -> emptyList()
                    }
                    var found:org.maplibre.android.maps.MapView?=null
                    inst.runOnMainSync{found=ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).flatMap{maps(it.window.decorView)}.lastOrNull{it.isShown}}
                    return requireNotNull(found)
                }
                fun markers():Int {
                    var count=-1
                    val mapView=visibleMap()
                    inst.runOnMainSync{mapView.getMapAsync{count=it.markers.size}}
                    return count
                }
                fun tapMap() {
                    val xy=IntArray(2);var width=0;var height=0
                    val mapView=visibleMap()
                    inst.runOnMainSync{mapView.let{it.getLocationOnScreen(xy);width=it.width;height=it.height}}
                    val time=android.os.SystemClock.uptimeMillis()
                    for(action in listOf(android.view.MotionEvent.ACTION_DOWN,android.view.MotionEvent.ACTION_UP)) {
                        val event=android.view.MotionEvent.obtain(time,android.os.SystemClock.uptimeMillis(),action,xy[0]+width*.55f,xy[1]+height*.55f,0)
                        event.source=android.view.InputDevice.SOURCE_TOUCHSCREEN
                        inst.uiAutomation.injectInputEvent(event,true);event.recycle()
                        if(action==android.view.MotionEvent.ACTION_DOWN)android.os.SystemClock.sleep(70)
                    }
                    compose.waitForIdle()
                    // MapLibre confirms a single tap after its double-tap window.
                    compose.waitUntil(5000){compose.onAllNodes(hasText("Use this location") and isEnabled()).fetchSemanticsNodes().isNotEmpty()}
                    android.os.SystemClock.sleep(400) // allow the native preview frame to reach the screenshot
                }
                compose.waitUntil(15000){markers()>3}
                val withSafe=markers()
                android.util.Log.i("PlanD-venue-ui","$mode show-safe markers=$withSafe")
                compose.onNodeWithText("Show safe").performClick();compose.waitForIdle()
                compose.waitUntil(10000){markers()<withSafe}
                // Annotation updates precede the native renderer's presented frame.
                android.os.SystemClock.sleep(1500);shot("hide-safe")
                android.util.Log.i("PlanD-venue-ui","$mode hide-safe markers=${markers()}")
                compose.onNodeWithText("Show safe").performClick();compose.waitForIdle()
                compose.waitUntil(10000){markers()>=withSafe}
                android.os.SystemClock.sleep(1500);shot("show-safe")
                compose.onNodeWithText("Location unknown").performClick()
                compose.onNodeWithText("Location unknown 2").assertIsDisplayed();englishScreen();shot("unknown")
                compose.onNodeWithContentDescription("Close").performClick()
                compose.onNodeWithContentDescription("Open menu").performClick()
                compose.onNodeWithText("Drills and earthquakes").assertExists();englishScreen();shot("menu")
                compose.onNodeWithContentDescription("Close").performClick()
                val existingReport=model.responseEvents.value.mapNotNull{it.change as? io.github.nevaseme.plandmk1.core.ResponseChange.Report}.first{!it.cancelled}
                val reportMap=visibleMap()
                inst.runOnMainSync{reportMap.getMapAsync{it.moveCamera(org.maplibre.android.camera.CameraUpdateFactory.newLatLngZoom(org.maplibre.android.geometry.LatLng(existingReport.latitude,existingReport.longitude),18.0))}}
                compose.waitUntil(15000){compose.onAllNodesWithContentDescription("Incident report").fetchSemanticsNodes().isNotEmpty()}
                compose.onAllNodesWithContentDescription("Incident report").onFirst().performClick();compose.waitForIdle()
                compose.onNodeWithText("Map").assertIsSelected()
                compose.onNodeWithTag("sheet-drag-handle").performTouchInput{swipe(center,androidx.compose.ui.geometry.Offset(center.x,center.y+350f),300)}
                compose.waitUntil(5000){compose.onAllNodesWithTag("sheet-drag-handle").fetchSemanticsNodes().isEmpty()}
                compose.onNodeWithText("Map").assertIsSelected()
                compose.onNodeWithText("Report an incident").performClick()
                compose.onNodeWithText("Use this location").assertIsNotEnabled()
                tapMap();compose.onNodeWithText("Use this location").assertIsEnabled();shot("report-preview")
                compose.onNodeWithText("Use this location").assertIsDisplayed().performClick()
                compose.onNodeWithContentDescription("Situation and details").performTextInput("Local preview check")
                compose.onNodeWithContentDescription("Situation and details").assertTextContains("Local preview check")
                compose.onNodeWithText("Report location selected · Change").performClick()
                compose.onNodeWithText("Use this location").assertIsNotEnabled()
                tapMap();compose.onNodeWithText("Use this location").assertIsEnabled();shot("report-form-preview")
                compose.onNodeWithText("Use this location").assertIsDisplayed().performClick()
                shot("report-preserved")
                compose.onNodeWithContentDescription("Situation and details").assertTextContains("Local preview check");englishScreen()
                compose.onNodeWithContentDescription("Close").performClick()
            }
        }finally{inst.runOnMainSync{models.clear();app.installation.setGroup(previous);ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).toList().forEach{it.finish()}};prefs.edit().apply{if(previousLanguage==null)remove("language")else putString("language",previousLanguage)}.commit()}
    }
}
