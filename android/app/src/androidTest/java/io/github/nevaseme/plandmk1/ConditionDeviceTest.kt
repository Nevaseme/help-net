package io.github.nevaseme.plandmk1

import android.app.Notification
import android.app.NotificationManager
import android.graphics.Bitmap
import android.os.Build
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import io.github.nevaseme.plandmk1.core.*
import io.github.nevaseme.plandmk1.eew.*
import io.github.nevaseme.plandmk1.ui.*
import io.github.nevaseme.plandmk1.ui.theme.PlanDMk1Theme
import org.junit.Assert.*
import org.junit.*
import java.io.File

class ConditionDeviceTest {
 @get:Rule val compose=createEmptyComposeRule()
 private fun screenshot(name:String){val i=InstrumentationRegistry.getInstrumentation();val f=File(i.targetContext.filesDir,"condition-review/$name.png");f.parentFile!!.mkdirs();i.uiAutomation.takeScreenshot().let {b->f.outputStream().use {b.compress(Bitmap.CompressFormat.PNG,100,it)};b.recycle()}}
 @Test fun notificationVariantsUseEnglishInAndroid() {
  assertTrue(Build.FINGERPRINT.contains("generic")||Build.MODEL.contains("sdk"))
  val app=ApplicationProvider.getApplicationContext<PlanDApplication>();val prefs=app.getSharedPreferences("interface-language",0);val previous=prefs.getString("language",null);prefs.edit().putString("language","en").commit()
  val manager=app.getSystemService(NotificationManager::class.java)
  val cases=listOf(
   "緊急地震速報（警報）" to "京都府南部：予測震度5弱〜6弱（P2P地震情報・第1報）",
   "緊急地震速報 更新" to "京都府南部は警報の対象から外れました。（P2P地震情報・第2報）",
   "緊急地震速報 取消" to "京都府南部への速報に取消が届きました。（P2P地震情報・第3報）",
   "Help Net 受信試験" to "消灯中の通知と端末内保存を確認する試験です。実際の地震ではありません。",
   "緊急地震速報（警報）" to "京都府南部：予測震度不明（Wolfx・第1報）"
  )
  try {cases.forEachIndexed {index,(title,body)->
   val tag="condition-review-$index";EewNotifications(app).show(EewNotice(tag,title,body,"review"))
   compose.waitUntil(10000){manager.activeNotifications.any {it.tag==tag}}
   val notification=manager.activeNotifications.single {it.tag==tag}.notification
   val visible=notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString()+notification.extras.getCharSequence(Notification.EXTRA_TEXT)
   assertFalse(visible,Regex("[\\u3040-\\u9fff]").containsMatchIn(visible))
   assertFalse(Regex("demo|fiction",RegexOption.IGNORE_CASE).containsMatchIn(visible))
  }
  val more=listOf("位置記録","Help Net 位置記録中","位置を取得しています","位置を保存しました","位置を取得できませんでした。再試行できます","最後の保存位置を保持しています","位置記録を停止","安否情報の共有","Help Net・共有中","共有を開始しています…","接続 2 台・同じグループの端末と自動共有します","接続 2 台・接続済みの相手とは画面を消しても共有します")
  more.forEach {assertFalse(it,Regex("[\\u3040-\\u9fff]").containsMatchIn(UiMessages.text(app,it)))}
  } finally {cases.indices.forEach {manager.cancel("condition-review-$it",EewNotifications.ID)};prefs.edit().apply {if(previous==null)remove("language")else putString("language",previous)}.commit()}
 }
 @Test fun pdfPickerWritesActualDocument() {
  assertTrue(Build.FINGERPRINT.contains("generic")||Build.MODEL.contains("sdk"))
  val report=CommunityReport(id="review",issuedAt=System.currentTimeMillis(),language="en",groupId="review",groupName="Help Net Review",incidentId="review",demo=true,rows=listOf(CommunityRow("west","御所南西町内会","Gosho South West",total=7,safe=4,sos=1,unreported=2)),people=emptyList(),sourceIds=listOf("hidden-source-id"))
  shellComposeContent {CompositionLocalProvider(LocalUiLanguage provides "en") {PlanDMk1Theme {androidx.compose.material3.Surface {CommunityReportPreview(report){}}}}}
  compose.onNodeWithText("Save PDF").performClick()
  val automation=InstrumentationRegistry.getInstrumentation().uiAutomation
  fun findSave(node:android.view.accessibility.AccessibilityNodeInfo?):android.view.accessibility.AccessibilityNodeInfo? {
   if(node==null)return null
   if(node.packageName=="com.google.android.documentsui"&&node.text?.toString()?.uppercase()=="SAVE"&&node.isEnabled)return node
   return (0 until node.childCount).firstNotNullOfOrNull {findSave(node.getChild(it))}
  }
  var save:android.view.accessibility.AccessibilityNodeInfo?=null
  compose.waitUntil(15000){save=findSave(automation.rootInActiveWindow);save!=null}
  screenshot("pdf-picker")
  assertTrue(save!!.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK))
  compose.waitUntil(20000){runCatching {compose.onAllNodesWithText("PDF saved").fetchSemanticsNodes().isNotEmpty()}.getOrDefault(false)}
  compose.onNodeWithText("PDF saved").assertIsDisplayed();screenshot("pdf-saved")
 }

 @Test fun administratorStartsEarthquakeFromScreen()=kotlinx.coroutines.runBlocking {
  assertTrue(Build.FINGERPRINT.contains("generic")||Build.MODEL.contains("sdk"))
  val app=ApplicationProvider.getApplicationContext<PlanDApplication>();val previous=app.installation.group.value;val me=app.installation.localId
  val group="condition-admin-"+java.util.UUID.randomUUID().toString().take(8);val models=androidx.lifecycle.ViewModelStore();lateinit var model:PlanViewModel
  app.store.updateOwn(group,me){Resident(group,me,1,0,"Review administrator",35.0119,135.7582,"2","")}
  app.quakes.importRoster(MemberRoster(groupId=group,members=listOf(GroupMember(me,"Review administrator",MemberRole.ADMIN,listOf(me)))).encode(),me)
  try {
   compose.runOnUiThread {app.installation.setGroup(group);model=PlanViewModel(app);models.put("admin",model)}
   compose.waitUntil(10000){model.roster.value!=null}
   shellComposeContent {CompositionLocalProvider(LocalUiLanguage provides "en") {PlanDMk1Theme {androidx.compose.material3.Surface {IncidentActions(model,true,administrator=true)}}}}
   compose.onNodeWithText("Start earthquake").performClick()
   compose.onNode(hasText("Start earthquake") and hasClickAction() and hasAnyAncestor(isDialog())).performClick()
   compose.waitUntil(15000){model.incidentUi.value.current?.start?.training==false}
   assertFalse(model.incidentUi.value.current!!.start.training)
   screenshot("administrator-earthquake")
  }finally {compose.runOnUiThread {models.clear();app.installation.setGroup(previous)}}
 }
}
