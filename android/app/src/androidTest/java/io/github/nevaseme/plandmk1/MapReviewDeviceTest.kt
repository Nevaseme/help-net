package io.github.nevaseme.plandmk1

import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import io.github.nevaseme.plandmk1.ui.*
import io.github.nevaseme.plandmk1.ui.theme.PlanDMk1Theme
import org.junit.Assert.*
import org.junit.*
import java.io.File
import java.util.Locale

class MapReviewDeviceTest {
 @get:Rule val compose=createEmptyComposeRule()
 @After fun finish(){InstrumentationRegistry.getInstrumentation().runOnMainSync {ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).toList().forEach {it.finish()}}}
 @Composable fun Fixture(scale:Float=1f,content:@Composable ()->Unit){
  val context=LocalContext.current
  val configuration=LocalConfiguration.current
  val owner=LocalActivityResultRegistryOwner.current
  val english=remember(context,configuration) {context.createConfigurationContext(Configuration(configuration).apply {setLocale(Locale.ENGLISH)})}
  val density=LocalDensity.current
  remember {org.maplibre.android.MapLibre.getInstance(context)}
  CompositionLocalProvider(LocalActivityResultRegistryOwner provides requireNotNull(owner),LocalContext provides english,LocalUiLanguage provides "en",LocalDensity provides Density(density.density,scale)) {PlanDMk1Theme {Surface(Modifier.fillMaxSize()){Box(Modifier.fillMaxSize(),contentAlignment=androidx.compose.ui.Alignment.TopCenter){Column(Modifier.width(360.dp).fillMaxHeight().safeDrawingPadding()){ApprovedHeader("Drill",null,onNavigate={});Box(Modifier.weight(1f)){content()};ApprovedTabs(1){}}}}}}
 }
 fun capture(name:String){val inst=InstrumentationRegistry.getInstrumentation();val file=File(inst.targetContext.filesDir,"map-review/$name.png");file.parentFile!!.mkdirs();inst.uiAutomation.takeScreenshot().let {b->file.outputStream().use {b.compress(Bitmap.CompressFormat.PNG,100,it)};b.recycle()}}
 @Test fun toolbarShowsAllActionsAt360dp(){
  shellComposeContent {Fixture {SituationScreen(emptyList(),emptyList(),MapPoint(35.0119,135.7582),System.currentTimeMillis())}}
  compose.onNodeWithText("Tsunami").assertIsDisplayed()
  compose.onNodeWithText("Location unknown").assertIsDisplayed()
  compose.onNodeWithText("Show safe").assertIsDisplayed()
  compose.onNodeWithText("Location unknown").performClick()
  compose.onNodeWithTag("approved-sheet").assertIsDisplayed()
  compose.onNodeWithTag("sheet-drag-handle").performClick()
  compose.onNodeWithTag("approved-sheet").assertDoesNotExist()
  compose.onNodeWithText("Legend").performClick()
  compose.onNodeWithTag("approved-sheet").assertIsDisplayed()
  val top=compose.onNodeWithTag("approved-sheet").fetchSemanticsNode().boundsInRoot.top
  compose.onRoot().performTouchInput { click(Offset(center.x,top-24f)) }
  compose.onNodeWithTag("approved-sheet").assertDoesNotExist()
  capture("toolbar-360")
 }
 @Test fun toolbarWrapsWithoutHidingActionsAtLargeType(){
  shellComposeContent {Fixture(1.5f) {SituationScreen(emptyList(),emptyList(),MapPoint(35.0119,135.7582),System.currentTimeMillis())}}
  compose.onNodeWithText("Tsunami").assertIsDisplayed();compose.onNodeWithText("Location unknown").assertIsDisplayed();compose.onNodeWithText("Show safe").assertIsDisplayed()
  capture("toolbar-large-text")
 }
 @Test fun handleTracksFingerReturnsAndDismisses(){
  var closed=false
  shellComposeContent {Fixture {ApprovedSheet("Building details",{closed=true},maxHeightFraction=.6f){Text("No response")}}}
  val start=compose.onNodeWithTag("approved-sheet").fetchSemanticsNode().boundsInRoot.top
  compose.onNodeWithTag("sheet-drag-handle").performTouchInput {down(center);moveBy(Offset(0f,90f))}
  val moved=compose.onNodeWithTag("approved-sheet").fetchSemanticsNode().boundsInRoot.top
  assertTrue("Sheet follows held finger",moved>start+30f)
  capture("drag-held")
  compose.onNodeWithTag("sheet-drag-handle").performTouchInput {up()}
  compose.waitForIdle()
  assertFalse(closed)
  assertEquals(start,compose.onNodeWithTag("approved-sheet").fetchSemanticsNode().boundsInRoot.top,2f)
  compose.onNodeWithTag("sheet-drag-handle").performTouchInput {swipeDown(endY=height+350f,durationMillis=500)}
  compose.waitUntil(3000){closed}
 }
 @Test fun buildingShowsOnlyUnansweredAndBriefOutsideReason(){
  val now=System.currentTimeMillis();val home=MapHome("b","御池之町301",MapPoint(35.0119,135.7582),built=1999,floors=10)
  val people=listOf(MapPerson("u","Unanswered neighbor",MapSafety.UNANSWERED,null,null,home=home),MapPerson("s","SOS neighbor",MapSafety.SOS,null,null,home=home))
  var outside by mutableStateOf(false)
  shellComposeContent {Fixture {BuildingMapDetail(home,BuildingResidentCounts(7,3,2,2,0),if(outside)emptyList()else people,now,{},{},{},outsideNeighborhood=outside)}}
  compose.onNodeWithText("Unanswered neighbor").performScrollTo().assertIsDisplayed()
  compose.onNodeWithText("SOS neighbor").assertDoesNotExist()
  capture("building-same-neighborhood")
  compose.runOnIdle {outside=true}
  compose.onNodeWithText("This building is outside your neighborhood.").performScrollTo().assertIsDisplayed()
  compose.onNodeWithText("Unanswered neighbor").assertDoesNotExist()
  capture("building-outside-neighborhood")
 }
}
