package io.github.nevaseme.plandmk1

import android.app.Activity
import android.content.ContextWrapper
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import io.github.nevaseme.plandmk1.ui.HomeLocationPicker
import io.github.nevaseme.plandmk1.ui.theme.PlanDMk1Theme
import kotlinx.coroutines.awaitCancellation
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.Before
import androidx.test.platform.app.InstrumentationRegistry
import org.maplibre.android.MapLibre
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMap

class HomeLocationPickerTest {
 @get:Rule val compose=createComposeRule()
 @Before fun initializeMap() {InstrumentationRegistry.getInstrumentation().runOnMainSync {MapLibre.getInstance(InstrumentationRegistry.getInstrumentation().targetContext)}}
 private var activity:Activity?=null
 private val fallback=MapPoint(35.681,139.767)
 private fun maps(v:View):List<MapView> = if(v is MapView)listOf(v) else if(v is ViewGroup)(0 until v.childCount).flatMap {maps(v.getChildAt(it))} else emptyList()
 private fun assertCenter(expected:MapPoint) {
  var map:MapLibreMap?=null
  compose.waitUntil(20_000) {compose.runOnIdle {if(map==null)maps(activity!!.window.decorView).firstOrNull()?.getMapAsync{map=it}};map!=null}
  compose.runOnIdle {assertEquals(expected.latitude,map!!.cameraPosition.target!!.latitude,.0001);assertEquals(expected.longitude,map!!.cameraPosition.target!!.longitude,.0001)}
 }
 @Composable private fun CaptureActivity() {var c=LocalContext.current;while(c is ContextWrapper&&c !is Activity)c=c.baseContext;activity=c as Activity}
 @Test fun currentFixOverridesStoredHomeWithoutSaving() {
  val current=MapPoint(35.0145,135.7625);var picks=0;var calls=0
  compose.setContent {PlanDMk1Theme {CaptureActivity();HomeLocationPicker(fallback,{picks++},{}){calls++;current}}}
  compose.onNodeWithText("住居の位置をタップ").assertIsDisplayed();assertCenter(current)
  assertEquals(1,calls);assertEquals(0,picks)
 }
 @Test fun unavailableFixShowsFallbackWithExplanation() {
  compose.setContent {PlanDMk1Theme {CaptureActivity();HomeLocationPicker(fallback,{}, {}){error("Location disabled")}}}
  compose.onNodeWithText("現在地を取得できませんでした。地図を動かして住居を選んでください。").assertIsDisplayed();assertCenter(fallback)
 }
 @Test fun cancelStopsPendingAcquisitionWithoutPicking() {
  val visible=mutableStateOf(true);var started=false;var cancelled=false;var picks=0
  compose.setContent {PlanDMk1Theme {if(visible.value)HomeLocationPicker(fallback,{picks++},{visible.value=false}){started=true;try {awaitCancellation()}finally {cancelled=true}}}}
  compose.waitUntil(5_000){started};compose.onNodeWithText("位置選択をやめる").performClick()
  compose.waitUntil(5_000){cancelled};assertEquals(0,picks)
 }
}
