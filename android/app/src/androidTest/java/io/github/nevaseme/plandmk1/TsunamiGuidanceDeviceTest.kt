package io.github.nevaseme.plandmk1

import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import io.github.nevaseme.plandmk1.ui.*
import io.github.nevaseme.plandmk1.ui.theme.PlanDMk1Theme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.UUID

/** No group or participant changes; only isolated guidance keys are written and removed. */
class TsunamiGuidanceDeviceTest {
 @get:Rule val compose=createEmptyComposeRule()
 @Test fun oncePerIncidentAtamiCameraAndNoInputFocus() {
  val inst=InstrumentationRegistry.getInstrumentation();val app=inst.targetContext.applicationContext as PlanDApplication
  val first="guidance-test-"+UUID.randomUUID();val second=first+"-next"
  val prefs=app.getSharedPreferences("tsunami-guidance",0)
  var incident by mutableStateOf(first);var language by mutableStateOf("en")
  inst.runOnMainSync{org.maplibre.android.MapLibre.getInstance(app)}
  try {
   shellComposeContent{CompositionLocalProvider(LocalUiLanguage provides language){PlanDMk1Theme{Surface{SituationScreen(emptyList(),emptyList(),MapPoint(35.0145,135.7625),System.currentTimeMillis(),incidentKey=incident)}}}}
   val map=java.util.concurrent.atomic.AtomicReference<org.maplibre.android.maps.MapLibreMap>()
   fun find(v:android.view.View):org.maplibre.android.maps.MapView?=if(v is org.maplibre.android.maps.MapView)v else if(v is android.view.ViewGroup)(0 until v.childCount).firstNotNullOfOrNull{find(v.getChildAt(it))} else null
   compose.waitUntil(30000){inst.runOnMainSync{ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).forEach{find(it.window.decorView)?.getMapAsync{m->map.set(m)}}};map.get()!=null}
   compose.onNodeWithText("Tsunami").performClick()
   compose.onNodeWithText("Tsunami inundation is shown for Atami only. Enter the tsunami height in meters (0–35).").assertIsDisplayed()
   compose.onNodeWithText("Go to Atami").assertIsNotFocused().performClick()
   compose.waitUntil(15000){var moved=false;inst.runOnMainSync{val p=map.get().cameraPosition.target;moved=p!=null&&kotlin.math.abs(p.latitude-35.095)<.001&&kotlin.math.abs(p.longitude-139.07)<.001};moved}
   compose.onAllNodes(hasSetTextAction()).onFirst().assertIsNotFocused()
   compose.onNodeWithText("Tsunami").performClick();compose.onNodeWithText("Tsunami").performClick();compose.onNodeWithText("Go to Atami").assertDoesNotExist()
   assertTrue(prefs.getBoolean(first,false))
   compose.runOnIdle{incident=second;language="ja"}
   compose.onNodeWithText("津波").performClick();compose.onNodeWithText("津波の浸水表示は熱海のみ対応しています。津波の高さをメートル単位で入力してください（0〜35m）。").assertIsDisplayed()
   compose.onNodeWithText("熱海へ移動").assertIsNotFocused();compose.onNodeWithText("閉じる").performClick();compose.onAllNodes(hasSetTextAction()).onFirst().assertIsNotFocused()
   compose.runOnIdle{incident=first;language="en"};compose.onNodeWithText("Tsunami").performClick();compose.onNodeWithText("Go to Atami").assertDoesNotExist()
  }finally{prefs.edit().remove(first).remove(second).commit();inst.runOnMainSync{ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).toList().forEach{it.finish()}}}
 }
}
