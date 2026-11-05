package io.github.nevaseme.plandmk1

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import io.github.nevaseme.plandmk1.enclosure.EnclosureProbeActivity
import org.junit.Rule
import org.junit.Test

/** Emulator-only UI flow; never presses the microphone button or grants recording permission. */
class EnclosureProbeFlowTest {
    @get:Rule val compose = createAndroidComposeRule<EnclosureProbeActivity>()

    @Test fun openingDoesNotStartAndCancelledTrialDoesNotBecomeAResult() {
        compose.onNodeWithText("センサーを20秒測る").assertIsNotEnabled()
        compose.onNodeWithText("完了した測定：0件").assertExists()
        compose.onNode(hasSetTextAction()).performTextInput("emulator-cancel")
        compose.onNodeWithText("センサーを20秒測る").performClick()
        compose.onNodeWithText("測定を中止").performScrollTo().performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithText("測定を中止しました。途中の結果は残していません").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("完了した測定：0件").assertExists()
        compose.onNodeWithText("結果を書き出す").assertIsNotEnabled()
    }

    @Test fun explicitSensorTrialCompletesAndMakesFeatureExportAvailable() {
        compose.onNode(hasSetTextAction()).performTextInput("emulator-exposed")
        compose.onNodeWithText("センサーを20秒測る").performClick()
        compose.waitUntil(30000) { compose.onAllNodesWithText("測定が完了しました").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("完了した測定：1件").assertExists()
        compose.onNodeWithText("結果を書き出す").performScrollTo().assertIsEnabled()
    }
}
