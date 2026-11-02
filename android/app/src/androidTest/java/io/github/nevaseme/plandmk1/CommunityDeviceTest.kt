package io.github.nevaseme.plandmk1
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import io.github.nevaseme.plandmk1.cloud.*
import io.github.nevaseme.plandmk1.core.*
import io.github.nevaseme.plandmk1.ui.*
import io.github.nevaseme.plandmk1.ui.theme.PlanDMk1Theme
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID

/** Offline fixture and separate group; existing memberships, homes and settings are restored. */
class CommunityDeviceTest {
 @get:Rule val compose=createComposeRule()
 @Test fun scenarioOverviewInjuryReportRestartAndLanguage(){
  val inst=InstrumentationRegistry.getInstrumentation();val app=inst.targetContext.applicationContext as PlanDApplication;val old=app.installation.group.value;val codec=Json{ignoreUnknownKeys=true};val vmStore=ViewModelStore();var language by mutableStateOf("ja")
  val raw=inst.context.assets.open("community-fixture.json").bufferedReader().use{it.readText()};val source=codec.parseToJsonElement(raw).jsonObject;val original=source.getValue("envelope").jsonObject;val originalDevice=original.getValue("roster").jsonObject.getValue("members").jsonArray.first().jsonObject.getValue("deviceIds").jsonArray.first().jsonPrimitive.content
  val group=UUID.randomUUID().toString();val payload=codec.parseToJsonElement(raw.replace(originalDevice,app.installation.localId).replace(original.getValue("groupId").jsonPrimitive.content,group)).jsonObject
  val envelope=codec.decodeFromJsonElement<CloudRosterEnvelope>(payload.getValue("envelope"));val venue=checkNotNull(envelope.venue)
  lateinit var model:PlanViewModel
  try {
   runBlocking{val journal=RoomCloudJournal(app.database,group,app.installation.localId);journal.updateRoster(envelope,app.installation.localId);journal.mergePage(codec.decodeFromJsonElement<List<CloudRecord>>(payload.getValue("records")))}
   CloudSettings(app).saveVenue(envelope)
   inst.runOnMainSync{org.maplibre.android.MapLibre.getInstance(app);app.installation.setGroup(group);app.installation.selectIncident(group,venue.incidentId);model=PlanViewModel(app);vmStore.put("community-test",model)}
   compose.waitUntil(20000){model.residents.value.size==13&&model.responseEvents.value.size>=12}
   assertEquals(2,model.residents.value.count{residentMapState(it,model.careEvents.value)==MapSafety.RESCUED});assertEquals(12,model.activeVenue.value?.scenario?.people?.size)
   compose.setContent{CompositionLocalProvider(LocalUiLanguage provides language){PlanDMk1Theme{CommunityScreen(model,"Fictional device verification") {}}}}
   compose.onNodeWithText("町内会の状況").assertExists();compose.onNodeWithText("レポートを発行").performScrollTo().performClick()
   val directory=File(app.filesDir,"community-reports/$group/${venue.incidentId}");compose.waitUntil(15000){directory.listFiles()?.any{it.extension=="json"}==true}
   val file=directory.listFiles()!!.single{it.extension=="json"};val frozen=file.readText();val report=codec.decodeFromString<CommunityReport>(frozen)
   assertEquals(13,report.rows.sumOf{it.total});assertEquals(2,report.rows.sumOf{it.injured});assertEquals(2,report.rows.sumOf{it.rescued});assertEquals(3,report.rows.sumOf{it.reports});assertTrue(report.people.all{it.buildingId!=null})
   inst.runOnMainSync{model.saveResponse(ResponseChange.Injury(ResponseTarget(TargetKind.PERSON,app.installation.localId),InjuryState.INJURED))}
   compose.waitUntil(15000){model.responseEvents.value.any{(it.change as? ResponseChange.Injury)?.let{c->c.target.id==app.installation.localId&&c.state==InjuryState.INJURED}==true}}
   assertEquals(frozen,file.readText());inst.runOnMainSync{language="en"};compose.onNodeWithText("Neighborhood overview").assertExists();compose.onNodeWithText("Issue report").performScrollTo().assertExists()
   val screenshot=inst.uiAutomation.takeScreenshot();File(app.getExternalFilesDir(null),"community-overview-en.png").outputStream().use{screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};screenshot.recycle()
   assertTrue(communityReportHtml(report).contains("Drinking water"))
  }finally{inst.runOnMainSync{vmStore.clear();app.installation.setGroup(old)}}
 }
}
