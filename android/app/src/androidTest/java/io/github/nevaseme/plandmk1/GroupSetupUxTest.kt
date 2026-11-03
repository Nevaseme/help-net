package io.github.nevaseme.plandmk1

import androidx.compose.runtime.*
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import io.github.nevaseme.plandmk1.ui.VenueSetupForm
import io.github.nevaseme.plandmk1.ui.theme.PlanDMk1Theme
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*

class GroupSetupUxTest {
    @get:Rule val compose=createEmptyComposeRule()

    @Test fun shortPasswordExplainsRequirementAndValidPasswordStartsSelectedMode() {
        var password by mutableStateOf("123456")
        var mode by mutableStateOf("KYOTO")
        var starts=0
        var submitted=""
        shellComposeContent { PlanDMk1Theme {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                VenueSetupForm(password,{password=it},false,mode,{mode=it}){starts++;submitted=it}
            }
        } }
        val start=compose.onNodeWithText("デモを開始する")
        start.assertIsEnabled().performScrollTo().performClick()
        compose.onNodeWithText("8〜128文字で入力してください（現在6文字）").assertExists()
        assertEquals(0,starts)
        compose.onNodeWithText("参加パスワード").performTextReplacement("12345678")
        compose.onNodeWithText("参加者の現在地").performScrollTo().performClick()
        start.performScrollTo().performClick()
        assertEquals(1,starts)
        assertEquals("ACTUAL",submitted)
        compose.onNodeWithText("参加パスワード").performTextReplacement("x".repeat(129))
        start.performScrollTo().performClick()
        compose.onNodeWithText("8〜128文字で入力してください（現在129文字）").assertExists()
        assertEquals(1,starts)
    }

    @Test fun inFlightStartShowsProgressAndPreventsAnotherSubmission() {
        var starts=0
        shellComposeContent {PlanDMk1Theme {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                VenueSetupForm("12345678",{},true,"KYOTO",{}){starts++}
            }
        }}
        compose.onNodeWithText("開始しています…").performScrollTo().assertIsNotEnabled()
        assertEquals(0,starts)
    }
}
