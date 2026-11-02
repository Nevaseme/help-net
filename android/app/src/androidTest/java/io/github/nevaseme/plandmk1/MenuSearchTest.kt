package io.github.nevaseme.plandmk1

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import io.github.nevaseme.plandmk1.buildings.CityAddresses
import io.github.nevaseme.plandmk1.ui.*
import io.github.nevaseme.plandmk1.ui.theme.PlanDMk1Theme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class MenuSearchTest {
 @get:Rule val compose=createComposeRule()
 @Test fun handleClosesByTapAndSwipeWithoutMovingBody() {
  var open by mutableStateOf(true)
  compose.setContent {PlanDMk1Theme {PlanLocalization {Box(Modifier.fillMaxSize()) {if(open)ApprovedSheet("試験パネル",{open=false}) {Text("本文のスクロールでは閉じません")}}}}}
  compose.onNodeWithContentDescription("パネルを閉じる").performClick();compose.runOnIdle {assertFalse(open);open=true}
  compose.onNodeWithContentDescription("パネルを閉じる").performTouchInput {swipe(center,center.copy(y=center.y+160f))}
  compose.runOnIdle {assertFalse(open)}
 }
 @Test fun bundledAddressesAndLandmarksSearchWithoutNetwork()=runBlocking {
  val repo=CityAddresses(ApplicationProvider.getApplicationContext())
  assertTrue(repo.search("26100","左京区",true).any {it.address.contains("左京区")})
  val tower=repo.searchAll("京都タワー").first()
  assertEquals("京都タワー",tower.address);assertEquals("poi",tower.precision)
  assertEquals(34.98755,tower.latitude,.001);assertEquals(135.75933,tower.longitude,.001)
  assertFalse(repo.search("26100","京都タワー",false).any {it.precision=="poi"})
  assertTrue(repo.search("26100","存在しない架空施設123456",true).isEmpty())
 }
}
