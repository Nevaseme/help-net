package io.github.nevaseme.plandmk1

import android.os.Build
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.util.UUID

/** Emulator-only visible controls; no registration request or real group record is sent. */
class CloudSharingFlowTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun normalSharingScreenExposesInviteAndCreateAndRetainsDraftOnRecreate() {
        assertTrue(Build.FINGERPRINT.contains("generic")||Build.MODEL.contains("sdk"))
        val app=compose.activity.application as PlanDApplication;val previous=app.installation.group.value
        try {
            compose.runOnIdle {app.installation.setGroup("cloud-ui-${UUID.randomUUID().toString().take(8)}")}
            if(compose.onAllNodesWithText("インターネットで共有").fetchSemanticsNodes().isEmpty()) {
                compose.onNodeWithContentDescription("メニューを開く").performClick()
                compose.onNodeWithText("グループ・共有").performScrollTo().performClick()
            }
            compose.onNodeWithText("インターネットで共有").assertIsDisplayed()
            compose.onNodeWithText("招待で参加").performScrollTo().performClick()
            compose.onNodeWithText("招待コード").assertIsDisplayed().performTextInput("fictional-draft")
            compose.onNodeWithText("活動名").performTextInput("架空参加者")
            compose.onNodeWithText("作成").performClick()
            compose.onNodeWithText("グループ名").performTextInput("架空グループ")
            compose.onNodeWithText("グループを作成").performScrollTo().assertIsDisplayed()
            compose.activityRule.scenario.recreate()
            compose.onRoot().printToLog("VenueDraft")
            compose.onNodeWithText("架空グループ").assertExists()
            compose.onNodeWithText("招待で参加").performScrollTo().performClick()
            compose.onNodeWithText("fictional-draft").assertExists()
        }finally {compose.runOnIdle {app.installation.setGroup(previous)}}
    }
}
