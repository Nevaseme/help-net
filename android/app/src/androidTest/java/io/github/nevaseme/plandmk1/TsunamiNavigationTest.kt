package io.github.nevaseme.plandmk1

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.*

/** Tsunami is a control in the existing map, not an additional menu destination. */
class TsunamiNavigationTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun menuDoesNotAddASeparateTsunamiPage() {
        val app=compose.activity.application as PlanDApplication
        val previous=app.installation.group.value
        compose.onNodeWithContentDescription("メニューを開く").performClick()
        compose.onNodeWithText("津波計算（実験）").assertDoesNotExist()
        compose.onNodeWithText("自分の情報").assertExists()
        Assert.assertEquals(previous,app.installation.group.value)
    }
}
