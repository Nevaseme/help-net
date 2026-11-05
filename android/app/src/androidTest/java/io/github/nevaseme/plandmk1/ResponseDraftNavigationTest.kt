package io.github.nevaseme.plandmk1

import android.os.Build
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import io.github.nevaseme.plandmk1.core.*
import io.github.nevaseme.plandmk1.ui.PlanApp
import io.github.nevaseme.plandmk1.ui.theme.PlanDMk1Theme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.UUID

/** Real app navigation with an isolated local group. Never changes a physical device. */
class ResponseDraftNavigationTest {
 @get:Rule val compose=createComposeRule()
 @Test fun responseDraftSurvivesTabAndCloseWithoutBecomingASavedRecord()=runBlocking {
  assertTrue(Build.FINGERPRINT.contains("generic")||Build.MODEL.contains("sdk"))
  val app=ApplicationProvider.getApplicationContext<PlanDApplication>()
  val previous=app.installation.group.value;val group="draft-${UUID.randomUUID().toString().take(8)}";val id=app.installation.localId
  val incident=UUID.randomUUID().toString();val report=UUID.randomUUID().toString();val models=ViewModelStore()
  app.store.updateOwn(group,id){Resident(group,id,1,0,"下書き確認（架空）",35.01,135.76,"1","")}
  app.quakes.importRoster(MemberRoster(groupId=group,members=listOf(GroupMember(id,"確認担当",MemberRole.ADMIN,listOf(id)))).encode(),id)
  app.quakes.command(QuakeEvent(group,incident,id,System.currentTimeMillis(),QuakeAction.TRAINING_START,true,title="下書き確認",area="架空地区"))
  app.responses.append(ResponseEvent(group,report,id,1,System.currentTimeMillis(),incident,ResponseChange.Report(report,"建物倒壊",35.01,135.76,"下書き確認の通報"),emptyList()))
  lateinit var model:PlanViewModel
  try {
   compose.runOnUiThread {org.maplibre.android.MapLibre.getInstance(app);app.installation.setGroup(group);model=PlanViewModel(app);models.put("draft",model)}
   compose.waitUntil(15000){model.responseEvents.value.any{it.eventId==report}&&model.incidentUi.value.current!=null}
   compose.setContent{PlanDMk1Theme{PlanApp(model)}}
   compose.onNodeWithText("対応",useUnmergedTree=true).performClick()
   compose.onNodeWithText("下書き確認の通報").performClick();println(compose.onRoot().printToString())
   compose.onNodeWithContentDescription("対応状況 変更").performClick()
   compose.onNodeWithContentDescription("対応結果").performTextReplacement("消えない下書き")
   androidx.test.espresso.Espresso.closeSoftKeyboard()
   compose.onNodeWithText("一覧",useUnmergedTree=true).performClick()
   compose.onNodeWithText("対応",useUnmergedTree=true).performClick()
   compose.onNodeWithContentDescription("対応結果").assert(hasText("消えない下書き"))
   compose.onNodeWithContentDescription("閉じる").performClick()
   compose.onNodeWithContentDescription("対応状況 変更").performClick()
   compose.onNodeWithContentDescription("対応結果").assert(hasText("消えない下書き"))
   assertFalse(model.responseEvents.value.any{it.change is ResponseChange.Progress})
  } finally {compose.runOnUiThread{models.clear();app.installation.setGroup(previous)}}
 }
}


