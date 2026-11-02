package io.github.nevaseme.plandmk1

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import io.github.nevaseme.plandmk1.core.*
import io.github.nevaseme.plandmk1.ui.*
import io.github.nevaseme.plandmk1.ui.theme.PlanDMk1Theme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.UUID
import java.io.File
import android.graphics.Bitmap
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.view.MotionEvent
import android.os.SystemClock
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import org.maplibre.android.maps.MapView

class ApprovedScreensTest {
    @get:Rule val compose=createComposeRule()
    private fun capture(name:String) {
        compose.waitForIdle()
        val app=ApplicationProvider.getApplicationContext<PlanDApplication>()
        val folder=File(app.filesDir,"approved-screens").apply {mkdirs()}
        InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().let {bitmap->File(folder,"$name.png").outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()}
    }
    @Test fun largeTextKeepsSafetyAndNavigationUsable() {
        compose.setContent {
            val density=androidx.compose.ui.platform.LocalDensity.current
            CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(density.density,2f)) {
                PlanDMk1Theme {PlanLocalization {
                    Column(Modifier.fillMaxSize()) {
                        ApprovedHeader("地震発生","11月1日 14:32",onNavigate={})
                        Box(Modifier.weight(1f)) {AnswerPanel(true,null,"",false,save={_,_->})}
                        ApprovedTabs(0){}
                    }
                }}
            }
        }
        compose.onNodeWithText("SOS",substring=false).performScrollTo().performClick()
        compose.onNodeWithContentDescription("SOSメモ").performScrollTo().performTextInput("文字拡大の確認")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        compose.onNodeWithText("安否を登録して地図へ",substring=false).performScrollTo().assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithText("安否",substring=false).assertIsDisplayed().assertIsSelected()
        compose.onNodeWithText("地図",substring=false).assertIsDisplayed()
        capture("large-text-safety")
    }

    private fun tapVisibleMapCenter() {
        fun maps(view:View):List<MapView> = when(view){is MapView->listOf(view);is ViewGroup->(0 until view.childCount).flatMap {maps(view.getChildAt(it))};else->emptyList()}
        val inst=InstrumentationRegistry.getInstrumentation();val bounds=Rect()
        inst.runOnMainSync {
            val root=ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).first().window.decorView
            assertTrue(maps(root).any {it.getGlobalVisibleRect(bounds)&&bounds.width()>100&&bounds.height()>100})
        }
        val time=SystemClock.uptimeMillis()
        listOf(MotionEvent.ACTION_DOWN,MotionEvent.ACTION_UP).forEach {action->
            val event=MotionEvent.obtain(time,SystemClock.uptimeMillis(),action,bounds.exactCenterX(),bounds.exactCenterY(),0)
            try {assertTrue(inst.uiAutomation.injectInputEvent(event,true))} finally {event.recycle()}
        }
    }

    @Test fun detailOverlayKeepsNavigationLiveAndLanguageChangePersists() {
        val app=ApplicationProvider.getApplicationContext<PlanDApplication>()
        val prefs=app.getSharedPreferences("interface-language",0)
        val before=prefs.getString("language",null);prefs.edit().putString("language","ja").commit()
        var tab by mutableIntStateOf(10)
        var settings by mutableStateOf(false)
        try {
            compose.setContent {PlanDMk1Theme {PlanLocalization {
                Scaffold(bottomBar={ApprovedTabs(tab){tab=it;settings=false}}){padding->Box(Modifier.fillMaxSize().padding(padding)){
                    if(tab==10)ApprovedSheet("通報の詳細",{}){Text("試験の通報")}
                    else if(settings)LanguageSettings {settings=false}
                    else Button(onClick={settings=true}){Text(uiText("settings"))}
                }}
            }}}
            compose.onNodeWithText("一覧",substring=false).performClick()
            compose.onNodeWithText("通報の詳細").assertDoesNotExist()
            compose.onNodeWithText("設定",substring=false).performClick()
            compose.onNodeWithText("English",substring=false).performClick()
            compose.onNodeWithText("Resident list",substring=false).assertIsDisplayed()
            assertEquals("en",prefs.getString("language",null))
            compose.onNodeWithText("Map",substring=false).performClick()
            compose.onNodeWithText("English",substring=false).assertDoesNotExist()
            compose.onNodeWithText("Settings",substring=false).assertIsDisplayed()
        } finally {if(before==null)prefs.edit().remove("language").commit()else prefs.edit().putString("language",before).commit()}
    }

    @Test fun responseFormPersistsReportAndStartButtonAssignsCurrentMember()=runBlocking {
        val app=ApplicationProvider.getApplicationContext<PlanDApplication>()
        val japanese=app.createConfigurationContext(android.content.res.Configuration(app.resources.configuration).apply {setLocale(java.util.Locale.JAPANESE)}).resources
        assertEquals("対応中",japanese.getString(R.string.approved_in_progress))
        if(android.os.Build.VERSION.SDK_INT>=30){
            app.getSystemService(android.app.ActivityManager::class.java).getHistoricalProcessExitReasons(app.packageName,0,5)
                .firstOrNull {it.reason==android.app.ApplicationExitInfo.REASON_ANR}?.traceInputStream?.use {input->File(app.filesDir,"approved-startup-anr.txt").outputStream().use {input.copyTo(it)}}
        }
        val previous=app.installation.group.value;val group="ui-${UUID.randomUUID().toString().take(8)}";val me=app.installation.localId
        val roster=MemberRoster(groupId=group,members=listOf(GroupMember(me,"試験担当者",MemberRole.ADMIN,listOf(me))))
        app.quakes.importRoster(roster.encode(),me)
        app.store.updateOwn(group,me){Resident(group,me,1,0,"試験担当者",35.0,135.0,"2階","試験室")}
        val incident=UUID.randomUUID().toString()
        app.quakes.command(QuakeEvent(group,incident,me,System.currentTimeMillis(),QuakeAction.TRAINING_START,true,title="画面確認用の訓練",area="架空の試験地区"))
        val resident=UUID.randomUUID().toString()
        app.store.updateOwn(group,resident){Resident(group,resident,1,0,"架空住民A",35.0,135.0,"2階","東側の寝室")}
        app.store.answerWithMemo(IncidentEvent(group,UUID.randomUUID().toString(),incident,resident,"架空住民A",1,System.currentTimeMillis(),IncidentKind.ANSWER,safety=Safety.SOS),"玄関の扉が開きません。救助をお願いします。")
        val models=ViewModelStore();lateinit var model:PlanViewModel
        val prefs=app.getSharedPreferences("interface-language",0);val language=prefs.getString("language",null);prefs.edit().putString("language","ja").commit()
        try {
            compose.runOnUiThread {org.maplibre.android.MapLibre.getInstance(app);app.installation.setGroup(group);app.installation.selectIncident(group,incident);model=PlanViewModel(app);models.put("response",model)}
            compose.waitUntil(15_000){model.incidentUi.value.current?.start?.eventId==incident&&model.roster.value?.groupId==group}
            compose.setContent {PlanDMk1Theme {PlanApp(model)}}
            compose.onNodeWithText("対応",substring=false).performClick()
            compose.onNodeWithText("通報を追加",substring=true).performClick()
            compose.onNodeWithContentDescription("緯度",substring=false).performTextInput("35.0")
            compose.onNodeWithContentDescription("経度",substring=false).performTextInput("135.0")
            compose.onNodeWithContentDescription("状況・内容",substring=false).performTextInput("試験用の倒木")
            androidx.test.espresso.Espresso.closeSoftKeyboard()
            compose.onNodeWithText("保存する",substring=false).assertIsEnabled()
            capture("09-report-form-ja")
            compose.onNodeWithText("保存する",substring=false).assertIsEnabled().performClick()
            compose.waitUntil(15_000){!model.saving.value && runBlocking {app.responses.heads(group).any {it.change is ResponseChange.Report}}}
            capture("10-response-ja")
            compose.onNodeWithText("道路閉塞",substring=false).performClick()
            capture("11-report-detail-ja")
            compose.onNodeWithText("対応を始める",substring=false).performClick()
            compose.waitUntil(15_000){!model.saving.value && runBlocking {app.responses.heads(group).any {(it.change as? ResponseChange.Assignment)?.let {a->a.memberId==me&&a.active}==true}}}
            val saved=app.responses.heads(group)
            assertEquals(1,saved.count {it.change is ResponseChange.Report})
            assertEquals("試験用の倒木",(saved.single {it.change is ResponseChange.Report}.change as ResponseChange.Report).description)
            assertEquals(WorkStatus.IN_PROGRESS,(saved.single {it.change is ResponseChange.Progress}.change as ResponseChange.Progress).status)
            compose.onNodeWithText("担当を解除",substring=false).performClick()
            compose.waitUntil(15_000){!model.saving.value && runBlocking {app.responses.heads(group).none {(it.change as? ResponseChange.Assignment)?.let {a->a.memberId==me&&a.active}==true}}}
            assertEquals(WorkStatus.IN_PROGRESS,(app.responses.heads(group).single {it.change is ResponseChange.Progress}.change as ResponseChange.Progress).status)
            compose.onNodeWithText("対応を始める",substring=false).performClick()
            compose.waitUntil(15_000){!model.saving.value && runBlocking {app.responses.heads(group).any {(it.change as? ResponseChange.Assignment)?.let {a->a.memberId==me&&a.active}==true}}}
            // Exercise the approved operational forms against the real persistence path.
            compose.onAllNodesWithText("変更",substring=false)[1].performScrollTo().performClick()
            val priorProgress=app.responses.heads(group).single {it.change is ResponseChange.Progress}
            app.responses.append(priorProgress.copy(eventId=UUID.randomUUID().toString(),operatedAt=System.currentTimeMillis(),change=ResponseChange.Progress((priorProgress.change as ResponseChange.Progress).target,WorkStatus.PLANNED,"別の更新を受信"),replaces=listOf(priorProgress.eventId)))
            compose.waitUntil(15_000){compose.onAllNodesWithText("内容が更新されました。閉じて開き直してください。",substring=false).fetchSemanticsNodes().isNotEmpty()}
            compose.onNodeWithText("保存する",substring=false).assertIsNotEnabled()
            compose.onNodeWithContentDescription("閉じる").performClick()
            compose.onAllNodesWithText("変更",substring=false)[1].performScrollTo().performClick()
            compose.onNodeWithText("対応状況: 対応予定",substring=false).performClick()
            compose.onNodeWithText("対応済み",substring=false).performClick()
            compose.onNodeWithContentDescription("対応済みにするには結果を入力してください",substring=false).performTextReplacement("試験の撤去を完了")
            compose.onNodeWithText("保存する",substring=false).assertIsEnabled().performClick()
            compose.waitUntil(15_000){!model.saving.value && runBlocking {app.responses.heads(group).any {(it.change as? ResponseChange.Progress)?.let {p->p.status==WorkStatus.DONE&&p.result=="試験の撤去を完了"}==true}}}
            compose.onAllNodesWithText("変更",substring=false)[2].performScrollTo().performClick()
            compose.onNodeWithText("緊急度: 未設定",substring=false).performClick()
            compose.onNodeWithText("高",substring=false).performClick()
            compose.onNodeWithContentDescription("理由",substring=false).performTextInput("試験の緊急度変更")
            compose.onNodeWithText("保存する",substring=false).assertIsEnabled().performClick()
            compose.waitUntil(15_000){!model.saving.value && runBlocking {app.responses.heads(group).any {(it.change as? ResponseChange.Priority)?.let {p->p.urgency==Urgency.HIGH&&p.reason=="試験の緊急度変更"}==true}}}
            compose.onNodeWithText("資材を要請",substring=false).performScrollTo().performClick()
            compose.onNodeWithContentDescription("資材名",substring=false).performTextInput("試験用ロープ")
            compose.onNodeWithContentDescription("単位",substring=false).performTextInput("本")
            compose.onNodeWithContentDescription("必要数",substring=false).performScrollTo().performTextInput("4")
            compose.onNodeWithContentDescription("手配済み数",substring=false).performScrollTo().performTextReplacement("1")
            androidx.test.espresso.Espresso.closeSoftKeyboard()
            compose.onNodeWithText("不足 3.0 本",substring=false).assertExists()
            capture("12-material-form-ja")
            compose.onNodeWithText("保存する",substring=false).assertIsEnabled().performClick()
            compose.waitUntil(15_000){!model.saving.value && runBlocking {app.responses.heads(group).any {(it.change as? ResponseChange.Material)?.let {m->m.name=="試験用ロープ"&&m.unit=="本"&&m.needed==4.0&&m.arranged==1.0}==true}}}
            compose.onNodeWithText("試験用ロープ",substring=false).performScrollTo()
            compose.onNodeWithText("不足 3.0 本",substring=false).performScrollTo().assertIsDisplayed()
            capture("13-material-detail-ja")
            compose.onNodeWithText("通報を取り消す",substring=false).performScrollTo().performClick()
            compose.onNodeWithContentDescription("取り消しの理由",substring=false).performTextInput("試験の取り消し")
            compose.onNodeWithText("保存する",substring=false).assertIsEnabled().performClick()
            compose.waitUntil(15_000){!model.saving.value && runBlocking {app.responses.heads(group).any {(it.change as? ResponseChange.Report)?.let {r->r.cancelled&&r.cancelReason=="試験の取り消し"}==true}}}
            compose.onNodeWithText("取り消しを戻す",substring=false).performScrollTo().performClick()
            compose.onNodeWithText("保存する",substring=false).assertIsEnabled().performClick()
            compose.waitUntil(15_000){!model.saving.value && runBlocking {app.responses.heads(group).any {(it.change as? ResponseChange.Report)?.let {r->!r.cancelled&&r.cancelReason.isEmpty()}==true}}}
            compose.onNodeWithText("一覧",substring=false).performClick()
            compose.onNodeWithText("メンバー一覧",substring=false).assertIsDisplayed()
            capture("05-people-ja")
            compose.onNodeWithText("架空住民A",substring=false).performClick()
            capture("06-person-detail-ja")
            compose.onNodeWithText("安否",substring=false).performClick()
            capture("18-my-status-ja")
            compose.onNodeWithText("地図",substring=false).performClick()
            capture("01-map-ja")
            compose.onNodeWithText("通報を追加",substring=false).performClick()
            compose.onNodeWithText("通報する場所を地図でタップしてください",substring=false).assertIsDisplayed()
            tapVisibleMapCenter()
            compose.waitUntil(15_000){compose.onAllNodesWithText("緯度",substring=false).fetchSemanticsNodes().isNotEmpty()}
            val pickedLatitude=compose.onNodeWithContentDescription("緯度",substring=false).fetchSemanticsNode().config[SemanticsProperties.EditableText].text.toDouble()
            val pickedLongitude=compose.onNodeWithContentDescription("経度",substring=false).fetchSemanticsNode().config[SemanticsProperties.EditableText].text.toDouble()
            assertTrue(pickedLatitude in -85.0..85.0&&pickedLongitude in -180.0..180.0)
            capture("02-map-report-point-ja")
            compose.onNodeWithContentDescription("閉じる").performClick()
            assertEquals(1,app.responses.heads(group).count {it.change is ResponseChange.Report})
            compose.onNodeWithContentDescription("メニューを開く").performClick()
            compose.onNodeWithText("設定",substring=false).performClick()
            capture("03-settings-ja")
            compose.onNodeWithText("English",substring=false).performClick()
            capture("04-settings-en")
            compose.onNodeWithText("Response",substring=false).performClick()
            capture("19-response-en")
        } catch(failure:Throwable) {
            runCatching {capture("failure");File(app.filesDir,"approved-screen-failure.txt").writeText(compose.onRoot(useUnmergedTree=true).printToString())}
            throw failure
        } finally {compose.runOnUiThread{models.clear();app.installation.setGroup(previous)};if(language==null)prefs.edit().remove("language").commit()else prefs.edit().putString("language",language).commit()}
    }
}
