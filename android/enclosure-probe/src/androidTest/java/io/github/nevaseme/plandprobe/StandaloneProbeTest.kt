package io.github.nevaseme.plandprobe

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import io.github.nevaseme.plandmk1.enclosure.EnclosureProbeActivity
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Dedicated emulator app only. These tests never grant or use the microphone. */
class StandaloneProbeTest {
    @get:Rule val compose = createAndroidComposeRule<EnclosureProbeActivity>()

    @Test fun measurementRemainsExplicitCancelDiscardsAndCompletedTrialSurvivesRecreation() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("io.github.nevaseme.plandprobe", context.packageName)
        val destination = File(context.filesDir, "enclosure-trials.json")
        // Target is the dedicated disposable emulator app, never normal PlanD.
        destination.delete()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("センサーを20秒測る").assertIsNotEnabled()
        compose.onNodeWithText("完了した測定：0件").assertExists()
        compose.onNode(hasSetTextAction()).performTextInput("emulator-cancel")
        compose.onNodeWithText("センサーを20秒測る").performClick()
        compose.onNodeWithText("測定を中止").performScrollTo().performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithText("測定を中止しました。途中の結果は残していません").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("完了した測定：0件").assertExists()
        assertFalse(destination.exists())
        compose.onNode(hasSetTextAction()).performTextReplacement("emulator-exposed")
        compose.onNodeWithText("センサーを20秒測る").performScrollTo().performClick()
        compose.waitUntil(30000) { compose.onAllNodesWithText("測定が完了しました").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("完了した測定：1件").assertExists()
        val document = JSONObject(destination.readText())
        assertEquals(1, document.getJSONArray("trials").length())
        assertEquals("not-configured", document.getString("classification"))
        val trial = document.getJSONArray("trials").getJSONObject(0)
        assertEquals("io.github.nevaseme.plandprobe", trial.getJSONObject("application").getString("package"))
        assertEquals("enclosure-features-2", trial.getString("featureVersion"))
        assertEquals(.35, trial.getDouble("brightnessOverride"), 0.0)
        assertTrue(trial.getJSONObject("device").getInt("api") >= 24)
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("完了した測定：1件").assertExists()
        compose.onNodeWithText("結果を書き出す").performScrollTo().assertIsEnabled()
        compose.onNodeWithText("保存した測定結果を復元しました").assertExists()
    }
}
