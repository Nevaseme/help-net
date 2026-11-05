package io.github.nevaseme.plandmk1

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import io.github.nevaseme.plandmk1.ui.StatusFeedback
import io.github.nevaseme.plandmk1.ui.CloudSyncButton
import androidx.compose.ui.graphics.toPixelMap
import io.github.nevaseme.plandmk1.ui.theme.PlanDMk1Theme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.File

/** No account or network writes: reproduces repeated sync-state transitions in the real component. */
class StatusFeedbackLayoutTest {
    @get:Rule val compose = createComposeRule()

    @Test fun syncButtonKeepsItsColorWhilePreventingDuplicateRequests() {
        val busy = mutableStateOf(false)
        var requests = 0
        compose.setContent { PlanDMk1Theme {
            Surface { Column(Modifier.padding(16.dp)) { CloudSyncButton(busy.value) { requests++ } } }
        } }
        val button = compose.onNodeWithText("今すぐ同期")
        button.assertIsEnabled()
        val idle = button.captureToImage().toPixelMap()
        val idleColor = idle[idle.width/2, idle.height/5]
        compose.runOnIdle { busy.value = true }
        button.assertIsNotEnabled().assertIsDisplayed()
        val active = button.captureToImage().toPixelMap()
        assertEquals("Automatic sync must not flash the button background", idleColor, active[active.width/2,active.height/5])
        button.performClick()
        assertEquals(0, requests)
        compose.runOnIdle { busy.value = false }
        button.assertIsEnabled().performClick()
        assertEquals(1, requests)
    }

    @Test fun progressDoesNotMoveFollowingControls() {
        val busy = mutableStateOf(false)
        compose.setContent { PlanDMk1Theme {
            Surface(Modifier.fillMaxSize()) {
                Column(Modifier.statusBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("グループ・共有", style = MaterialTheme.typography.headlineSmall)
                    Text("画面確認用グループ（架空）")
                    StatusFeedback(if (busy.value) "同期しています…" else "未送信の情報があります", busy = busy.value)
                    CloudSyncButton(busy.value) { }
                    Text("近くの端末と共有")
                }
            }
        } }
        val idle = compose.onNodeWithText("今すぐ同期").fetchSemanticsNode().boundsInRoot
        repeat(6) {
            compose.runOnIdle { busy.value = true }
            compose.onNode(hasProgressBarRangeInfo(androidx.compose.ui.semantics.ProgressBarRangeInfo.Indeterminate)).assertExists()
            val active = compose.onNodeWithText("今すぐ同期").fetchSemanticsNode().boundsInRoot
            assertEquals("Progress must not move the following button", idle.top, active.top, 0.01f)
            if (it == 0) capture("sync-busy.png")
            compose.runOnIdle { busy.value = false }
            compose.onNode(hasProgressBarRangeInfo(androidx.compose.ui.semantics.ProgressBarRangeInfo.Indeterminate)).assertDoesNotExist()
            assertEquals(idle, compose.onNodeWithText("今すぐ同期").fetchSemanticsNode().boundsInRoot)
        }
        capture("sync-idle.png")
    }

    private fun capture(name: String) {
        val inst = InstrumentationRegistry.getInstrumentation()
        inst.uiAutomation.takeScreenshot()?.let { bitmap ->
            File(inst.targetContext.getExternalFilesDir(null), name).outputStream().use {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
            bitmap.recycle()
        }
    }
}

