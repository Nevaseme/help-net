package io.github.nevaseme.plandmk1

import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.lifecycle.ViewModelStore
import io.github.nevaseme.plandmk1.core.*
import io.github.nevaseme.plandmk1.ui.*
import io.github.nevaseme.plandmk1.ui.theme.PlanDMk1Theme
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.util.UUID

class MenuAccessDeviceTest {
 @get:Rule val compose=createEmptyComposeRule()
 @Before fun initializeMaps(){compose.runOnUiThread{org.maplibre.android.MapLibre.getInstance(ApplicationProvider.getApplicationContext<PlanDApplication>())}}
 @Test fun englishAdminCanStartEarthquakeWithoutAHome()=runBlocking {
  val app=ApplicationProvider.getApplicationContext<PlanDApplication>();val old=app.installation.group.value;val prefs=app.getSharedPreferences("interface-language",0);val language=prefs.getString("language",null)
  val group=UUID.randomUUID().toString();val me=app.installation.localId;val models=ViewModelStore();lateinit var model:PlanViewModel
  app.quakes.importRoster(MemberRoster(groupId=group,members=listOf(GroupMember(me,"Administrator",MemberRole.ADMIN,listOf(me)))).encode(),me)
  try{
   prefs.edit().putString("language","en").commit()
   compose.runOnUiThread{app.installation.setGroup(group);model=PlanViewModel(app);models.put("qa",model)}
   compose.waitUntil(15000){model.roster.value?.groupId==group}
   var admin by mutableStateOf(false)
   shellComposeContent{PlanDMk1Theme{PlanLocalization{if(!admin)ApprovedMenu("Test group",{},{if(it==12)admin=true},{},administrator=true)else Column(Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState())){IncidentActions(model,false,administrator=true)}}}}
   compose.onNodeWithText("Administrator",substring=false).performClick()
   compose.onNodeWithText("Start earthquake",substring=false).performScrollTo().performClick()
   compose.onAllNodes(hasSetTextAction())[1].performTextInput("Kyoto")
   androidx.test.espresso.Espresso.closeSoftKeyboard()
   compose.onAllNodesWithText("Start earthquake").filter(hasClickAction()).onLast().performClick()
   compose.waitUntil(15000){model.incidentUi.value.current?.start?.training==false}
   assertEquals(QuakeAction.ADMIN_START,app.quakes.events(group).single().action)
   assertNull(app.store.all(group).find{it.originId==me})
   compose.waitForIdle();capture("admin-start-en")
   shellComposeContent{PlanDMk1Theme{PlanApp(model)}}
   compose.onNodeWithContentDescription("Open menu").performClick()
   compose.onAllNodesWithText("Map",substring=false).onFirst().performClick()
   compose.onNodeWithText("Tell us your current status").assertIsDisplayed()
   compose.onNodeWithText("Show safe").assertDoesNotExist()
   compose.onNodeWithText("I’m safe").performClick()
   compose.onNodeWithText("Save status and open map").performClick()
   compose.waitUntil(15000){model.incidentUi.value.current?.answers?.get(me)?.safety==Safety.SAFE}
   compose.runOnUiThread{model.endIncident()}
   compose.waitUntil(15000){model.incidentUi.value.current?.closed==true}
   assertNull(app.store.all(group).find{it.originId==me})
  }finally{compose.runOnUiThread{models.clear();app.installation.setGroup(old)};if(language==null)prefs.edit().remove("language").commit()else prefs.edit().putString("language",language).commit()}
 }
 @Test fun englishMapToolbarAndAssignedCurrentLocation(){
  val app=ApplicationProvider.getApplicationContext<PlanDApplication>();val prefs=app.getSharedPreferences("interface-language",0);val previous=prefs.getString("language",null)
  try{prefs.edit().putString("language","en").commit();val assigned=MapPoint(35.017,135.766)
   shellComposeContent{PlanDMk1Theme{PlanLocalization{Box(Modifier.fillMaxSize().systemBarsPadding()){SituationScreen(emptyList(),emptyList(),MapPoint(35.011,135.759),System.currentTimeMillis(),assignedLocation=assigned)}}}}
   compose.onNodeWithText("Show safe").assertIsDisplayed();val tsunami=compose.onNodeWithText("Tsunami").fetchSemanticsNode().boundsInRoot;val unknown=compose.onNodeWithText("Location unknown").fetchSemanticsNode().boundsInRoot
   assertTrue(unknown.left>tsunami.left);assertTrue(unknown.height<tsunami.height*3)
   var map:org.maplibre.android.maps.MapLibreMap?=null
   compose.runOnUiThread{
    fun find(v:android.view.View):org.maplibre.android.maps.MapView? {if(v is org.maplibre.android.maps.MapView)return v;if(v is android.view.ViewGroup)for(i in 0 until v.childCount)find(v.getChildAt(i))?.let{return it};return null}
    val host=androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED).last()
    checkNotNull(find(host.findViewById(android.R.id.content))).getMapAsync{map=it}
   }
   compose.waitUntil(15000){map!=null}
   compose.onNodeWithContentDescription("My location").performClick()
   compose.waitUntil(15000){var centered=false;compose.runOnUiThread{centered=map?.cameraPosition?.target?.let{kotlin.math.abs(it.latitude-assigned.latitude)<0.0001&&kotlin.math.abs(it.longitude-assigned.longitude)<0.0001}==true};centered}
   compose.waitForIdle();capture("toolbar-en")
  }finally{if(previous==null)prefs.edit().remove("language").commit()else prefs.edit().putString("language",previous).commit()}
 }
 private fun capture(name:String){val inst=InstrumentationRegistry.getInstrumentation();inst.uiAutomation.takeScreenshot()?.let{b->File(inst.targetContext.getExternalFilesDir(null),"nov05-$name.png").outputStream().use{b.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};b.recycle()}}
}
