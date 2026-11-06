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
    Icon(androidx.compose.ui.res.painterResource(resource),description?.let {menuText(it)},
        modifier.then(if(kind=="back")Modifier.rotate(180f)else Modifier),
        tint=if(kind in listOf("menu","back"))MaterialTheme.colorScheme.primary else LocalContentColor.current)
}

@Composable fun planNavigationColors() = NavigationBarItemDefaults.colors(
    selectedIconColor = MaterialTheme.colorScheme.primary, selectedTextColor = MaterialTheme.colorScheme.primary,
    unselectedIconColor = androidx.compose.ui.graphics.Color(0xFF7C8793), unselectedTextColor = androidx.compose.ui.graphics.Color(0xFF7C8793),
    indicatorColor = androidx.compose.ui.graphics.Color.Transparent)

@Composable fun SafetyScreen(model: PlanViewModel, own: Resident?, saving: Boolean, onProfile: () -> Unit, onIncident: () -> Unit) {
    val incident by model.incidentUi.collectAsStateWithLifecycle()
    val roster by model.roster.collectAsStateWithLifecycle()
    val registered=own!=null||roster?.members?.any {model.localId in it.deviceIds}==true
    if (!registered || !incident.canEdit) {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Text(uiText("my_safety"), style = MaterialTheme.typography.headlineSmall)
            Text(uiText(if (!registered) "register_name_home" else "waiting_start"))
            Button(onClick = if (!registered) onProfile else onIncident) { Text(uiText(if (!registered) "register_profile" else "incident_management")) }
        }
    } else {
        val first = incident.current?.answers?.containsKey(model.localId) != true
        val memos by model.memos.collectAsStateWithLifecycle()
        val answer = incident.current?.answers?.get(model.localId)
        val memo = memos.find { it.answerId == answer?.eventId && it.incidentId == answer?.incidentId && it.authorId==model.localId }?.text.orEmpty()
        key(incident.groupId, incident.selection, model.localId) {
            AnswerPanel(first, when (answer?.safety?:own?.safety) { Safety.SAFE -> MapSafety.SAFE; Safety.SOS -> MapSafety.SOS; else -> null }, memo, saving,
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
        Text(menuText("もしものときに、\n地域でつながる。"), style = MaterialTheme.typography.headlineMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
        Text(menuText(if (registered) "安否確認の開始を待っています" else "はじめに自分の住居・寝室を登録しましょう"), color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (!registered) Button(onClick = onProfile, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(menuText("自分の情報を登録")) }
        else OutlinedButton(onClick = onIncident, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(menuText("災害・訓練の管理")) }
        Text(menuText(if (nearby.active) "共有中・接続 ${nearby.connected.size}台" else "共有は停止中"), style = MaterialTheme.typography.bodyMedium)
        if (BuildConfig.DEBUG) { Spacer(Modifier.weight(1f)); TextButton(onClick = onPreview) { Text(menuText("画面訓練を試す")) } }
    }
}

@Composable fun MapScreen(model: PlanViewModel, residents: List<Resident>, own: Resident?, picking: Boolean, onPick: (MapPoint) -> Unit, onCancelPick: () -> Unit, onSelect: (Resident) -> Unit, onBuilding: (String) -> Unit, openList: Boolean = false, onListClosed: () -> Unit = {}, listOnly: Boolean = false, onAddReport:((MapPoint)->Unit)?=null,onReport:(String)->Unit={},onPersonResponse:((String)->Unit)?=null) {
    val care by model.careEvents.collectAsStateWithLifecycle()
    val roster by model.roster.collectAsStateWithLifecycle()
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
    val app=LocalContext.current.applicationContext as PlanDApplication
    val registeredBuildings=remember(places){placeBuildings(places)}
    val buildingCounts=remember(residents,places,care,roster){buildingResidentCounts(residents,places,care,roster)}
    val homes by produceState(registeredBuildings.map {MapHome(it.buildingId!!,it.displayLabel(),MapPoint(it.latitude!!,it.longitude!!))},registeredBuildings) {
        value=registeredBuildings.map {building->
            val home=MapHome(building.buildingId!!,building.displayLabel(),MapPoint(building.latitude!!,building.longitude!!),plateauId=building.locationReference?.plateauId)
            val id=building.locationReference?.plateauId
            if(id==null)home else try {
                app.buildingAssessments.registeredBuilding(id,building.latitude,building.longitude)?.let {home.withBuildingAttributes(it)}?:home.copy(attributesUnavailable=true)
            }catch(e:kotlinx.coroutines.CancellationException){throw e}catch(_:Exception){home.copy(attributesUnavailable=true)}
        }
    }
    val language = LocalUiLanguage.current
    val people = remember(residents, places, care, positions, memos, incident, now,venue,deviceRecords,homes,language,roster) {
        val locations = positions.filter {venue?.positionMode!="ACTUAL"||it.acquiredAt>=venue!!.startedAt}.associateBy { it.originId }
        val notes = memos.associateBy { it.answerId }
        residents.map { person ->
            val answer = incident.current?.answers?.get(person.originId)
            val scenarioPerson=venue?.scenario?.people?.find {it.deviceId==person.originId}
            val simulated=venue?.observationTimes(person.originId)
            val base=residentMapPerson(person, places, care, locations[person.originId],
                if (answer?.safety == Safety.SOS) notes[answer.eventId]?.takeIf {it.authorId==person.originId&&it.incidentId==answer.incidentId}?.text?:scenarioPerson?.note.orEmpty() else scenarioPerson?.note.orEmpty(), language=language,roster=roster).copy(impactAt=if(scenarioPerson!=null)simulated?.first else latestImpact(deviceRecords,group,person.originId,now),enclosedAt=simulated?.second)
            val placement=venue?.allPlacements()?.find {it.deviceId==person.originId}
            val enriched=base.copy(home=base.home?.let {home->homes.find {it.id==home.id}?:home})
            projectPersonPrivacy(if(placement==null)enriched else if(venue?.positionMode=="ACTUAL"&&placement.source!="SCENARIO")enriched.copy(name=placement.name,home=null,demo=true) else enriched.copy(name=placement.name,home=enriched.home.takeIf {placement.home!=null},bedroom=base.bedroom.takeIf {placement.home!=null}.orEmpty(),point=placement.latitude?.let {lat->placement.longitude?.let {lon->MapPoint(lat,lon)}},acquiredAt=venue?.assignedPositionTime(placement,locations[person.originId]),assignedPosition=venue?.positionMode=="KYOTO"&&placement.source!="SCENARIO",lastKnownAt=venue?.scenario?.people?.find {it.deviceId==placement.deviceId}?.lastKnownPosition?.acquiredAt,accuracy=null,demo=true,fictional=placement.source=="SCENARIO"),incident.current?.start?.createdAt,now)
        }
    }
    val incidentResponses=responses.filter {it.groupId==group&&it.incidentId==incident.current?.start?.eventId}.map {it.copy(change=io.github.nevaseme.plandmk1.core.locateResponseChange(it.change,communityNamesFor(venue,"")))}
    val groupName=cloud.takeIf {it.groupId==group}?.groupName?.takeIf {it.isNotBlank()}?:uiText("group_label")
    val communityPeople=communityPeopleFor(residents,care,venue,roster)
    val ownNeighborhood=communityPeople.find {it.id==model.localId}?.communityId
    val names=communityNamesFor(venue,groupName)
    val communityRows=communityOverview(communityPeople,incidentResponses.map {it.change},names)
    val activeRows=communityOverview(communityPeople,activeMaterialChanges(incidentResponses.map {it.change}),names)
    val neighborhoods=communityRows.map {row->
        val points=venue?.allPlacements().orEmpty().filter {p->communityPeople.any {it.id==p.deviceId&&it.communityId==row.id}}.mapNotNull {it.home?.let {h->MapPoint(h.latitude,h.longitude)}}
        val region=venue?.scenario?.communities?.find {it.id==row.id}
        val regionPoint=region?.latitude?.let {lat->region.longitude?.let {lon->MapPoint(lat,lon)}}
        MapNeighborhood(row.copy(materials=activeRows.find {it.id==row.id}?.materials?:mutableListOf()),regionPoint?:points.takeIf {it.isNotEmpty()}?.let {MapPoint(it.map {p->p.latitude}.average(),it.map {p->p.longitude}.average())},activeHighReports(incidentResponses.map {it.change},row.id),incidentResponses.mapNotNull {it.change as? ResponseChange.Report}.filter {!it.cancelled&&row.id in it.regionIds}.distinctBy {it.reportId}.map {it.reportId to it.category})
    }
    val visiblePeople=people.filter {p->venue?.scenario==null||p.id==model.localId||communityPeople.any {it.id==p.id&&it.communityId==ownNeighborhood}}
    val outsideBuildingIds=if(venue?.scenario==null)emptySet() else {
        val ownBuildings=communityPeople.filter {it.communityId==ownNeighborhood}.mapNotNull {homeFor(places,it.id)?.buildingId}.toSet()
        val otherBuildings=communityPeople.filter {it.communityId!=ownNeighborhood}.mapNotNull {homeFor(places,it.id)?.buildingId}.toSet()-ownBuildings
        homes.filter {it.id in otherBuildings}.flatMap {listOfNotNull(it.id,it.plateauId)}.toSet()
    }
    val reports=incidentResponses.mapNotNull {it.change as? ResponseChange.Report}.filterNot {it.cancelled}.distinctBy {it.reportId}.map {report->MapReport(report.reportId,MapPoint(report.latitude,report.longitude),responseUrgency(incidentResponses,ResponseTarget(TargetKind.REPORT,report.reportId)))}
    val center = if(!picking&&venue?.positionMode=="KYOTO") MapPoint(35.0145,135.7625)
        else positions.find { it.originId == model.localId&&(venue?.positionMode!="ACTUAL"||it.acquiredAt>=venue!!.startedAt) }?.let { MapPoint(it.latitude, it.longitude) }
            ?: if(venue?.positionMode=="ACTUAL")MapPoint(35.6665,139.7495) else own?.takeIf {it.latitude.isFinite()&&it.longitude.isFinite()}?.let { MapPoint(it.latitude, it.longitude) } ?: MapPoint(35.003, 135.759)
    if (picking) {
        val app=LocalContext.current.applicationContext as PlanDApplication
        val source=remember(app){app.positionSourceFactory()}
        HomeLocationPicker(center,onPick,onCancelPick) {source().let {MapPoint(it.latitude,it.longitude)}}
    } else SituationScreen(visiblePeople, homes.filter {home->visiblePeople.any {it.home?.id==home.id&&it.registeredPlaceVisible}}, center, now, incidentKey=group+":"+(incident.current?.start?.eventId?:""), assignedLocation=venue?.takeIf {it.positionMode=="KYOTO"}?.placements?.find {it.deviceId==model.localId}?.let {p->p.latitude?.let {lat->p.longitude?.let {lon->MapPoint(lat,lon)}}}, neighborhoods=neighborhoods,ownNeighborhoodId=ownNeighborhood,onPersonResponse=onPersonResponse, initialZoom=if(venue!=null)14.5 else 16.8, canRescue = own != null && incident.canEdit && !saving,
        outsideBuildingIds=outsideBuildingIds,buildingCounts=buildingCounts,registeredBuildingIds=homes.filter {h->(buildingCounts[h.id]?.total?:0)>0}.mapNotNull {it.plateauId},
        canUndo = { p -> canRevokeRescue(roster,group,model.localId)&&currentCareEvents(care.filter { it.subjectId == p.id },roster).any { it.action == CareAction.RESCUED } },
        onRescue = { model.saveCare(it.id, CareAction.RESCUED, "本人の救助完了を確認") },
        onUndo = { p -> model.revokeRescues(p.id) },
        history = { p -> residents.find { it.originId == p.id }?.let { CareHistoryAndEditor(model, it, care, own != null && incident.canEdit, saving) } },
        feedback = { p -> OperationFeedback(model, model.careSaveKey(p.id)) },
        openList = openList, onListClosed = onListClosed, listOnly = listOnly,onAddReport=onAddReport.takeIf {incident.canEdit},reports=reports,onReport=onReport,statisticsLabel=cloud.takeIf {it.groupId==group}?.groupName?.takeIf {it.isNotBlank()}?:uiText("group_label"))
}

@Composable fun AppInformation() {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text("Help Net", style = MaterialTheme.typography.headlineMedium)
        Text((if(LocalUiLanguage.current=="en")"Version " else "バージョン ")+BuildConfig.VERSION_NAME, style = MaterialTheme.typography.labelLarge)
        Text(if(LocalUiLanguage.current=="en")"Building assessment: JMA instrumental intensity 6.2. Damage rates are estimates for buildings of the same structural type and age." else "建物評価の想定計測震度：6.2。同じ構造・年代の建物に対する全壊率の試算です。")
        HorizontalDivider()
        Text((if(LocalUiLanguage.current=="en")"Map: " else "地図：")+"OpenFreeMap / OpenMapTiles / OpenStreetMap", style = MaterialTheme.typography.bodySmall)
        Text((if(LocalUiLanguage.current=="en")"Impact icon: " else "衝撃アイコン：")+"Font Awesome Free Burst / Fonticons, Inc. / CC BY 4.0", style = MaterialTheme.typography.bodySmall)
    }
}
