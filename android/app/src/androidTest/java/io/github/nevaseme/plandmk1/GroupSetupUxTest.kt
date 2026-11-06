package io.github.nevaseme.plandmk1

import androidx.compose.runtime.*
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.asAndroidBitmap
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

    @Test fun existingGroupStartsBothModesWithoutPassword() {
        var mode by mutableStateOf("DRILL")
        var starts=0
        var submitted=""
        shellComposeContent { PlanDMk1Theme {
            Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState()).testTag("group-start-form")) {
                VenueSetupForm(false,presentationMode=mode,onPresentationMode={mode=it}){starts++;submitted=mode}
            }
        } }
        compose.onNodeWithText("Group password").assertDoesNotExist()
        compose.onNodeWithText("Participation password").assertDoesNotExist()
        compose.onNodeWithText("New participants (optional)").assertDoesNotExist()
        shot("GROUP-start-drill")
        compose.onNodeWithText("Start drill").assertIsEnabled().performScrollTo().performClick()
        assertEquals(1,starts)
        assertEquals("DRILL",submitted)
        compose.onNodeWithText("Earthquake").performScrollTo().performClick()
        shot("GROUP-start-earthquake")
        compose.onNodeWithText("Start earthquake").assertIsEnabled().performScrollTo().performClick()
        assertEquals(2,starts)
        assertEquals("EARTHQUAKE",submitted)
        val context=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        val config=android.content.res.Configuration(context.resources.configuration).apply{setLocale(java.util.Locale.ENGLISH)}
        assertEquals("Map legend",context.createConfigurationContext(config).getString(R.string.approved_legend))
    }

    @Test fun inFlightStartShowsProgressAndPreventsAnotherSubmission() {
        var starts=0
        shellComposeContent {PlanDMk1Theme {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                VenueSetupForm(true){starts++}
            }
        }}
        compose.onNodeWithText("Starting…").performScrollTo().assertIsNotEnabled()
        assertEquals(0,starts)
    }
    private fun shot(name:String){
        compose.waitForIdle()
        val inst=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        val file=java.io.File(inst.targetContext.filesDir,"venue-experience/$name.png");file.parentFile?.mkdirs()
        val bitmap=compose.onNodeWithTag("group-start-form").captureToImage().asAndroidBitmap();file.outputStream().use{bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
    }
}
