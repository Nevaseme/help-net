package io.github.nevaseme.plandmk1

import androidx.compose.material3.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import io.github.nevaseme.plandmk1.ui.*
import io.github.nevaseme.plandmk1.ui.theme.PlanDMk1Theme
import org.junit.*
import org.maplibre.android.MapLibre
import org.maplibre.android.maps.MapView
import org.maplibre.android.style.sources.GeoJsonSource
import java.io.File

/** Empty residents and no group writes; exercises the existing map filter and retained-map tabs. */
class TsunamiMapTest {
    @get:Rule val compose=createComposeRule()
    @Test fun heightInputClearsLegacyResultsAndMapSurvivesTabChanges() {
        val inst=InstrumentationRegistry.getInstrumentation()
        val capture=mutableStateOf(false)
        inst.runOnMainSync { MapLibre.getInstance(inst.targetContext);MapLibre.setConnected(null) }
        compose.setContent { PlanDMk1Theme {
            var visible by remember { mutableStateOf(true) }
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                if(!capture.value) TextButton(onClick={visible=!visible}) {Text(if(visible)"別タブへ" else "地図へ戻る")}
                Box(Modifier.weight(1f)) {RetainedMapContent(visible) {SituationScreen(emptyList(),emptyList(),MapPoint(35.097,139.077),0,initialZoom=15.0)}}
            }
        } }
        try {
            var original:MapView?=null
            inst.runOnMainSync {original=ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).first().window.decorView.findViewWithTag("situation-map")}
            compose.onNodeWithText("津波").performClick()
            compose.onNodeWithText("10m格子").assertDoesNotExist()
            compose.onNodeWithText("20m格子（比較）").assertDoesNotExist()
            compose.onNodeWithContentDescription("津波の高さ（m）").performTextInput("-1")
            compose.onNodeWithContentDescription("津波の高さ（m）").performTextReplacement("7.25")
            compose.onNodeWithContentDescription("津波の高さ（m）").performTextReplacement("36")
            var count=-1
            compose.waitUntil(20000) {
                inst.runOnMainSync {
                    ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).first().window.decorView
                        .findViewWithTag<MapView>("situation-map")?.getMapAsync {map -> count=map.style?.getSourceAs<GeoJsonSource>("tsunami-depth")?.querySourceFeatures(null)?.size ?: -1}
                }
                count==0
            }
            var before:org.maplibre.android.camera.CameraPosition?=null
            inst.runOnMainSync {original!!.getMapAsync {before=it.cameraPosition}}
            compose.onNodeWithContentDescription("津波の高さ（m）").performTextReplacement("35")
            compose.waitUntil(20000) {
                inst.runOnMainSync {
                    ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).first().window.decorView
                        .findViewWithTag<MapView>("situation-map")?.getMapAsync {map -> count=map.style?.getSourceAs<GeoJsonSource>("tsunami-depth")?.querySourceFeatures(null)?.size ?: -1}
                }
                count>0
            }
            compose.onNodeWithText("別タブへ").performClick()
            compose.onNodeWithText("地図へ戻る").performClick()
            compose.onNodeWithContentDescription("津波の高さ（m）").assertTextContains("35")

            compose.onNodeWithContentDescription("津波の高さ（m）").performTextReplacement("20")
            compose.onNodeWithContentDescription("津波の高さ（m）").performImeAction()
            inst.runOnMainSync {original!!.getMapAsync {map ->
                Assert.assertEquals(before!!.target!!.latitude,map.cameraPosition.target!!.latitude,1e-7)
                Assert.assertEquals(before!!.target!!.longitude,map.cameraPosition.target!!.longitude,1e-7)
                Assert.assertEquals(before!!.zoom,map.cameraPosition.zoom,1e-7)
                Assert.assertNull(map.style?.getLayer("tsunami-reference-dot"))
                Assert.assertNull(map.style?.getSource("tsunami-reference"))
            }}
            compose.onNodeWithText("表示範囲").assertDoesNotExist()
            compose.onNodeWithText("凡例・計算条件").assertDoesNotExist()
            compose.onNodeWithText("凡例").performClick()
            compose.waitUntil(30000) {compose.onAllNodes(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription,"高さ 20m の事前計算結果")).fetchSemanticsNodes().isNotEmpty()}
            compose.onNodeWithText("津波の浸水深").assertExists()
            compose.onNodeWithText("津波の最大浸水深・計算条件").assertDoesNotExist()
            compose.onNodeWithText("3m以上").assertExists()
            compose.onNodeWithContentDescription(inst.targetContext.getString(R.string.approved_close)).performClick()
            compose.onNodeWithText("高さ 20m の事前計算結果").assertDoesNotExist()
            compose.runOnIdle {capture.value=true}
            compose.waitForIdle()
            inst.runOnMainSync {
                val activity=ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).first()
                androidx.core.view.WindowCompat.getInsetsController(activity.window,activity.window.decorView).isAppearanceLightStatusBars=true
            }
            android.os.SystemClock.sleep(500)
            val bitmap=inst.uiAutomation.takeScreenshot()
            File(inst.targetContext.getExternalFilesDir(null),"tsunami-height-input.png").outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
            bitmap.recycle()
            compose.waitForIdle()
            android.os.SystemClock.sleep(500)
            val mapShot=inst.uiAutomation.takeScreenshot()
            File(inst.targetContext.getExternalFilesDir(null),"tsunami-same-map.png").outputStream().use {mapShot.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
            mapShot.recycle()
            compose.onNodeWithText("凡例").performClick()
            compose.onNodeWithText("3m以上").assertExists()
            compose.waitForIdle()
            android.os.SystemClock.sleep(500)
            val legendShot=inst.uiAutomation.takeScreenshot()
            File(inst.targetContext.getExternalFilesDir(null),"tsunami-simple-legend.png").outputStream().use {legendShot.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
            legendShot.recycle()
            compose.onNodeWithContentDescription(inst.targetContext.getString(R.string.approved_close)).performClick()
            compose.onNodeWithText("津波").performClick()
            compose.onNodeWithText(inst.targetContext.getString(R.string.approved_show_safe)).assertExists()
            compose.onNodeWithContentDescription("津波の高さ（m）").assertTextContains("20")
            inst.runOnMainSync {Assert.assertSame(original,ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).first().window.decorView.findViewWithTag<MapView>("situation-map"))}
        } finally {inst.runOnMainSync {MapLibre.setConnected(null)}}
    }
}