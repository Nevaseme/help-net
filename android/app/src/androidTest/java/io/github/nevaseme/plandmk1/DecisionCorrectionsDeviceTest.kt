package io.github.nevaseme.plandmk1

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import io.github.nevaseme.plandmk1.buildings.BuildingAssessmentRepository
import io.github.nevaseme.plandmk1.ui.*
import io.github.nevaseme.plandmk1.ui.theme.PlanDMk1Theme
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Uses packaged public PLATEAU data and in-memory people; never changes user records. */
class DecisionCorrectionsDeviceTest {
 @get:Rule val compose=createEmptyComposeRule()
 @org.junit.After fun closeFixture(){InstrumentationRegistry.getInstrumentation().runOnMainSync {
  androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED).toList().forEach {it.finish()}
 }}
 @Test fun sourceAttributesAreVisibleAndSafeDetailsCannotBeOpened()=runBlocking {
  val inst=InstrumentationRegistry.getInstrumentation();val context=inst.targetContext
  val point=MapPoint(35.015627382769296,135.76033315936462)
  val source=BuildingAssessmentRepository {context.assets.open(it)}.registeredBuilding("bldg_f383d3d7-a2dd-4a17-bf4d-02fd0ce0c27f",point.latitude,point.longitude)!!
  val home=MapHome("known-home","確認住居",point).withBuildingAttributes(source)
  val now=System.currentTimeMillis()
  val unreported=projectPersonPrivacy(MapPerson("unknown","確認対象",MapSafety.UNANSWERED,home=home,bedroom="Bedroom"),now-240000,now)
  val safe=projectPersonPrivacy(MapPerson("safe","PRIVATE SAFE NAME",MapSafety.SAFE,home=home,bedroom="PRIVATE BEDROOM",memo="PRIVATE MEMO",demo=true),now-240000,now)
  val ja=context.createConfigurationContext(android.content.res.Configuration(context.resources.configuration).apply {setLocale(java.util.Locale.JAPANESE)})
  shellComposeContent {CompositionLocalProvider(LocalUiLanguage provides "ja",androidx.compose.ui.platform.LocalContext provides ja){PlanDMk1Theme {SituationScreen(listOf(unreported,safe),listOf(home),point,now,listOnly=true)}}}
  compose.onNodeWithText("PRIVATE SAFE NAME").assertDoesNotExist();compose.onNodeWithText("PRIVATE BEDROOM").assertDoesNotExist();compose.onNodeWithText("PRIVATE MEMO").assertDoesNotExist()
  compose.onNode(hasText("無事の人") and hasClickAction()).assertIsNotEnabled().performTouchInput { click() }
  compose.onNodeWithContentDescription("詳細を閉じる").assertDoesNotExist()
  compose.onNodeWithText("確認対象").performClick()
  compose.onNodeWithText("RC造").performScrollTo().assertIsDisplayed()
  compose.onNodeWithText("3階建").assertIsDisplayed()
  compose.onNodeWithText("築${java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)-2012}年").assertIsDisplayed()
  inst.waitForIdleSync();val dir=File(context.filesDir,"decision-corrections").apply {mkdirs()}
  inst.uiAutomation.takeScreenshot().let {image->File(dir,"source-attributes.png").outputStream().use {image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};image.recycle()}
 }
}
