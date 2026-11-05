package io.github.nevaseme.plandmk1.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nevaseme.plandmk1.PlanViewModel
import io.github.nevaseme.plandmk1.core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

@Composable fun CommunityScreen(model:PlanViewModel,groupName:String,onCase:((ResponseTarget)->Unit)?=null,onClose:()->Unit){
 val context=LocalContext.current;val scope=rememberCoroutineScope();val en=LocalUiLanguage.current=="en";fun label(k:String)=communityLabel(k,en)
 val group by model.group.collectAsStateWithLifecycle();val state by model.incidentUi.collectAsStateWithLifecycle();val residents by model.residents.collectAsStateWithLifecycle();val care by model.careEvents.collectAsStateWithLifecycle();val allHeads by model.responseEvents.collectAsStateWithLifecycle();val venue by model.activeVenue.collectAsStateWithLifecycle();val roster by model.roster.collectAsStateWithLifecycle()
 val incident=state.current;val incidentId=incident?.start?.eventId;val heads=allHeads.filter {it.groupId==group&&it.incidentId==incidentId};val changes=heads.map {locateResponseChange(it.change,communityNamesFor(venue,groupName))}
 val editable=incident!=null&&!incident.closed&&runCatching {roster?.canTriage(model.localId)==true}.getOrDefault(false)
 val communities=communityNamesFor(venue,groupName)
 val people=communityPeopleFor(residents,care,venue,roster)
 val ownRegion=people.find {it.id==model.localId}?.communityId
 val accessibleIds=people.filter {venue?.scenario==null||it.id==model.localId||it.communityId==ownRegion}.map {it.id}.toSet()
 val cases=responseCases(residents.filter {it.originId in accessibleIds},care,heads,incidentStartedAt=incident?.start?.createdAt)
 var selectedCases by remember {mutableStateOf<List<ResponseCase>?>(null)}
 val rows=communityOverview(people,changes,communities)
 val codec=remember{Json{encodeDefaults=true;ignoreUnknownKeys=true}}
 val directory=remember(group,incidentId){File(context.filesDir,"community-reports/$group/${incidentId?:"none"}")}
 var reports by remember(group,incidentId){mutableStateOf<List<CommunityReport>>(emptyList())};var error by remember{mutableStateOf("")};var issuing by remember{mutableStateOf(false)};var injuryOpen by remember{mutableStateOf(false)};var reportsOpen by remember{mutableStateOf(false)}
 var previewReport by remember {mutableStateOf<CommunityReport?>(null)}
 var exportData by remember{mutableStateOf("")}
 val export=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/html")){uri->if(uri!=null){val text=exportData;scope.launch{runCatching{withContext(Dispatchers.IO){context.contentResolver.openOutputStream(uri)?.use{it.write(text.toByteArray())}?:error("Cannot open destination")}}.onFailure{error=it.message.orEmpty()}}}}
 val exportJson=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")){uri->if(uri!=null){val text=exportData;scope.launch{runCatching{withContext(Dispatchers.IO){context.contentResolver.openOutputStream(uri)?.use{it.write(text.toByteArray())}?:error("Cannot open destination")}}.onFailure{error=it.message.orEmpty()}}}}
 LaunchedEffect(directory){reports=withContext(Dispatchers.IO){directory.listFiles()?.filter{it.extension=="json"}?.mapNotNull{runCatching{codec.decodeFromString<CommunityReport>(it.readText())}.getOrNull()}?.sortedByDescending{it.issuedAt}.orEmpty()}}
 ApprovedSheet(label("title"),onClose){
  Text(groupName,fontWeight=FontWeight.Bold,fontSize=18.sp)
  TextButton(onClick={injuryOpen=!injuryOpen},modifier=Modifier.fillMaxWidth()){Text(if(editable){if(en)"Record injuries" else "負傷情報を登録"}else{if(en)"Injury information" else "負傷情報"},Modifier.weight(1f));Text(if(injuryOpen)"−" else "+")}
  if(injuryOpen)Text(if(en)"Injury records describe an injury. SOS means help is needed, with or without an injury. Recording an injury does not change safety status." else "負傷はけがの記録、SOSはけがの有無にかかわらず助けが必要な状態です。負傷を登録しても安否は変わりません。")
  if(injuryOpen)people.filter {it.id in accessibleIds}.forEach{p->var open by remember(p.id){mutableStateOf(false)};Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){Column(Modifier.weight(1f)){Text(p.name)};Box{OutlinedButton(onClick={open=true},enabled=editable){Text(label(communityInjury(changes,p.id).name))};DropdownMenu(open,{open=false}){InjuryState.entries.forEach{v->DropdownMenuItem(text={Text(label(v.name))},onClick={open=false;model.saveResponse(ResponseChange.Injury(ResponseTarget(TargetKind.PERSON,p.id),v),heads.filter{(it.change as? ResponseChange.Injury)?.target?.id==p.id}.map{it.eventId})})}}}}}
  NeighborhoodStatistics(rows)
  Text(if(en)"Reports by neighborhood" else "地域別の通報集計",fontWeight=FontWeight.Bold,fontSize=18.sp)
  val categories=listOf(null,"SOS","ROAD","BUILDING","OTHER")
  fun categoryTitle(type:String?)=if(en)when(type){null->"Total";"SOS"->"SOS";"ROAD"->"Road blocked";"BUILDING"->"Building collapse";else->"Other"} else when(type){null->"合計";"SOS"->"SOS";"ROAD"->"道路閉塞";"BUILDING"->"建物倒壊";else->"その他"}
  Column(Modifier.horizontalScroll(rememberScrollState())) {
   Row(Modifier.width(if(en)552.dp else 320.dp),verticalAlignment=Alignment.CenterVertically) {
    Text(if(en)"Region" else "地域",Modifier.width(if(en)132.dp else 100.dp),fontSize=12.sp,fontWeight=FontWeight.Bold)
    categories.forEach {Text(categoryTitle(it),Modifier.width(if(en)84.dp else 44.dp).padding(4.dp),fontSize=12.sp,fontWeight=FontWeight.Bold)}
   }
   (listOf<CommunityRow?>(null)+rows).forEach {r->
    val regionName=r?.let {if(en)it.nameEn else it.nameJa}?:if(en)"All regions" else "全地域"
    val reports=changes.filterIsInstance<ResponseChange.Report>().filter {!it.cancelled&&(r==null||r.id in it.regionIds)}.distinctBy {it.reportId}
    val sos=people.filter {it.status=="SOS"&&(r==null||it.communityId==r.id)}
    HorizontalDivider()
    Row(Modifier.width(if(en)552.dp else 320.dp),verticalAlignment=Alignment.CenterVertically) {
     Text(regionName,Modifier.width(if(en)132.dp else 100.dp),fontSize=13.sp,fontWeight=FontWeight.Bold)
     categories.forEach {type->
      val reportIds=reports.filter {type==null||reportCategoryGroup(it.category)==type}.map {it.reportId}.toSet()
      val personIds=if(type==null||type=="SOS")sos.map {it.id}.toSet() else emptySet()
      val matching=cases.filter {c->if(c.target.kind==TargetKind.REPORT)c.target.id in reportIds else c.target.id in personIds}
      val count=reportIds.size+personIds.size
      TextButton(onClick={selectedCases=matching},enabled=matching.isNotEmpty(),modifier=Modifier.width(if(en)84.dp else 44.dp).heightIn(min=48.dp).semantics {contentDescription="$regionName ${categoryTitle(type)} $count"}){Text(count.toString(),fontWeight=FontWeight.Bold)}
     }
    }
   }
  }
  selectedCases?.let {selected->
   Text(if(en)"Accessible reports" else "閲覧できる案件",fontWeight=FontWeight.Bold)
   selected.forEach {c->TextButton(onClick={onCase?.invoke(c.target)},enabled=onCase!=null){Text(c.person?.let {personText(it)}?:c.title);Text(" ›")}}
   TextButton(onClick={selectedCases=null}){Text(if(en)"Close list" else "一覧を閉じる")}
  }
  rows.forEach {r->OutlinedCard(Modifier.fillMaxWidth()){Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
   Text(if(en)r.nameEn else r.nameJa,fontWeight=FontWeight.Bold,fontSize=17.sp)
   communityCounts(r).chunked(4).forEach {pair->Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)){pair.forEach{(key,value)->Column(Modifier.weight(1f)){Text(label(key),fontSize=11.sp);Text(value.toString(),fontWeight=FontWeight.Bold,fontSize=19.sp)}}}}
   HorizontalDivider();Text(label("materials"),fontWeight=FontWeight.Bold);if(r.materials.isEmpty())Text(label("none"),fontSize=14.sp)
   r.materials.forEach{m->Text("${m.name}: ${m.shortfall} ${m.unit}");if(m.conflict)Text(label("conflict"),fontSize=12.sp)}
  }}}
  Button(enabled=incidentId!=null&&!issuing,onClick={val currentId=incidentId?:return@Button;issuing=true;val report=CommunityReport(id=UUID.randomUUID().toString(),issuedAt=System.currentTimeMillis(),language=if(en)"en" else "ja",groupId=group,groupName=groupName,incidentId=currentId,demo=venue!=null,rows=rows,people=people.map {if(it.id in accessibleIds)it else it.copy(name="",buildingId=null)},sourceIds=(heads.map{it.eventId}+incident.answers.values.map{it.eventId}+care.map{it.eventId}).distinct().sorted());scope.launch{runCatching{withContext(Dispatchers.IO){check(directory.isDirectory||directory.mkdirs());val target=File(directory,report.id+".json");val temporary=File(directory,report.id+".tmp");temporary.writeText(codec.encodeToString(report));check(temporary.renameTo(target))};reports=listOf(report)+reports;reportsOpen=true;previewReport=report}.onFailure{error=it.message.orEmpty()};issuing=false}},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){Text(label("issue"))}
  if(error.isNotBlank())Text(error,color=MaterialTheme.colorScheme.error)
  TextButton(onClick={reportsOpen=!reportsOpen},modifier=Modifier.fillMaxWidth()){Text("${label("saved")} (${reports.size})",Modifier.weight(1f));Text(if(reportsOpen)"−" else "+")}
  if(reportsOpen)reports.forEach{r->Text(io.github.nevaseme.plandmk1.core.usDateTime(r.issuedAt));Row{TextButton(onClick={previewReport=r}){Text(if(en)"View report" else "レポートを見る")}}}
 }
 previewReport?.let {r->CommunityReportPreview(r){previewReport=null}}

}

@Composable internal fun CommunityReportPreview(report:CommunityReport,onClose:()->Unit){
 val en=report.language=="en"
 val context=LocalContext.current
 var pdfBytes by remember {mutableStateOf<ByteArray?>(null)}
 var pdfFeedback by remember {mutableStateOf("")}
 val savePdf=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")){uri->
  if(uri!=null)runCatching {context.contentResolver.openOutputStream(uri)?.use {it.write(checkNotNull(pdfBytes))}?:error("Unable to open file")}.onSuccess {pdfFeedback=if(en)"PDF saved" else "PDFを保存しました"}.onFailure {pdfFeedback=if(en)"Could not save PDF" else "PDFを保存できませんでした"}
 }
 ApprovedSheet(if(en)"Issued report" else "発行済みレポート",onClose){
  Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){
   Button(onClick={runCatching{communityReportPdf(report)}.onSuccess{pdfBytes=it;savePdf.launch("Help-Net-report.pdf")}.onFailure{pdfFeedback=if(en)"Could not create PDF" else "PDFを作成できませんでした"}}){Text(if(en)"Save PDF" else "PDFを保存")}
  }
  if(pdfFeedback.isNotEmpty())Text(pdfFeedback)
  Text(report.groupName,fontWeight=FontWeight.Bold,fontSize=20.sp)
  Text(io.github.nevaseme.plandmk1.core.usDateTime(report.issuedAt))
  report.rows.forEach {row->
   Text(if(en)row.nameEn else row.nameJa,fontWeight=FontWeight.Bold,fontSize=18.sp)
   communityCounts(row).forEach {(key,value)->Row(Modifier.fillMaxWidth()){Text(communityLabel(key,en),Modifier.weight(1f));Text(value.toString(),fontWeight=FontWeight.Bold)}}
   Text(communityLabel("materials",en),fontWeight=FontWeight.Bold)
   if(row.materials.isEmpty())Text(communityLabel("none",en))
   row.materials.forEach{Text("${it.name}: ${it.shortfall} ${it.unit}")}
   HorizontalDivider()
  }
 }
}
