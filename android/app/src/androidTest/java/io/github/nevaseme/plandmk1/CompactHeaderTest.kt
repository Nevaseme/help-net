package io.github.nevaseme.plandmk1

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.nevaseme.plandmk1.ui.ApprovedHeader
import io.github.nevaseme.plandmk1.ui.uiText
import io.github.nevaseme.plandmk1.ui.theme.PlanDMk1Theme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class CompactHeaderTest {
 @get:Rule val compose=createComposeRule()
 @Test fun offlineIndicatorDoesNotMoveContentAtNormalOrLargeText() {
  val offline=mutableStateOf(false);val scale=mutableFloatStateOf(1f);val title=mutableStateOf("地震");var opened=false;var offlineLabel="";var detailsLabel=""
  compose.setContent {PlanDMk1Theme {val density=LocalDensity.current;offlineLabel=uiText("internet_unavailable");detailsLabel=uiText("incident_details_action")
   CompositionLocalProvider(LocalDensity provides Density(density.density,scale.floatValue)) {
    Surface {Column(Modifier.width(320.dp).statusBarsPadding()){ApprovedHeader(title.value,null,onNavigate={},onDetails={opened=true},noInternet=offline.value,compact=true);Text("地図の開始位置")}}
   }
  }}
  for(value in listOf(1f,2f)) {
   compose.runOnIdle {scale.floatValue=value;offline.value=false}
   val bounds=compose.onNodeWithText("地図の開始位置").fetchSemanticsNode().boundsInRoot
   compose.runOnIdle {offline.value=true}
   compose.onNodeWithContentDescription(offlineLabel).assertIsDisplayed()
   assertEquals(bounds,compose.onNodeWithText("地図の開始位置").fetchSemanticsNode().boundsInRoot)
   compose.runOnIdle {offline.value=false}
   compose.onNodeWithContentDescription(offlineLabel).assertDoesNotExist()
   assertEquals(bounds,compose.onNodeWithText("地図の開始位置").fetchSemanticsNode().boundsInRoot)
  }
  compose.onNodeWithText(detailsLabel).performClick();assertTrue(opened)
  compose.runOnIdle {title.value=""};compose.onNodeWithText("PlanD").assertDoesNotExist();compose.onNodeWithText("Tasukeai").assertDoesNotExist()
 }
}
