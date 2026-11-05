package io.github.nevaseme.plandmk1
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.input.ImeAction
import androidx.test.platform.app.InstrumentationRegistry
import io.github.nevaseme.plandmk1.ui.*
import io.github.nevaseme.plandmk1.ui.theme.PlanDMk1Theme
import org.maplibre.android.MapLibre
import org.junit.*
import org.junit.Assert.*

class HomeUxTest {
 private fun map():org.maplibre.android.maps.MapLibreMap? {
  var result:org.maplibre.android.maps.MapLibreMap?=null
  InstrumentationRegistry.getInstrumentation().runOnMainSync {
   fun maps(v:android.view.View):List<org.maplibre.android.maps.MapView> = if(v is org.maplibre.android.maps.MapView)listOf(v) else if(v is android.view.ViewGroup)(0 until v.childCount).flatMap{maps(v.getChildAt(it))} else emptyList()
   android.view.inspector.WindowInspector.getGlobalWindowViews().flatMap{maps(it)}.lastOrNull{it.isShown}?.getMapAsync{result=it}
  };return result
 }
 private fun latitude():Double {val controller=map();var value=Double.NaN;InstrumentationRegistry.getInstrumentation().runOnMainSync {value=controller?.cameraPosition?.target?.latitude?:Double.NaN};return value}
 @get:Rule val compose=createEmptyComposeRule()
 @Before fun init(){val i=InstrumentationRegistry.getInstrumentation();i.runOnMainSync{MapLibre.getInstance(i.targetContext)}}
 @Test fun searchClearCurrentLocationAndCancelDoNotSave(){
  var located=0;var saved=0;var canceled=0
  shellComposeContent {PlanDMk1Theme {BuildingLocationPicker(MapPoint(35.0145,135.7625),{_,_->saved++},{canceled++},locate={located++;MapPoint(35.0112,135.7748)})}}
  val query=compose.onNodeWithText("住所・建物名を検索")
  query.assert(SemanticsMatcher.expectValue(SemanticsProperties.ImeAction,ImeAction.Search)).performTextInput("左京区讃州寺町203")
  query.performImeAction()
  compose.waitUntil(60000){compose.onAllNodesWithText("京都府京都市左京区讃州寺町203 付近").fetchSemanticsNodes().isNotEmpty()}
  compose.onNodeWithContentDescription("入力を消す").performClick();query.assertTextEquals("住所・建物名を検索","")
  compose.onNodeWithText("現在地").performClick();compose.waitUntil(15000){located==1}
  compose.onNodeWithText("編集").performClick()
  compose.onNodeWithText("登録する住所・建物名").performClick()
  compose.onNodeWithText("戻る").assertIsDisplayed()
  compose.onNodeWithText("登録する住所・建物名").assert(SemanticsMatcher.expectValue(SemanticsProperties.ImeAction,ImeAction.Done))
  compose.onNodeWithText("戻る").performClick();assertEquals(0,saved);assertEquals(1,canceled)
 }
 @Test fun currentLocationFailureKeepsAddress(){
  shellComposeContent {PlanDMk1Theme {BuildingLocationPicker(MapPoint(35.0145,135.7625),{_,_->},{},initialReference=io.github.nevaseme.plandmk1.core.BuildingLocationReference("確認済み住居"),locate={error("現在地を取得できません")})}}
  compose.onNodeWithText("現在地").performClick();compose.onNodeWithText("現在地を取得できません").assertExists();compose.onNodeWithText("確認済み住居").assertExists()
 }
 @Test fun firstSearchAndRepeatedCurrentLocationMoveActualMap(){
  shellComposeContent {PlanDMk1Theme {BuildingLocationPicker(MapPoint(35.6812,139.7671),{_,_->},{},initialQuery="左京区讃州寺町203",autoSearch=true,locate={MapPoint(35.0145,135.7625)})}}
  compose.waitUntil(60000){compose.onAllNodesWithText("京都府京都市左京区讃州寺町203 付近").fetchSemanticsNodes().isNotEmpty()}
  compose.waitUntil(60000){kotlin.math.abs(latitude()-35.011046)<.0001}
  compose.onNodeWithText("現在地").performClick()
  compose.waitUntil(15000){kotlin.math.abs(latitude()-35.0145)<.0001}
  val controller=checkNotNull(map());InstrumentationRegistry.getInstrumentation().runOnMainSync {controller.cameraPosition=org.maplibre.android.camera.CameraPosition.Builder(controller.cameraPosition).target(org.maplibre.android.geometry.LatLng(35.6812,139.7671)).build()}
  compose.waitUntil(10000){kotlin.math.abs(latitude()-35.6812)<.0001}
  compose.onNodeWithText("現在地").performClick()
  compose.waitUntil(15000){kotlin.math.abs(latitude()-35.0145)<.0001}
 }
}
