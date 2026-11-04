package io.github.nevaseme.plandmk1

import android.content.res.Configuration
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import io.github.nevaseme.plandmk1.ui.*
import io.github.nevaseme.plandmk1.ui.theme.PlanDMk1Theme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.Locale

/** In-memory components only: no profile, group, location or server changes. */
class BuildingStatusPrivacyDeviceTest {
    @get:Rule val compose=createEmptyComposeRule()
    private val start=1_800_000_000_000L
    private val home=MapHome("building","京都府京都市中京区少将井町245-1",MapPoint(35.0,135.0),built=1995,floors=2)
    private val phone=MapPoint(35.1,135.1)
    @Composable private fun Fixture(content:@Composable ()->Unit) {
        val context=LocalContext.current
        val english=remember(context){context.createConfigurationContext(Configuration(context.resources.configuration).apply {setLocale(Locale.ENGLISH)})}
        CompositionLocalProvider(LocalContext provides english,LocalUiLanguage provides "en") {PlanDMk1Theme {Surface(Modifier.fillMaxSize()){content()}}}
    }

    @Test fun buildingTotalsKeepHiddenResidentsAndDisclosedRowJumpsToPhone() {
        val now=mutableStateOf(start+179_999)
        val source=listOf(
            MapPerson("unanswered","Disclosed neighbor",MapSafety.UNANSWERED,phone,start,home=home,bedroom="Floor 2",demo=true),
            MapPerson("safe","Hidden safe name",MapSafety.SAFE,phone,start,home=home),
            MapPerson("rescued","Hidden rescued name",MapSafety.RESCUED,phone,start,home=home,rescuedAt=start+20),
            MapPerson("sos","Hidden SOS home link",MapSafety.SOS,phone,start,home=home),
        )
        var jump:MapPoint?=null
        var detail:MapPerson?=null
        shellComposeContent {Fixture {BuildingMapDetail(home,BuildingResidentCounts(4,1,1,1,1),source.map {projectPersonPrivacy(it,start,now.value)},now.value,{jump=it},{detail=it},{})}}
        compose.onNodeWithText("Total residents: 4").assertIsDisplayed()
        compose.onNodeWithText("Safe 1 · No response 1\nSOS 1 · Rescued 1").assertIsDisplayed()
        compose.onNodeWithText("2 floors").assertIsDisplayed()
        compose.onNodeWithText("Disclosed neighbor").assertDoesNotExist()
        compose.onNodeWithText("Hidden safe name").assertDoesNotExist()
        compose.onNodeWithText("Hidden rescued name").assertDoesNotExist()
        compose.onNodeWithText("Hidden SOS home link").assertDoesNotExist()
        capture("building-protected.png")
        compose.runOnIdle {now.value=start+180_000}
        compose.onNodeWithText("Disclosed neighbor").performScrollTo().performClick()
        compose.runOnIdle {assertEquals(phone,jump);assertNotEquals(home.point,jump);assertNull(detail)}
        capture("building-disclosed.png")
    }

    @Test fun safeAndRescuedSheetsStayAnonymousAndOnlyAdminGetsUndo() {
        val person=mutableStateOf(MapPerson("p","Private name",MapSafety.SAFE,phone,start,home=home,bedroom="Private bedroom",memo="Private memo",impactAt=start,enclosedAt=start))
        val admin=mutableStateOf(false)
        var undos=0
        var homeOpens=0
        shellComposeContent {Fixture {PersonMapDetail(person.value,start+200_000,true,admin.value,{}, {undos++},{homeOpens++},{},{Text("Private history")})}}
        compose.onNodeWithText("Safe resident").assertIsDisplayed()
        assertPrivateFieldsAbsent()
        capture("safe-anonymous.png")
        compose.runOnIdle {person.value=person.value.copy(state=MapSafety.RESCUED,rescuedAt=start+100)}
        compose.onNodeWithText("Rescued resident").assertIsDisplayed()
        compose.onNodeWithText("Rescued at: ",substring=true).assertIsDisplayed()
        compose.onNodeWithText("Correct rescued status").assertDoesNotExist()
        assertPrivateFieldsAbsent()
        compose.runOnIdle {admin.value=true}
        compose.onNodeWithText("Correct rescued status").performClick()
        compose.runOnIdle {assertEquals(1,undos);assertEquals(0,homeOpens)}
        capture("rescued-admin-anonymous.png")
    }
    @Test fun shortObservationLabelsAndTsunamiLegendInEnglish() {
        shellComposeContent {Fixture {androidx.compose.foundation.layout.Column {TsunamiLegend();Text(uiText("impact"));Text(uiText("enclosed"));Text(tsunamiText("range",true))}}}
        compose.onNodeWithText("Tsunami inundation depth").assertIsDisplayed()
        compose.onNodeWithText("3m or more").assertIsDisplayed()
        compose.onNodeWithText("Impact").assertIsDisplayed()
        compose.onNodeWithText("Enclosed space").assertIsDisplayed()
        compose.onNodeWithText("Enter a whole number from 0 to 35m").assertIsDisplayed()
        capture("tsunami-labels-en.png")
    }
    @Test fun observationCardsUseAtMostTwoEnglishLines() {
        val person=MapPerson("p","Casey Brown",MapSafety.UNANSWERED,phone,start,home=home,bedroom="Floor 2",impactAt=start,enclosedAt=start,demo=true,fictional=true)
        shellComposeContent {Fixture {PersonMapDetail(person,start+200000,true,false,{},{},{},{},{})}}
        compose.onNodeWithText("Enclosed space").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Enclosed space").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) {action->
            val layouts=mutableListOf<androidx.compose.ui.text.TextLayoutResult>();action(layouts);assertTrue(layouts.isNotEmpty());assertTrue(layouts.all {it.lineCount<=2})
        }
        capture("observation-cards-en.png")
    }
    private fun assertPrivateFieldsAbsent() {
        listOf("Private name","Private bedroom","Private memo","Private history",home.address,"245-1 Shoshoicho, Nakagyo-ku, Kyoto, Kyoto Prefecture").forEach {compose.onNodeWithText(it).assertDoesNotExist()}
    }
    private fun capture(name:String) {
        val inst=InstrumentationRegistry.getInstrumentation()
        inst.uiAutomation.takeScreenshot()?.let {bitmap->File(inst.targetContext.getExternalFilesDir(null),name).outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()}
    }
}
