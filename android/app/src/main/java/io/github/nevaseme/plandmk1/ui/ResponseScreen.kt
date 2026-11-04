package io.github.nevaseme.plandmk1.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.saveable.Saver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nevaseme.plandmk1.MapPoint
import io.github.nevaseme.plandmk1.PlanViewModel
import io.github.nevaseme.plandmk1.R
import io.github.nevaseme.plandmk1.core.*
import kotlinx.coroutines.launch
import java.util.UUID

data class ResponseCase(val target:ResponseTarget,val title:String,val description:String,val report:ResponseChange.Report?=null,val cancelled:Boolean=false,val person:MapPerson?=null)

/** Keep concurrent report revisions visible in the detail; cancellation never silently wins a conflict. */
fun responseCases(residents:List<Resident>,care:List<CareEvent>,heads:List<ResponseEvent>,memos:Map<String,String> = emptyMap(),incidentStartedAt:Long?=null,now:Long=System.currentTimeMillis(),demoIds:Set<String> = emptySet(),roster:MemberRoster?=null):List<ResponseCase> {
    val recorded=heads.filter {it.change !is ResponseChange.Injury}.mapNotNull {it.change.target()?.takeIf {target->target.kind==TargetKind.PERSON}?.id}.toSet()
    val people=residents.filter {residentMapState(it,care,roster)==MapSafety.SOS||(residentMapState(it,care,roster)==MapSafety.UNANSWERED&&it.originId in recorded)}.map {
        val person=projectPersonPrivacy(MapPerson(it.originId,it.displayName(),residentMapState(it,care,roster),demo=it.originId in demoIds),incidentStartedAt,now)
        ResponseCase(ResponseTarget(TargetKind.PERSON,it.originId),person.publicName,if(person.state==MapSafety.SOS)memos[it.originId].orEmpty()else "",person=person)
    }
    val reports=heads.filter {it.change is ResponseChange.Report}.groupBy {(it.change as ResponseChange.Report).reportId}.map {(id,events)->
        val revisions=events.map {it.change as ResponseChange.Report}
        val report=revisions.firstOrNull {!it.cancelled}?:revisions.first()
        ResponseCase(ResponseTarget(TargetKind.REPORT,id),report.category,report.description,report,revisions.all {it.cancelled})
    }
    return people+reports
}
fun responseAssignments(events:List<ResponseEvent>,target:ResponseTarget)=events.mapNotNull {it.change as? ResponseChange.Assignment}.filter {it.target==target&&it.active}.map {it.memberId}.distinct()
fun workLabel(status:WorkStatus)=when(status){WorkStatus.UNASSIGNED->"not_started";WorkStatus.PLANNED->"acknowledged";WorkStatus.IN_PROGRESS->"in_progress";WorkStatus.DONE->"resolved"}
fun urgencyLabel(value:Urgency)=when(value){Urgency.UNSET->"urgency_unset";Urgency.LOW->"urgency_low";Urgency.MEDIUM->"urgency_medium";Urgency.HIGH->"urgency_high"}
fun activityLabel(value:ActivityState)=when(value){ActivityState.UNSET->"activity_unset";ActivityState.AVAILABLE->"available";ActivityState.WORKING->"busy_activity";ActivityState.UNAVAILABLE->"unavailable"}
@Composable private fun categoryLabel(value:String)=when(value){"道路閉塞","ROAD_BLOCKED"->uiText("road_blocked");"建物倒壊"->uiText("building_collapse");"BUILDING_DAMAGE"->if(LocalUiLanguage.current=="en")"Building damage" else "建物損傷";"その他"->uiText("other_report");"WATER_OUTAGE"->if(LocalUiLanguage.current=="en")"Water outage" else "断水";else->value}
@Composable private fun caseTitle(case:ResponseCase)=case.person?.let {personText(it)}?:categoryLabel(case.title)
fun responseUrgency(events:List<ResponseEvent>,target:ResponseTarget)=listOf(Urgency.HIGH,Urgency.MEDIUM,Urgency.LOW).firstOrNull {value->events.any {(it.change as? ResponseChange.Priority)?.let {p->p.target==target&&p.urgency==value}==true}}?:Urgency.UNSET
fun reportIcon(urgency:Urgency)=when(urgency){Urgency.HIGH->R.drawable.report_emergency;Urgency.MEDIUM,Urgency.LOW->R.drawable.report_normal;Urgency.UNSET->R.drawable.report_unrated}
fun responseResolved(events:List<ResponseEvent>,target:ResponseTarget):Boolean {val progress=events.mapNotNull {it.change as? ResponseChange.Progress}.filter {it.target==target};return progress.isNotEmpty()&&progress.all {it.status==WorkStatus.DONE}}

@Composable fun ResponseScreen(model:PlanViewModel,showSummary:Boolean,onCloseSummary:()->Unit,initialPoint:MapPoint?,onPointUsed:()->Unit,initialTarget:ResponseTarget?=null,onTargetUsed:()->Unit={},onOpenSummary:()->Unit={}) {
    val group by model.group.collectAsStateWithLifecycle()
    val cloudState by model.cloud.state.collectAsStateWithLifecycle()
    val groupLabel=cloudState.takeIf {it.groupId==group}?.groupName?.takeIf {it.isNotBlank()}?:uiText("group_label")
    val incident by model.incidentUi.collectAsStateWithLifecycle()
    val rosterValue by model.roster.collectAsStateWithLifecycle()
    val residents by model.residents.collectAsStateWithLifecycle()
    val care by model.careEvents.collectAsStateWithLifecycle()
    val venue by model.activeVenue.collectAsStateWithLifecycle()
    val places by model.places.collectAsStateWithLifecycle()
    val allHeads by model.responseEvents.collectAsStateWithLifecycle()
    val allHistory by model.responseHistory.collectAsStateWithLifecycle()
    val memos by model.memos.collectAsStateWithLifecycle()
    val busy by model.saving.collectAsStateWithLifecycle()
    val roster=rosterValue?.takeIf {it.groupId==group}
    val member=roster?.members?.find {model.localId in it.deviceIds}
    val coordinator=member?.role in listOf(MemberRole.LEADER,MemberRole.ADMIN)
    val incidentId=incident.current?.start?.eventId
    var now by remember {mutableLongStateOf(System.currentTimeMillis())}
    LaunchedEffect(incident.current?.start?.createdAt){while(true){now=System.currentTimeMillis();val until=incident.current?.start?.createdAt?.plus(180_000)?.minus(now)?.takeIf {it>0}?:60_000;kotlinx.coroutines.delay(minOf(60_000L,until))}}
    val heads=allHeads.filter {it.groupId==group&&it.incidentId==incidentId}
    val history=allHistory.filter {it.groupId==group&&it.incidentId==incidentId}
    val communityPeople=communityPeopleFor(residents,care,venue,roster)
    val ownCommunity=communityPeople.find {it.id==model.localId}?.communityId
    val accessibleResidents=residents.filter {p->venue?.scenario==null||p.originId==model.localId||communityPeople.any {it.id==p.originId&&it.communityId==ownCommunity}}
    val regions=communityNamesFor(venue,groupLabel)
    val english=LocalUiLanguage.current=="en"
    val cases=responseCases(accessibleResidents,care,heads,residents.associate {p->p.originId to (memos.firstOrNull {m->m.answerId==incident.current?.answers?.get(p.originId)?.eventId&&m.authorId==p.originId&&m.incidentId==incidentId}?.text?:venue?.scenario?.people?.find {it.deviceId==p.originId}?.note.orEmpty())},incident.current?.start?.createdAt,now,venue?.allPlacements()?.map {it.deviceId}?.toSet().orEmpty(),roster)
    var filter by rememberSaveable(group,incidentId){mutableStateOf("all")}
    var showCancelled by rememberSaveable(group,incidentId){mutableStateOf(false)}
    var selected by rememberSaveable(group,incidentId,stateSaver=Saver<ResponseTarget?,ArrayList<String>>(save={it?.let {arrayListOf(it.kind.name,it.id)}?:arrayListOf()},restore={if(it.isEmpty())null else ResponseTarget(TargetKind.valueOf(it[0]),it[1])})){mutableStateOf<ResponseTarget?>(null)}
    val drafts=rememberSaveableStateHolder()
    var editor by rememberSaveable(group,incidentId){mutableStateOf<String?>(null)}
    var materialId by rememberSaveable {mutableStateOf<String?>(null)}
    var point by remember {mutableStateOf<MapPoint?>(null)}
    LaunchedEffect(initialPoint){if(initialPoint!=null){point=initialPoint;selected=null;editor="report";onPointUsed()}}
    LaunchedEffect(initialTarget){if(initialTarget!=null){selected=initialTarget;editor=null;onTargetUsed()}}
    val scope=rememberCoroutineScope();val context=LocalContext.current;val language=LocalUiLanguage.current
    var csv by remember {mutableStateOf<String?>(null)}
    val exported=uiText("exported");val exportFailed=uiText("export_failed")
    val export=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")){uri->
        val payload=csv;csv=null
        if(uri!=null&&payload!=null)scope.launch {try {context.contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use {it.write(payload)}?:error(exportFailed);model.message.value=exported}catch(e:Exception){model.message.value=exportFailed}}
    }
    val editable=member!=null&&incident.canEdit&&!busy
    val shown=cases.filter {it.cancelled==showCancelled}.filter {case->val ids=responseAssignments(heads,case.target);when(filter){"mine"->member?.memberId in ids;"unassigned"->ids.isEmpty();else->true}}
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).padding(bottom=122.dp),contentPadding=PaddingValues(bottom=16.dp)) {
            item {
                Column(Modifier.padding(horizontal=16.dp,vertical=14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    val ownAvailability=allHeads.filter {it.groupId==group&&it.change is ResponseChange.Availability&&roster?.members?.find {m->it.actorDeviceId in m.deviceIds}?.memberId==member?.memberId}
                    if(ownAvailability.size>1)Text(uiText("conflicting_records"),color=MaterialTheme.colorScheme.error)
                    ChoiceField(ActivityState.entries.map {it to uiText(activityLabel(it))},(ownAvailability.firstOrNull()?.change as? ResponseChange.Availability)?.state?:ActivityState.UNSET,uiText("my_activity"),member!=null&&!busy) {value->model.saveResponse(ResponseChange.Availability(value),ownAvailability.map {it.eventId})}
                    TextButton(onClick=onOpenSummary,modifier=Modifier.align(Alignment.End)){Text(uiText("area_summary"))}
                    if(member==null)Text(uiText("response_requires_roster"))
                    else if(incidentId==null)Text(uiText("response_requires_incident"))
                }
            }
            item {Column(Modifier.fillMaxWidth().padding(horizontal=10.dp)) {
             listOf("unassigned" to "unassigned_filter","mine" to "mine_filter","all" to "all_filter").chunked(if(LocalDensity.current.fontScale>=1.5f)2 else 3).forEach { options ->
              Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {options.forEach {(id,label)->
                val count=cases.count {c->!c.cancelled&&when(id){"unassigned"->responseAssignments(heads,c.target).isEmpty();"mine"->member?.memberId in responseAssignments(heads,c.target);else->true}}
                Column(Modifier.weight(1f).clickable{filter=id},horizontalAlignment=Alignment.CenterHorizontally){Text("${uiText(label)} $count",Modifier.padding(vertical=18.dp),color=if(filter==id)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,fontWeight=FontWeight(750),fontSize=if(LocalUiLanguage.current=="en")12.sp else 14.sp,textAlign=androidx.compose.ui.text.style.TextAlign.Center);Box(Modifier.fillMaxWidth().height(3.dp).background(if(filter==id)MaterialTheme.colorScheme.primary else Color.Transparent))}
            }}}}}
            if(shown.isEmpty())item {Text(uiText("response_no_records"),Modifier.padding(24.dp))}
            items(shown,key={"${it.target.kind}:${it.target.id}"}) {case->
                val events=heads.filter {it.change.target()==case.target}
                val progresses=events.mapNotNull {it.change as? ResponseChange.Progress}
                val assignments=responseAssignments(heads,case.target)
                Row(Modifier.fillMaxWidth().clickable{selected=case.target}.heightIn(min=130.dp).padding(horizontal=16.dp,vertical=18.dp),horizontalArrangement=Arrangement.spacedBy(14.dp),verticalAlignment=Alignment.Top){
                    Box(Modifier.size(50.dp),contentAlignment=Alignment.Center){if(case.target.kind==TargetKind.PERSON)StateSymbol(case.person?.state?:MapSafety.SOS,Modifier.size(46.dp))else Image(painterResource(reportIcon(responseUrgency(heads,case.target))),null,Modifier.size(45.dp),colorFilter=if(responseUrgency(heads,case.target)==Urgency.UNSET)androidx.compose.ui.graphics.ColorFilter.tint(MaterialTheme.colorScheme.onSurface)else null)}
                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(6.dp)){
                        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.Top){
                            Text(caseTitle(case),Modifier.weight(1f),fontSize=19.sp,lineHeight=27.sp,fontWeight=FontWeight.ExtraBold)
                            val urgency=responseUrgency(heads,case.target)
                            Text(uiText("urgency")+" "+uiText(urgencyLabel(urgency)),fontSize=13.sp,lineHeight=18.sp,fontWeight=FontWeight.Bold,color=if(urgency==Urgency.HIGH)MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if(case.description.isNotBlank())Text(case.description,fontSize=15.sp,lineHeight=25.sp,maxLines=2)
                        Text(if(case.cancelled)uiText("cancelled")else if(progresses.isEmpty())uiText("not_started")else progresses.map {uiText(workLabel(it.status))}.distinct().joinToString(" / "),Modifier.background(MaterialTheme.colorScheme.surfaceContainerLow,RoundedCornerShape(5.dp)).padding(horizontal=8.dp,vertical=4.dp),fontSize=12.sp,lineHeight=19.sp,color=MaterialTheme.colorScheme.onSurfaceVariant,fontWeight=FontWeight.Bold)
                        Text(if(assignments.isEmpty())uiText("unassigned")else assignments.joinToString("、"){id->roster?.members?.find {it.memberId==id}?.activityName.orEmpty()},fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        events.maxOfOrNull {it.operatedAt}?.let {Text(formatTime(it),fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)}
                    };PlanIcon("chevron",null,Modifier.size(16.dp).align(Alignment.CenterVertically))
                };HorizontalDivider()
            }
            item {TextButton(onClick={showCancelled=!showCancelled}){Text(uiText(if(showCancelled)"all_filter" else "cancelled_reports"))}}
            item {ResponseFeedback(model)}
        }
        Surface(Modifier.align(Alignment.BottomCenter).fillMaxWidth(),color=MaterialTheme.colorScheme.surface){Column(Modifier.padding(horizontal=16.dp,vertical=10.dp),horizontalAlignment=Alignment.CenterHorizontally){
            Button(onClick={selected=null;point=null;editor="report"},enabled=editable,modifier=Modifier.fillMaxWidth().heightIn(min=50.dp),shape=RoundedCornerShape(8.dp)){Text(uiText("report_add"),fontSize=16.sp,fontWeight=FontWeight(750))}
            TextButton(onClick={scope.launch {try {csv=model.responseCsv(language,cases.mapNotNull {it.person?.takeIf {p->p.identityVisible}?.id}.toSet());export.launch("PlanD-${incidentId}.csv")}catch(e:Exception){model.message.value=exportFailed}}},enabled=member!=null&&incidentId!=null&&!busy){Text(uiText("csv_export"),fontSize=13.sp,fontWeight=FontWeight(750))}
        }}
        val case=cases.find {it.target==selected}
        if(case!=null&&editor==null) {
            val events=heads.filter {it.change.target()==case.target}
            val assignments=responseAssignments(heads,case.target)
            val ownAssigned=member?.memberId in assignments
            val creator=history.find {it.eventId==case.target.id&&(it.change as? ResponseChange.Report)?.reportId==case.target.id}?.actorDeviceId
            val mayManage=editable&&roster?.let {canManageResponseTarget(it,history,model.localId,case.target,incidentId)}==true
            val mayEditReport=mayManage
            ApprovedSheet(caseTitle(case),{selected=null},footer={if(mayManage&&!case.cancelled)Button(onClick={
                if(ownAssigned&&member!=null)model.saveResponse(ResponseChange.Assignment(case.target,member.memberId,false),events.filter {(it.change as? ResponseChange.Assignment)?.memberId==member.memberId}.map {it.eventId})
                else model.startResponse(case.target)
            },enabled=editable,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp),shape=RoundedCornerShape(13.dp)){Text(uiText(if(ownAssigned)"leave_assignment" else "start_work"))}}){
                if(case.description.isNotBlank())Text(case.description,fontSize=16.sp,lineHeight=26.sp)
                case.report?.let {report->
                    val reporter=residents.find {it.originId==creator}
                    val reporterCommunity=communityPeople.find {it.id==creator}?.communityId
                    val reporterHome=creator?.let {validHome(places,it)}?.buildingId?.let {id->placeBuildings(places).find {it.buildingId==id}?.displayLabel()}
                    Text((if(LocalUiLanguage.current=="en")"Reported by: " else "通報者：")+(if(reporter!=null&&residentMapState(reporter,care,roster) in setOf(MapSafety.SAFE,MapSafety.RESCUED))if(english)"Reporter" else "通報者" else if(coordinator&&reporterCommunity==ownCommunity)reporter?.displayName().orEmpty() else reporterHome?:uiText("unregistered")),fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    ResponseFact(if(LocalUiLanguage.current=="en")"Region" else "地域",report.regionIds.joinToString(" / "){id->regions.find {it.id==id}?.let {if(english)it.nameEn else it.nameJa}?:id}.ifBlank {uiText("region_unset")},mayEditReport&&!case.cancelled,mayEditReport){editor="report"}
                    if(report.cancelled)Text(report.cancelReason,color=MaterialTheme.colorScheme.error)
                }
                if(assignments.isNotEmpty()&&!ownAssigned)Surface(color=MaterialTheme.colorScheme.tertiaryContainer,shape=RoundedCornerShape(8.dp)) {Text(uiText("assignment_warning"),Modifier.padding(12.dp),fontSize=14.sp,color=MaterialTheme.colorScheme.onTertiaryContainer)}
                if(events.groupBy {if(roster!=null)responseKey(roster,it)else it.eventId}.values.any {it.size>1})Text(uiText("conflicting_records"),color=MaterialTheme.colorScheme.error)
                val progresses=events.mapNotNull {it.change as? ResponseChange.Progress}
                val priorities=events.mapNotNull {it.change as? ResponseChange.Priority}
                Column {
                    ResponseFact(uiText("assignees"),if(assignments.isEmpty())uiText("unassigned")else assignments.joinToString("、"){id->roster?.members?.find {it.memberId==id}?.activityName.orEmpty()},mayManage&&!case.cancelled,mayManage){editor="assignment"}
                    ResponseFact(uiText("progress"),if(progresses.isEmpty())uiText("not_started")else progresses.map {uiText(workLabel(it.status))}.distinct().joinToString(" / "),mayManage&&!case.cancelled,mayManage){editor="progress"}
                    ResponseFact(uiText("urgency"),if(priorities.isEmpty())uiText("urgency_unset")else priorities.map {uiText(urgencyLabel(it.urgency))}.distinct().joinToString(" / "),editable&&!case.cancelled,coordinator){editor="priority"}
                }
                progresses.filter {it.result.isNotBlank()}.forEach {Text(it.result,fontSize=14.sp)}
                priorities.filter {it.reason.isNotBlank()}.forEach {Text(it.reason,fontSize=14.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)}
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text(uiText("materials"),Modifier.weight(1f),fontWeight=FontWeight.Bold);TextButton(onClick={materialId=null;editor="material"},enabled=mayManage&&!case.cancelled){Text(uiText("material_add"))}}
                val materials=events.mapNotNull {it.change as? ResponseChange.Material}.filterNot {it.cancelled};if(materials.isEmpty())Text(uiText("no_materials"))
                materials.forEach {m->val allowed=editable&&roster?.let {canEditMaterial(it,history,model.localId,m,incidentId)}==true
                    OutlinedCard(onClick={materialId=m.requestId;editor="material"},enabled=allowed&&!case.cancelled){Column(Modifier.fillMaxWidth().padding(12.dp)){Text(m.name,fontWeight=FontWeight.Bold);Text("${uiText("needed")} ${m.needed} / ${uiText("arranged")} ${m.arranged} ${m.unit}");Text(uiText("shortfall",m.shortfall,m.unit))}}
                    if(allowed&&!case.cancelled)TextButton(onClick={materialId=m.requestId;editor="material_cancel"}){Text(if(english)"Cancel material request" else "資材要請を取り消す")}
                }
                if(case.report!=null&&mayEditReport){if(case.cancelled)OutlinedButton(onClick={editor="restore"}){Text(uiText("report_restore"))}else Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){OutlinedButton(onClick={editor="report"}){Text(uiText("report_edit"))};TextButton(onClick={editor="cancel"}){Text(uiText("report_cancel"))}}}
                var historyOpen by remember(case.target) {mutableStateOf(false)}
                HorizontalDivider()
                TextButton(onClick={historyOpen=!historyOpen},modifier=Modifier.fillMaxWidth()) {Text(uiText("history"),Modifier.weight(1f));Text(if(historyOpen)"−" else "+")}
                if(historyOpen) {
                    case.report?.let {Text("${uiText("report_location")}: ${it.latitude}, ${it.longitude}",fontSize=13.sp)}
                    history.filter {it.change.target()==case.target||it.eventId==case.target.id}.sortedByDescending {it.operatedAt}.forEach {e->Text("${formatTime(e.operatedAt)} · ${roster?.members?.find {e.actorDeviceId in it.deviceIds}?.activityName.orEmpty()}",fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant);ResponseEventText(e.change)}
                }
                ResponseFeedback(model)
            }
        }
        editor?.let {mode->key(group,incidentId,mode,selected,materialId){
            val draftKey="$group:$incidentId:$mode:${selected?.kind}:${selected?.id}:$materialId"
            drafts.SaveableStateProvider(draftKey) { ResponseEditor(mode,case,point,materialId,heads,history,roster,member,coordinator,editable,model,onSaved={drafts.removeState(draftKey);editor=null}){editor=null} }
        }}
        if(showSummary)CommunityScreen(model,groupLabel,onClose=onCloseSummary,onCase={target->onCloseSummary();selected=target})
    }
}

@Composable private fun ResponseFeedback(model:PlanViewModel){val states by model.saveStates.collectAsStateWithLifecycle();val state=states["response"];when(state?.phase){SavePhase.SAVING->Text(uiText("saving"));SavePhase.SAVED->Text(uiText("response_saved"));SavePhase.ERROR->Text(state.text.ifBlank {uiText("save_failed")},color=MaterialTheme.colorScheme.error);else->Unit}}

@Composable private fun <T> ChoiceField(options:List<Pair<T,String>>,selected:T,label:String,enabled:Boolean=true,choose:(T)->Unit){
    var open by remember{mutableStateOf(false)}
    Box {OutlinedButton(onClick={open=true},enabled=enabled,modifier=Modifier.fillMaxWidth().heightIn(min=if(label==uiText("my_activity"))57.dp else 48.dp),shape=RoundedCornerShape(10.dp),border=if(label==uiText("my_activity"))null else ButtonDefaults.outlinedButtonBorder(enabled),colors=ButtonDefaults.outlinedButtonColors(containerColor=if(label==uiText("my_activity"))MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,contentColor=MaterialTheme.colorScheme.onSurface)){Text("$label: ${options.find {it.first==selected}?.second.orEmpty()}",Modifier.weight(1f));PlanIcon("chevron",null,Modifier.size(16.dp))};DropdownMenu(open,{open=false}){options.forEach {(value,text)->DropdownMenuItem(text={Text(text)},onClick={choose(value);open=false})}}}
}

@Composable private fun ResponseEventText(change:ResponseChange){when(change){
    is ResponseChange.Report->{Text(categoryLabel(change.category));Text(change.description);if(change.cancelled)Text("${uiText("cancelled")}: ${change.cancelReason}")}
    is ResponseChange.Progress->{Text(uiText(workLabel(change.status)));if(change.result.isNotBlank())Text(change.result)}
    is ResponseChange.Priority->{Text(uiText(urgencyLabel(change.urgency)));Text(change.reason)}
    is ResponseChange.Material->{Text("${change.name}: ${change.arranged} / ${change.needed} ${change.unit}");if(change.cancelled)Text("${uiText("cancelled")}: ${change.cancelReason}")}
    is ResponseChange.Assignment->Text(uiText(if(change.active)"assignees" else "leave_assignment"))
    is ResponseChange.Injury->Text(communityLabel("injury",LocalUiLanguage.current=="en")+": "+communityLabel(change.state.name,LocalUiLanguage.current=="en"))
    is ResponseChange.Availability->Text(uiText(activityLabel(change.state)))
}}

@Composable private fun ResponseEditor(mode:String,case:ResponseCase?,point:MapPoint?,materialId:String?,heads:List<ResponseEvent>,history:List<ResponseEvent>,roster:MemberRoster?,member:GroupMember?,coordinator:Boolean,editable:Boolean,model:PlanViewModel,onSaved:()->Unit,close:()->Unit){
    val target=case?.target
    val targetHeads=heads.filter {it.change.target()==target}
    val report=case?.report
    val oldMaterial=targetHeads.mapNotNull {it.change as? ResponseChange.Material}.firstOrNull {it.requestId==materialId}
    val currentHeads=targetHeads.filter {when(mode){"report","cancel","restore"->it.change is ResponseChange.Report;"progress"->it.change is ResponseChange.Progress;"priority"->it.change is ResponseChange.Priority;"material","material_cancel"->(it.change as? ResponseChange.Material)?.let {material->material.requestId==materialId}==true;else->false}}
    var reviewedIds by rememberSaveable { mutableStateOf(currentHeads.map {it.eventId}) }
    val reviewedHeads=currentHeads.filter {it.eventId in reviewedIds}
    val editorUpdated=currentHeads.map {it.eventId}.toSet()!=reviewedIds.toSet()
    var reviewed by rememberSaveable(reviewedHeads.map {it.eventId}.joinToString()) {mutableStateOf(false)}
    val venue by model.activeVenue.collectAsStateWithLifecycle()
    val regionOptions=communityNamesFor(venue,uiText("group_label"))
    var regionId by rememberSaveable {mutableStateOf(report?.regionIds?.firstOrNull()?:point?.takeIf {venue?.scenario!=null&&it.latitude in 35.0..35.04&&it.longitude in 135.75..135.78}?.longitude?.let(::demoCommunityFor).orEmpty())}
    var choosingLocation by rememberSaveable {mutableStateOf(false)}
    var category by rememberSaveable {mutableStateOf(report?.category?:"道路閉塞")}
    var text by rememberSaveable {mutableStateOf(when(mode){"report"->report?.description.orEmpty();"progress"->targetHeads.mapNotNull {it.change as? ResponseChange.Progress}.singleOrNull()?.result.orEmpty();"priority"->targetHeads.mapNotNull {it.change as? ResponseChange.Priority}.singleOrNull()?.reason.orEmpty();else->""})}
    var lat by rememberSaveable {mutableStateOf((report?.latitude?:point?.latitude)?.toString().orEmpty())};var lon by rememberSaveable {mutableStateOf((report?.longitude?:point?.longitude)?.toString().orEmpty())}
    var progress by rememberSaveable {mutableStateOf(targetHeads.mapNotNull {it.change as? ResponseChange.Progress}.singleOrNull()?.status?:WorkStatus.UNASSIGNED)}
    var urgency by rememberSaveable {mutableStateOf(targetHeads.mapNotNull {it.change as? ResponseChange.Priority}.singleOrNull()?.urgency?:Urgency.UNSET)}
    var name by rememberSaveable {mutableStateOf(oldMaterial?.name.orEmpty())};var unit by rememberSaveable {mutableStateOf(oldMaterial?.unit.orEmpty())};var needed by rememberSaveable {mutableStateOf(oldMaterial?.needed?.toString().orEmpty())};var arranged by rememberSaveable {mutableStateOf(oldMaterial?.arranged?.toString()?:"0")}
    val mayManage=target!=null&&roster?.let {canManageResponseTarget(it,history,model.localId,target,model.incidentUi.value.current?.start?.eventId)}==true
    val permitted=editable&&when(mode){
        "priority"->coordinator
        "report"->report==null||mayManage
        "material","material_cancel"->if(oldMaterial==null)mayManage else roster?.let {canEditMaterial(it,history,model.localId,oldMaterial,model.incidentUi.value.current?.start?.eventId)}==true
        else->mayManage
    }
    val valid=!editorUpdated&&(reviewedHeads.size<2||reviewed)&&when(mode){"report"->category.isNotBlank()&&text.isNotBlank()&&lat.toDoubleOrNull()?.let {it.isFinite()&&it in -85.0..85.0}==true&&lon.toDoubleOrNull()?.let {it.isFinite()&&it in -180.0..180.0}==true;"progress"->progress!=WorkStatus.DONE||text.isNotBlank();"priority"->urgency==Urgency.UNSET||text.isNotBlank();"material"->name.isNotBlank()&&unit.isNotBlank()&&listOf(needed,arranged).all {it.toDoubleOrNull()?.let {v->v.isFinite()&&v>=0}==true};"cancel"->text.isNotBlank();"material_cancel"->oldMaterial!=null&&text.isNotBlank();"restore"->true;else->false}
    val title=if(mode=="material_cancel")if(LocalUiLanguage.current=="en")"Cancel material request" else "資材要請を取り消す" else uiText(when(mode){"report"->if(report==null)"report_add" else "report_edit";"progress"->"progress_update";"material"->"material_add";"priority"->"urgency";"cancel"->"report_cancel";"restore"->"report_restore";else->"assignment_change"})
    if(choosingLocation) {
        ApprovedSheet(if(LocalUiLanguage.current=="en")"Choose report location" else "通報地点を選択",{choosingLocation=false},scrollContent=false) {
            Text(if(LocalUiLanguage.current=="en")"Tap the report location on the map" else "地図上で通報地点をタップしてください",Modifier.padding(16.dp))
            val camera=remember {SituationCamera()}
            val center=if(lat.toDoubleOrNull()!=null&&lon.toDoubleOrNull()!=null)MapPoint(lat.toDouble(),lon.toDouble())else if(venue?.positionMode=="ACTUAL")MapPoint(35.6665,139.7495)else MapPoint(35.0145,135.7625)
            SituationMap(emptyList(),emptyList(),center,true,System.currentTimeMillis(),false,camera,{},{},{},{},Modifier.fillMaxWidth().height(420.dp),onReportPoint={p->lat=p.latitude.toString();lon=p.longitude.toString();choosingLocation=false})
        }
        return
    }
    ApprovedSheet(title,close,footer=if(mode=="assignment")null else {{Button(onClick={
        val change:ResponseChange=when(mode){"report"->ResponseChange.Report(report?.reportId?:UUID.randomUUID().toString(),category,lat.toDouble(),lon.toDouble(),text,regionIds=regionId.takeIf {it.isNotBlank()}?.let {listOf(it)}?:emptyList());"progress"->ResponseChange.Progress(target!!,progress,text);"priority"->ResponseChange.Priority(target!!,urgency,text);"material"->ResponseChange.Material(target!!,materialId?:UUID.randomUUID().toString(),name,unit,needed.toDouble(),arranged.toDouble());"restore"->report!!.copy(cancelled=false,cancelReason="");"material_cancel"->oldMaterial!!.copy(cancelled=true,cancelReason=text);else->report!!.copy(cancelled=true,cancelReason=text)}
        val replaced=reviewedHeads.map {it.eventId}
        model.saveResponse(change,replaced,onSaved)
    },enabled=permitted&&valid,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp),shape=RoundedCornerShape(13.dp)){Text(uiText("save"))}}}) {
        if(editorUpdated) {
            Text(uiText("editor_updated"),color=MaterialTheme.colorScheme.error)
            currentHeads.forEach { ResponseEventText(it.change) }
            TextButton(onClick={reviewedIds=currentHeads.map {it.eventId};reviewed=false}) {Text(uiText("review_current_heads"))}
        }
        if(reviewedHeads.size>1){
            Text(uiText("conflicting_records"),color=MaterialTheme.colorScheme.error)
            reviewedHeads.forEach {e->OutlinedCard{Column(Modifier.fillMaxWidth().padding(12.dp)){Text(formatTime(e.operatedAt),style=MaterialTheme.typography.labelSmall);ResponseEventText(e.change)}}}
            Row(verticalAlignment=Alignment.CenterVertically){Checkbox(reviewed,{reviewed=it},enabled=editable);Text(uiText("review_current_heads"),Modifier.weight(1f))}
        }
        if(mode=="report"){
            ApprovedField(category,{category=it},uiText("report_category"),modifier=Modifier.fillMaxWidth(),singleLine=true,enabled=editable)
            OutlinedButton(onClick={choosingLocation=true},enabled=editable,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){Text(if(lat.isBlank()||lon.isBlank())if(LocalUiLanguage.current=="en")"Choose report location on map" else "地図で通報地点を選ぶ" else if(LocalUiLanguage.current=="en")"Report location selected · Change" else "通報地点を選択済み・変更")}
            ChoiceField(listOf("" to uiText("region_unset"))+regionOptions.map {it.id to if(LocalUiLanguage.current=="en")it.nameEn else it.nameJa},regionId,if(LocalUiLanguage.current=="en")"Region" else "地域",editable){regionId=it}

        }
        if(mode=="progress")ChoiceField(WorkStatus.entries.map {it to uiText(workLabel(it))},progress,uiText("progress"),editable){progress=it}
        if(mode=="priority")ChoiceField(Urgency.entries.map {it to uiText(urgencyLabel(it))},urgency,uiText("urgency"),editable){urgency=it}
        if(mode in listOf("report","progress","priority","cancel","material_cancel"))ApprovedField(text,{text=it.take(4000)},uiText(when(mode){"report"->"report_description";"progress"->if(progress==WorkStatus.DONE)"result_required" else "result";"cancel","material_cancel"->"cancel_reason";else->"reason"}),minLines=4,modifier=Modifier.fillMaxWidth(),enabled=editable)
        if(mode=="material"){
            ApprovedField(name,{name=it},uiText("material_name"),Modifier.fillMaxWidth(),enabled=editable)
            ApprovedField(unit,{unit=it},uiText("unit"),Modifier.fillMaxWidth(),enabled=editable)
            Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){
                ApprovedField(needed,{needed=it},uiText("needed"),Modifier.weight(1f),enabled=editable)
                ApprovedField(arranged,{arranged=it},uiText("arranged"),Modifier.weight(1f),enabled=editable)
            }
            if(needed.toDoubleOrNull()!=null&&arranged.toDoubleOrNull()!=null)Text(uiText("shortfall",(needed.toDouble()-arranged.toDouble()).coerceAtLeast(0.0),unit))
        }
        if(mode=="assignment"&&target!=null)roster?.members?.filter {coordinator||it.memberId==member?.memberId}?.forEach {person->
            val old=targetHeads.filter {(it.change as? ResponseChange.Assignment)?.memberId==person.memberId}
            val active=old.any {(it.change as ResponseChange.Assignment).active}
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text(person.activityName,Modifier.weight(1f));Switch(active,{value->model.saveResponse(ResponseChange.Assignment(target,person.memberId,value),old.map {it.eventId})},enabled=editable)}
        }
        ResponseFeedback(model)
    }
}

/** Keep the label, value and its edit action together, including at larger font sizes. */
@Composable private fun ResponseFact(label:String,value:String,enabled:Boolean,editable:Boolean=true,edit:()->Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min=64.dp).padding(vertical=8.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
            Text(label,fontSize=13.sp,lineHeight=19.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value,fontSize=16.sp,lineHeight=24.sp,fontWeight=FontWeight.Bold)
        }
        val changeLabel=uiText("change")
        if(editable)TextButton(onClick=edit,enabled=enabled,modifier=Modifier.semantics{contentDescription="$label $changeLabel"}) {Text(changeLabel)}
    }
    HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
}
