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
        }finally{inst.runOnMainSync{models.clear();app.installation.setGroup(previous);ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).toList().forEach{it.finish()}};prefs.edit().apply{if(previousLanguage==null)remove("language")else putString("language",previousLanguage)}.commit()}
    }
}
