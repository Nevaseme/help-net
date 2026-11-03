package io.github.nevaseme.plandmk1

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import io.github.nevaseme.plandmk1.core.*
import io.github.nevaseme.plandmk1.ui.*
import io.github.nevaseme.plandmk1.ui.theme.PlanDMk1Theme
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Pure presentation fixtures: does not change memberships, records, language or device settings. */
class RestoredBaselineScreensTest {
 @get:Rule val compose=createEmptyComposeRule()
 @org.junit.After fun closeFixture() {
  InstrumentationRegistry.getInstrumentation().runOnMainSync {
   androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED).toList().forEach {it.finish()}
  }
 }
 private fun japaneseContext()=InstrumentationRegistry.getInstrumentation().targetContext.let {it.createConfigurationContext(android.content.res.Configuration(it.resources.configuration).apply {setLocale(java.util.Locale.JAPANESE)})}
 private fun capture(name:String) {
  compose.waitForIdle()
  val inst=InstrumentationRegistry.getInstrumentation()
  inst.waitForIdleSync();Thread.sleep(250)
  val dir=File(inst.targetContext.filesDir,"restored-baseline").apply {mkdirs()}
  inst.uiAutomation.takeScreenshot().let {image->File(dir,"$name.png").outputStream().use {image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};image.recycle()}
 }
 @Test fun allNeighborhoodTableAndPersonFooterRetainMemoAndSections() {
  val now=System.currentTimeMillis()
  val home=MapHome("home","空町103-1",MapPoint(35.0,135.0),1986,"木造",2)
  val known=MapPerson("known","鈴木 悠真",MapSafety.UNANSWERED,MapPoint(35.0,135.0),now-120000,9,home,"1階・南側の部屋",impactAt=now-180000,enclosedAt=now-120000)
  val unknown=MapPerson("unknown","山田 太郎",MapSafety.SOS,home=home,bedroom="2階・寝室",memo="玄関の扉が開きません。救助をお願いします。")
  val neighborhoods=listOf(MapNeighborhood(CommunityRow("east","東町内会","East",sos=2,unreported=2,safe=9,rescued=2),null,1),MapNeighborhood(CommunityRow("central","中央町内会","Central",sos=1,unreported=1,safe=8,rescued=1),null,0),MapNeighborhood(CommunityRow("west","西町内会","West",sos=3,unreported=2,safe=9,rescued=2),null,2))
  shellComposeContent {CompositionLocalProvider(LocalUiLanguage provides "ja",androidx.compose.ui.platform.LocalContext provides japaneseContext()){PlanDMk1Theme {
   Scaffold(bottomBar={ApprovedTabs(8){}}){padding->Box(Modifier.fillMaxSize().padding(padding)) {SituationScreen(listOf(known,unknown),listOf(home),home.point,now,listOnly=true,neighborhoods=neighborhoods,ownNeighborhoodId="east",onPersonResponse={},history={Text("救助・確認の記録はありません")})}}
  }}}
  listOf("東町内会","中央町内会","西町内会").forEach {compose.onNodeWithText(it).assertIsDisplayed()}
  capture("people-statistics-ja")
  compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("鈴木 悠真"))
  compose.onNodeWithText("鈴木 悠真").performClick()
  capture("person-before-footer-check")
  compose.onNodeWithText("救助済みにする").assertIsDisplayed()
  compose.onNodeWithText("普段昼間いる場所").performScrollTo().assertIsDisplayed()
  capture("person-known-ja")
  compose.onNodeWithContentDescription("詳細を閉じる").performClick()
  compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("山田 太郎"))
  compose.onNodeWithText("山田 太郎").performClick()
  compose.onNodeWithText("玄関の扉が開きません。救助をお願いします。").performScrollTo().assertIsDisplayed()
  compose.onNodeWithText("救助済みにする").assertIsDisplayed()
  capture("person-unknown-sos-ja")
  compose.onNodeWithContentDescription("詳細を閉じる").performClick()
  compose.onNode(hasScrollToIndexAction()).performScrollToIndex(0)
  capture("people-statistics-ja")
 }
 @Test fun neighborhoodSummaryShowsOnlyAggregatesAndMaterialDetails() {
  val row=CommunityRow("central","中央町内会","Central neighborhood",sos=2,unreported=2,safe=9,rescued=2,materials=mutableListOf(CommunityMaterial("飲料水","本",8.0)))
  var details by mutableStateOf(false)
  shellComposeContent {CompositionLocalProvider(LocalUiLanguage provides "ja",androidx.compose.ui.platform.LocalContext provides japaneseContext()){PlanDMk1Theme {
   Scaffold(bottomBar={ApprovedTabs(1){}}){padding->Box(Modifier.fillMaxSize().padding(padding)){ApprovedSheet("中央町内会",{}){NeighborhoodDetail(MapNeighborhood(row,MapPoint(35.0,135.0),1),details,{},{details=true})}}}
  }}}
  compose.onNodeWithText("資材：不足あり").performScrollTo().assertIsDisplayed()
  compose.onNodeWithText("飲料水",substring=true).assertDoesNotExist()
  capture("neighborhood-summary-ja")
  compose.onNodeWithText("詳細を見る").performScrollTo().performClick()
  compose.onNodeWithText("飲料水 · 8.0 本").performScrollTo().assertIsDisplayed()
  capture("neighborhood-details-ja")
 }
}
