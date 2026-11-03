package io.github.nevaseme.plandmk1.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
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

@Composable fun CommunityScreen(model:PlanViewModel,groupName:String,onClose:()->Unit){
 val context=LocalContext.current;val scope=rememberCoroutineScope();val en=LocalUiLanguage.current=="en";fun label(k:String)=communityLabel(k,en)
 val group by model.group.collectAsStateWithLifecycle();val state by model.incidentUi.collectAsStateWithLifecycle();val residents by model.residents.collectAsStateWithLifecycle();val care by model.careEvents.collectAsStateWithLifecycle();val allHeads by model.responseEvents.collectAsStateWithLifecycle();val venue by model.activeVenue.collectAsStateWithLifecycle();val roster by model.roster.collectAsStateWithLifecycle()
 val incident=state.current;val incidentId=incident?.start?.eventId;val heads=allHeads.filter {it.groupId==group&&it.incidentId==incidentId};val changes=heads.map {it.change}
 val editable=incident!=null&&!incident.closed&&roster?.members?.any {model.localId in it.deviceIds}==true
 val scenario=venue?.scenario;val communities=scenario?.communities?.map {CommunityName(it.id,it.nameJa,it.nameEn)}?.let{if(venue?.positionMode=="ACTUAL")it+CommunityName("venue","会場参加者","Venue participants")else it}?:listOf(CommunityName("group",groupName,groupName))
 val people=residents.map {p->val placement=venue?.allPlacements()?.find {it.deviceId==p.originId};CommunityPerson(p.originId,p.name,if(scenario!=null){if(venue?.positionMode=="ACTUAL"&&scenario.people.none{it.deviceId==p.originId})"venue" else demoCommunityFor(placement?.home?.longitude?:p.longitude)}else "group",residentMapState(p,care).name,scenario?.people?.any {it.deviceId==p.originId}==true,placement?.home?.buildingId)}
 val rows=communityOverview(people,changes,communities)
 val codec=remember{Json{encodeDefaults=true;ignoreUnknownKeys=true}}
 val directory=remember(group,incidentId){File(context.filesDir,"community-reports/$group/${incidentId?:"none"}")}
 var reports by remember(group,incidentId){mutableStateOf<List<CommunityReport>>(emptyList())};var error by remember{mutableStateOf("")};var issuing by remember{mutableStateOf(false)};var injuryOpen by remember{mutableStateOf(false)};var reportsOpen by remember{mutableStateOf(false)}
 var exportData by remember{mutableStateOf("")}
 val export=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/html")){uri->if(uri!=null){val text=exportData;scope.launch{runCatching{withContext(Dispatchers.IO){context.contentResolver.openOutputStream(uri)?.use{it.write(text.toByteArray())}?:error("Cannot open destination")}}.onFailure{error=it.message.orEmpty()}}}}
 val exportJson=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")){uri->if(uri!=null){val text=exportData;scope.launch{runCatching{withContext(Dispatchers.IO){context.contentResolver.openOutputStream(uri)?.use{it.write(text.toByteArray())}?:error("Cannot open destination")}}.onFailure{error=it.message.orEmpty()}}}}
 LaunchedEffect(directory){reports=withContext(Dispatchers.IO){directory.listFiles()?.filter{it.extension=="json"}?.mapNotNull{runCatching{codec.decodeFromString<CommunityReport>(it.readText())}.getOrNull()}?.sortedByDescending{it.issuedAt}.orEmpty()}}
 ApprovedSheet(label("title"),onClose){
  Text(groupName,fontWeight=FontWeight.Bold,fontSize=18.sp)
  rows.forEach {r->OutlinedCard(Modifier.fillMaxWidth()){Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
   Text(if(en)r.nameEn else r.nameJa,fontWeight=FontWeight.Bold,fontSize=17.sp)
   communityCounts(r).chunked(4).forEach {pair->Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)){pair.forEach{(key,value)->Column(Modifier.weight(1f)){Text(label(key),fontSize=11.sp);Text(value.toString(),fontWeight=FontWeight.Bold,fontSize=19.sp)}}}}
   HorizontalDivider();Text(label("materials"),fontWeight=FontWeight.Bold);if(r.materials.isEmpty())Text(label("none"),fontSize=14.sp)
   r.materials.forEach{m->Text("${m.name}: ${m.shortfall} ${m.unit}");if(m.conflict)Text(label("conflict"),fontSize=12.sp)}
  }}}
  TextButton(onClick={injuryOpen=!injuryOpen},modifier=Modifier.fillMaxWidth()){Text(label("injury"),Modifier.weight(1f));Text(if(injuryOpen)"−" else "+")}
  if(injuryOpen)people.forEach{p->var open by remember(p.id){mutableStateOf(false)};Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){Column(Modifier.weight(1f)){Text(p.name)};Box{OutlinedButton(onClick={open=true},enabled=editable){Text(label(communityInjury(changes,p.id).name))};DropdownMenu(open,{open=false}){InjuryState.entries.forEach{v->DropdownMenuItem(text={Text(label(v.name))},onClick={open=false;model.saveResponse(ResponseChange.Injury(ResponseTarget(TargetKind.PERSON,p.id),v),heads.filter{(it.change as? ResponseChange.Injury)?.target?.id==p.id}.map{it.eventId})})}}}}}
  Button(enabled=incidentId!=null&&!issuing,onClick={val currentId=incidentId?:return@Button;issuing=true;val report=CommunityReport(id=UUID.randomUUID().toString(),issuedAt=System.currentTimeMillis(),language=if(en)"en" else "ja",groupId=group,groupName=groupName,incidentId=currentId,demo=venue!=null,rows=rows,people=people,sourceIds=(heads.map{it.eventId}+incident.answers.values.map{it.eventId}+care.map{it.eventId}).distinct().sorted());scope.launch{runCatching{withContext(Dispatchers.IO){check(directory.isDirectory||directory.mkdirs());val target=File(directory,report.id+".json");val temporary=File(directory,report.id+".tmp");temporary.writeText(codec.encodeToString(report));check(temporary.renameTo(target))};reports=listOf(report)+reports;reportsOpen=true}.onFailure{error=it.message.orEmpty()};issuing=false}},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){Text(label("issue"))}
  if(error.isNotBlank())Text(error,color=MaterialTheme.colorScheme.error)
  TextButton(onClick={reportsOpen=!reportsOpen},modifier=Modifier.fillMaxWidth()){Text("${label("saved")} (${reports.size})",Modifier.weight(1f));Text(if(reportsOpen)"−" else "+")}
  if(reportsOpen)reports.forEach{r->Text(java.text.DateFormat.getDateTimeInstance().format(java.util.Date(r.issuedAt)));Row{TextButton(onClick={exportData=communityReportHtml(r);export.launch("Tasukeai-report-${r.id}.html")}){Text("HTML")};TextButton(onClick={exportData=codec.encodeToString(r);exportJson.launch("Tasukeai-report-${r.id}.json")}){Text("JSON")}}}
 }
}
