package io.github.nevaseme.plandmk1.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nevaseme.plandmk1.*
import io.github.nevaseme.plandmk1.R
import io.github.nevaseme.plandmk1.core.*

@Composable fun PlanIcon(kind: String, description: String?, modifier: Modifier = Modifier.size(24.dp)) {
    val resource=when(kind){
        "map"->R.drawable.approved_map;"list"->R.drawable.approved_list;"response"->R.drawable.approved_response
        "menu"->R.drawable.approved_menu;"close"->R.drawable.approved_close;"target"->R.drawable.approved_target
        "chevron","back"->R.drawable.approved_chevron;"home"->R.drawable.approved_home
        "settings"->R.drawable.approved_settings;"building"->R.drawable.approved_building;else->R.drawable.approved_person
    }
    Icon(androidx.compose.ui.res.painterResource(resource),description,
        modifier.then(if(kind=="back")Modifier.rotate(180f)else Modifier),
        tint=if(kind in listOf("menu","back"))MaterialTheme.colorScheme.primary else LocalContentColor.current)
}

@Composable fun planNavigationColors() = NavigationBarItemDefaults.colors(
    selectedIconColor = MaterialTheme.colorScheme.primary, selectedTextColor = MaterialTheme.colorScheme.primary,
    unselectedIconColor = androidx.compose.ui.graphics.Color(0xFF7C8793), unselectedTextColor = androidx.compose.ui.graphics.Color(0xFF7C8793),
    indicatorColor = androidx.compose.ui.graphics.Color.Transparent)

@Composable fun SafetyScreen(model: PlanViewModel, own: Resident?, saving: Boolean, onProfile: () -> Unit, onIncident: () -> Unit) {
    val incident by model.incidentUi.collectAsStateWithLifecycle()
    if (own == null || !incident.canEdit) {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Text(uiText("my_safety"), style = MaterialTheme.typography.headlineSmall)
            Text(uiText(if (own == null) "register_name_home" else "waiting_start"))
            Button(onClick = if (own == null) onProfile else onIncident) { Text(uiText(if (own == null) "register_profile" else "incident_management")) }
        }
    } else {
        val first = incident.current?.answers?.containsKey(model.localId) != true
        val memos by model.memos.collectAsStateWithLifecycle()
        val answer = incident.current?.answers?.get(model.localId)
        val memo = memos.find { it.answerId == answer?.eventId && it.incidentId == answer?.incidentId && it.authorId==model.localId }?.text.orEmpty()
        key(incident.groupId, incident.selection, model.localId) {
            AnswerPanel(first, when (own.safety) { Safety.SAFE -> MapSafety.SAFE; Safety.SOS -> MapSafety.SOS; else -> null }, memo, saving,
                save = { state, text -> model.setSafety(if (state == MapSafety.SAFE) Safety.SAFE else Safety.SOS, text) },
                saveMemo = answer?.takeIf { it.safety == Safety.SOS }?.let { current -> { text -> model.saveSosMemo(current.eventId, text) } },
                feedback = { OperationFeedback(model, "safety") })
        }
    }
}

@Composable fun PeaceHome(model: PlanViewModel, registered: Boolean, onProfile: () -> Unit, onIncident: () -> Unit, onPreview: () -> Unit) {
    val nearby by model.sharing.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Spacer(Modifier.height(24.dp))
        Text("もしものときに、\n地域でつながる。", style = MaterialTheme.typography.headlineMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
        Text(if (registered) "安否確認の開始を待っています" else "はじめに自分の住居・寝室を登録しましょう", color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (!registered) Button(onClick = onProfile, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("自分の情報を登録") }
        else OutlinedButton(onClick = onIncident, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("災害・訓練の管理") }
        Text(if (nearby.active) "共有中・接続 ${nearby.connected.size}台" else "共有は停止中", style = MaterialTheme.typography.bodyMedium)
        if (BuildConfig.DEBUG) { Spacer(Modifier.weight(1f)); TextButton(onClick = onPreview) { Text("画面デモを試す") } }
    }
}

@Composable fun MapScreen(model: PlanViewModel, residents: List<Resident>, own: Resident?, picking: Boolean, onPick: (MapPoint) -> Unit, onCancelPick: () -> Unit, onSelect: (Resident) -> Unit, onBuilding: (String) -> Unit, openList: Boolean = false, onListClosed: () -> Unit = {}, listOnly: Boolean = false, onAddReport:((MapPoint)->Unit)?=null,onReport:(String)->Unit={}) {
    val care by model.careEvents.collectAsStateWithLifecycle()
    val places by model.places.collectAsStateWithLifecycle()
    val incident by model.incidentUi.collectAsStateWithLifecycle()
    val saving by model.saving.collectAsStateWithLifecycle()
    val deviceRecords by model.deviceRecords.collectAsStateWithLifecycle()
    val positions by model.positions.collectAsStateWithLifecycle()
    val memos by model.memos.collectAsStateWithLifecycle()
    val responses by model.responseEvents.collectAsStateWithLifecycle()
    val cloud by model.cloud.state.collectAsStateWithLifecycle()
    val group by model.group.collectAsStateWithLifecycle()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val mapVisible = LocalMapVisible.current
    LaunchedEffect(mapVisible,incident.current?.start?.createdAt) {
        if (mapVisible) {
            now = System.currentTimeMillis()
            while (true) {
                val disclosure=incident.current?.start?.createdAt?.plus(180_000)
                val until=disclosure?.minus(now)?.takeIf {it>0}?:60_000
                kotlinx.coroutines.delay(minOf(60_000L,until)); now = System.currentTimeMillis()
            }
        }
    }
    val venue by model.activeVenue.collectAsStateWithLifecycle()
    val people = remember(residents, places, care, positions, memos, incident, now,venue,deviceRecords) {
        val locations = positions.filter {venue?.positionMode!="ACTUAL"||it.acquiredAt>=venue!!.startedAt}.associateBy { it.originId }
        val notes = memos.associateBy { it.answerId }
        residents.map { person ->
            val answer = incident.current?.answers?.get(person.originId)
            val scenarioPerson=venue?.scenario?.people?.find {it.deviceId==person.originId}
            val base=residentMapPerson(person, places, care, locations[person.originId],
                if (answer?.safety == Safety.SOS) notes[answer.eventId]?.takeIf {it.authorId==person.originId&&it.incidentId==answer.incidentId}?.text?:scenarioPerson?.note.orEmpty() else scenarioPerson?.note.orEmpty()).copy(impactAt=latestImpact(deviceRecords,group,person.originId,now))
            val placement=venue?.allPlacements()?.find {it.deviceId==person.originId}
            projectPersonPrivacy(if(placement==null)base else if(venue?.positionMode=="ACTUAL"&&placement.source!="SCENARIO")base.copy(name=placement.name,home=null,demo=true) else base.copy(name=placement.name,point=MapPoint(checkNotNull(placement.latitude),checkNotNull(placement.longitude)),acquiredAt=incident.current?.start?.createdAt,accuracy=null,demo=true,fictional=placement.source=="SCENARIO"),incident.current?.start?.createdAt,now)
        }
    }
    val homes = placeBuildings(places).map { MapHome(it.buildingId!!, it.displayLabel(), MapPoint(it.latitude!!, it.longitude!!)) }
    val incidentResponses=responses.filter {it.incidentId==incident.current?.start?.eventId}
    val reports=incidentResponses.mapNotNull {it.change as? ResponseChange.Report}.filterNot {it.cancelled}.distinctBy {it.reportId}.map {report->MapReport(report.reportId,MapPoint(report.latitude,report.longitude),responseUrgency(incidentResponses,ResponseTarget(TargetKind.REPORT,report.reportId)))}
    val center = if(!picking&&venue?.positionMode=="KYOTO") MapPoint(35.0145,135.7625)
        else positions.find { it.originId == model.localId&&(venue?.positionMode!="ACTUAL"||it.acquiredAt>=venue!!.startedAt) }?.let { MapPoint(it.latitude, it.longitude) }
            ?: if(venue?.positionMode=="ACTUAL")MapPoint(35.6665,139.7495) else own?.takeIf {it.latitude.isFinite()&&it.longitude.isFinite()}?.let { MapPoint(it.latitude, it.longitude) } ?: MapPoint(35.003, 135.759)
    if (picking) {
        val app=LocalContext.current.applicationContext as PlanDApplication
        val source=remember(app){app.positionSourceFactory()}
        HomeLocationPicker(center,onPick,onCancelPick) {source().let {MapPoint(it.latitude,it.longitude)}}
    } else SituationScreen(people, homes, center, now, initialZoom=if(venue!=null)14.5 else 16.8, canRescue = own != null && incident.canEdit && !saving,
        canUndo = { p -> currentCareEvents(care.filter { it.subjectId == p.id }).any { it.action == CareAction.RESCUED && it.authorId == model.localId } },
        onRescue = { model.saveCare(it.id, CareAction.RESCUED, "本人の救助完了を確認") },
        onUndo = { p -> currentCareEvents(care.filter { it.subjectId == p.id }).find { it.action == CareAction.RESCUED && it.authorId == model.localId }?.let { model.saveCare(p.id, CareAction.REVOKED, "救助完了の記録を訂正", it.eventId) } },
        history = { p -> residents.find { it.originId == p.id }?.let { CareHistoryAndEditor(model, it, care, own != null && incident.canEdit, saving) } },
        feedback = { p -> OperationFeedback(model, model.careSaveKey(p.id)) },
        openList = openList, onListClosed = onListClosed, listOnly = listOnly,onAddReport=onAddReport.takeIf {incident.canEdit},reports=reports,onReport=onReport,statisticsLabel=cloud.takeIf {it.groupId==group}?.groupName?.takeIf {it.isNotBlank()}?:uiText("group_label"))
}

@Composable fun AppInformation() {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text("PlanD Mk1", style = MaterialTheme.typography.headlineMedium)
        Text("バージョン ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.labelLarge)
        HorizontalDivider()
        Text("地図：OpenFreeMap / OpenMapTiles / OpenStreetMap", style = MaterialTheme.typography.bodySmall)
        Text("衝撃アイコン：Font Awesome Free Burst / Fonticons, Inc. / CC BY 4.0", style = MaterialTheme.typography.bodySmall)
    }
}
