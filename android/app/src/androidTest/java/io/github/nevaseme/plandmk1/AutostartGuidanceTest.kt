package io.github.nevaseme.plandmk1

import android.content.Context
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import io.github.nevaseme.plandmk1.sharing.*
import io.github.nevaseme.plandmk1.ui.*
import io.github.nevaseme.plandmk1.ui.theme.PlanDMk1Theme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.Before
import org.junit.After

class AutostartGuidanceTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val languagePreferences get() = InstrumentationRegistry.getInstrumentation().targetContext.getSharedPreferences("interface-language",Context.MODE_PRIVATE)
    private var originalLanguage: String? = null
    @Before fun useJapanese() { originalLanguage=languagePreferences.getString("language",null);languagePreferences.edit().putString("language","ja").commit() }
    @After fun restoreLanguage() {
        val edit=languagePreferences.edit()
        if(originalLanguage==null)edit.remove("language") else edit.putString("language",originalLanguage)
        edit.commit()
    }

    @Test fun inaccessibleManufacturerSettingsFallsBackWithoutChangingAcknowledgement() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val prefs=context.getSharedPreferences("qa-autostart-intents",Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        val settings=AutostartSettings(context,AutostartVendor.XIAOMI,prefs)
        val opened=mutableListOf<android.content.Intent>()
        val result=settings.openWith { intent ->
            opened.add(intent)
            if(intent.component!=null)throw SecurityException("OEM page not exported")
        }
        assertEquals(AutostartDestination.APP_DETAILS,result)
        assertEquals(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,opened.last().action)
        assertEquals("package:${context.packageName}",opened.last().dataString)
        assertFalse(settings.handled.value)
        assertEquals(AutostartDestination.UNAVAILABLE,settings.openWith { throw android.content.ActivityNotFoundException() })
        assertEquals(AutostartVendor.XIAOMI,AutostartVendor.identify("Xiaomi","POCO"))
        assertEquals(AutostartVendor.VIVO,AutostartVendor.identify("vivo","iQOO"))
        assertNull(AutostartVendor.identify("Google","google"))
    }

    @Test fun membershipPromptWaitsForSetupAndDoesNotClaimPermissionAfterSettingsReturn() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences("qa-autostart-guide", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        val settings = AutostartSettings(context, AutostartVendor.XIAOMI, prefs)
        var eligible by mutableStateOf(false)
        var blocked by mutableStateOf(false)
        var manual by mutableStateOf(false)
        var opens = 0
        shellComposeContent { PlanLocalization { PlanDMk1Theme {
            AutostartGuidanceHost(settings, eligible && !blocked, manual, {manual=false},
                openSettings={opens++;AutostartDestination.APP_DETAILS})
        } } }
        compose.onNodeWithText("再起動後も安否共有を続ける").assertDoesNotExist()
        compose.runOnIdle { blocked=true;eligible=true }
        compose.onNodeWithText("再起動後も安否共有を続ける").assertDoesNotExist()
        compose.runOnIdle { blocked=false }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("設定を開く").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("設定を開く").performClick()
        compose.onNodeWithText("設定画面を直接開けませんでした。端末の設定で「自動起動」を検索し、PlanD Mk1をオンにしてください。").assertExists()
        compose.runOnIdle { assertEquals(1, opens);assertFalse(settings.handled.value) }
        compose.onNodeWithText("確認した").performClick()
        compose.runOnIdle { assertTrue(settings.handled.value);eligible=false }
        compose.runOnIdle { eligible=true }
        compose.onNodeWithText("再起動後も安否共有を続ける").assertDoesNotExist()
        // Recreated preference reader stays dismissed; user can still reopen from settings.
        assertTrue(AutostartSettings(context, AutostartVendor.XIAOMI, prefs).handled.value)
        compose.runOnIdle { manual=true }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("再起動後も安否共有を続ける").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("再起動後も安否共有を続ける").assertExists()
        compose.onNodeWithText("あとで").performClick()
        compose.runOnIdle { assertFalse(manual) }
        prefs.edit().clear().commit()
    }

    @Test fun unavailableSettingsStillShowsEnglishInstructionsAndCanDismiss() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val prefs=context.getSharedPreferences("qa-autostart-guide-en",Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        languagePreferences.edit().putString("language","en").commit()
        shellComposeContent { PlanLocalization {
            PlanDMk1Theme { AutostartGuidanceHost(AutostartSettings(context,AutostartVendor.VIVO,prefs),true,false,{},openSettings={AutostartDestination.UNAVAILABLE}) }
        } }
        compose.onNodeWithText("Open settings").performClick()
        compose.onNodeWithText("Could not open settings. Open your phone settings and search for Autostart, then enable PlanD Mk1.").assertExists()
        compose.onNodeWithText("Later").performClick()
        compose.onNodeWithText("Keep safety sharing after a restart").assertDoesNotExist()
        prefs.edit().clear().commit()
    }
}
