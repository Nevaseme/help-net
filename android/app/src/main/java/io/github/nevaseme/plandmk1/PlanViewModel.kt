package io.github.nevaseme.plandmk1

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.nevaseme.plandmk1.core.*

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi

import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch


data class MapPoint(val latitude: Double, val longitude: Double)

@OptIn(ExperimentalCoroutinesApi::class)
class PlanViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as PlanDApplication
    val localId = app.installation.localId
    val group = app.installation.group
    val message = MutableStateFlow<String?>(null)
    val roster=group.flatMapLatest {app.quakes.observeRoster(it)}.stateIn(viewModelScope,SharingStarted.Eagerly,null)
    fun canStartTraining() = roster.value?.takeIf { it.groupId == group.value }?.members
        ?.any { localId in it.deviceIds && it.role == MemberRole.ADMIN } == true
    fun canEndIncident(startedBy:String)=roster.value?.members?.any {localId in it.deviceIds&&it.role==MemberRole.ADMIN} ?: (startedBy==localId)
    fun importParticipants(json:String) {
        viewModelScope.launch {
            try {app.quakes.importRoster(json,localId);message.value="参加者設定を保存しました"}
            catch(e:CancellationException){throw e}
            catch(e:Exception){message.value=e.localizedMessage}
        }
    }
    val deviceRecords=group.flatMapLatest {app.deviceRecords.observe(it)}.stateIn(viewModelScope,SharingStarted.Eagerly,emptyList())
    val incidentUi = group.flatMapLatest { g ->
        val authority=combine(app.quakes.observeRoster(g),app.quakes.observe(g),app.cloud.venueChanges,deviceRecords) {roster,events,_,records->Triple(roster,events,app.cloud.venue(g)?.withHomes(records))}
        combine(app.store.observe(g),app.store.observeEvents(g),app.store.observeIncidents(g),app.installation.incidentSelectionChanges,authority) { residents, care, journal, _, authorityState ->
            val preferred=canonicalIncidentId(authorityState.first,authorityState.second,app.installation.incidentSelection(g))
            val options=managedIncidentOptions(journal,authorityState.first,authorityState.second)
            val displayed=authorityState.third?.takeIf {it.active(g,preferred)}?.displayResidents()?:residents
            val demo=authorityState.third?.takeIf {it.active(g,preferred)}
            val projectedJournal=journal+demo?.scenarioJournal().orEmpty()
            val projectedOptions=if(demo!=null)managedIncidentOptions(projectedJournal,authorityState.first,authorityState.second)else options
            val clockOptions=projectedOptions.map {v->demo?.projectClock(v)?:v}
            val state=projectIncidentUi(g,displayed,care,projectedJournal,preferred,clockOptions)
            if(preferred==null&&state.selection.isNotEmpty()) {
                try {app.installation.selectIncident(g,state.selection)}
                catch(e: Exception) {message.value=e.localizedMessage;return@combine projectIncidentUi(g,residents,care,journal,"",options)}
            }
            state
        }
    }.stateIn(viewModelScope,SharingStarted.Eagerly,IncidentUiState())
    val residents = combine(group,incidentUi) { g,s -> if(g==s.groupId) s.residents else emptyList() }.stateIn(viewModelScope,SharingStarted.Eagerly,emptyList())
    private val realOwn=group.flatMapLatest {g->app.store.observe(g).map {rows->rows.find {it.originId==localId}}}.stateIn(viewModelScope,SharingStarted.Eagerly,null)
    val activeVenue=combine(group,incidentUi,app.cloud.venueChanges,deviceRecords) {g,state,_,records->app.cloud.venue(g)?.takeIf {it.active(g,state.selection)}?.withHomes(records)}.stateIn(viewModelScope,SharingStarted.Eagerly,null)
    val storedOwn=combine(realOwn,activeVenue) {own,venue->if(venue?.positionMode=="KYOTO")venue.displayResidents().find {it.originId==localId}?:own else own}.stateIn(viewModelScope,SharingStarted.Eagerly,null)
    val ownDemoHome=activeVenue.map {v->v?.takeIf {it.positionMode=="KYOTO"}?.placements?.find {it.deviceId==localId}?.home}.stateIn(viewModelScope,SharingStarted.Eagerly,null)
    fun hasCloudSession()=app.cloud.hasSession(group.value)
    private suspend fun personForOperation(g:String,device:String):Resident?=app.cloud.venue(g)?.takeIf {it.active(g,app.installation.incidentSelection(g))}?.displayResidents()?.find {it.originId==device}?:app.store.get(g,device)
    private suspend fun ownForOperation(g:String)=personForOperation(g,localId)
    private val placeSource=group.flatMapLatest {g->combine(app.store.observePlaces(g),app.quakes.observeRoster(g),app.quakes.observe(g)) {rows,roster,quakes->g to rows.map {it.copy(incidentId=canonicalIncidentId(roster,quakes,it.incidentId))}}}
    val places=combine(group,placeSource,activeVenue) {g,s,v->if(g!=s.first)emptyList() else if(v!=null) {val projected=v.placeRecords();projected+s.second.filter {(v.positionMode!="KYOTO"||it.kind!=PlaceKind.HOME)&&projected.none {p->p.eventId==it.eventId}}}else s.second}.stateIn(viewModelScope,SharingStarted.Eagerly,emptyList())
    private val positionSource = group.flatMapLatest { g -> app.store.observePositions(g).map { g to it } }
    val positions = combine(group, positionSource) { g, data -> if (g == data.first) data.second else emptyList() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    private val memoSource = group.flatMapLatest { g -> app.store.observeMemos(g).map { g to it } }
    val memos = combine(group, memoSource) { g, data -> if (g == data.first) data.second else emptyList() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val sharing = app.sharing
    val position = app.position
    val motion = app.motion.state
    fun motionCalibration() = app.motion.snapshot(group.value)
    val lab = app.lab
    val cloud = app.cloud
    val careEvents = combine(group,incidentUi) { g,s -> if(g==s.groupId) s.care else emptyList() }.stateIn(viewModelScope,SharingStarted.Eagerly,emptyList())
    val responseEvents=group.flatMapLatest {g->combine(app.responses.observe(g),app.quakes.observeRoster(g),app.quakes.observe(g)) {rows,roster,quakes->
        if(roster==null)emptyList() else responseHeads(roster,rows.map {it.copy(incidentId=canonicalIncidentId(roster,quakes,it.incidentId))})
    }}.stateIn(viewModelScope,SharingStarted.Eagerly,emptyList())
    val responseHistory=group.flatMapLatest {g->combine(app.responses.observe(g),app.quakes.observeRoster(g),app.quakes.observe(g)) {rows,roster,quakes->
        if(roster==null)emptyList() else rows.map {it.copy(incidentId=canonicalIncidentId(roster,quakes,it.incidentId))}
    }}.stateIn(viewModelScope,SharingStarted.Eagerly,emptyList())
    fun saveResponse(change:ResponseChange,replaces:List<String> = emptyList(),onSaved:()->Unit={}) {
        if(!saveOperations.begin("response"))return
        val g=group.value;val incident=if(change is ResponseChange.Availability)null else incidentUi.value.takeIf {it.groupId==g}?.current?.start?.eventId
        viewModelScope.launch {
            saveOperations.complete("response","対応記録",persist={
                val eventId=if(replaces.isEmpty())when(change){is ResponseChange.Report->change.reportId;is ResponseChange.Material->change.requestId;else->java.util.UUID.randomUUID().toString()}else java.util.UUID.randomUUID().toString()
                app.responses.append(ResponseEvent(g,eventId,localId,1,System.currentTimeMillis(),incident,change,replaces))
            },publish={app.recordsChanged();onSaved()})
        }
    }
    fun startResponse(target:ResponseTarget) {
        if(!saveOperations.begin("response"))return
        val g=group.value;val incident=incidentUi.value.takeIf {it.groupId==g}?.current?.start?.eventId
        viewModelScope.launch {saveOperations.complete("response","対応記録",persist={
            app.responses.startWork(g,checkNotNull(incident),localId,target,System.currentTimeMillis())
        },publish={app.recordsChanged()})}
    }
    suspend fun responseCsv(language:String="ja",allowedResidentIds:Set<String>?=null):String {
        val g=group.value
        val incident=checkNotNull(incidentUi.value.takeIf {it.groupId==g}?.current?.start?.eventId)
        return app.responses.exportCsv(g,incident,language=language,allowedResidentIds=allowedResidentIds)
    }
    fun cancelEarthquakeReport(reportId:String) {
        val g=group.value
        viewModelScope.launch {
            try {
                val report=app.quakes.events(g).first {it.eventId==reportId&&it.action==QuakeAction.REPORT}
                app.quakes.command(QuakeEvent(g,java.util.UUID.randomUUID().toString(),localId,System.currentTimeMillis(),QuakeAction.CANCEL,report.training,targetId=reportId))
                app.recordsChanged();message.value="地震報告を取り消しました"
            }catch(e:CancellationException){throw e}catch(e:Exception){message.value=e.localizedMessage}
        }
    }
    val maps = app.offlineMaps
    // Point queries take an explicit intensity; the map's approved pre-event default is 6.2.
    suspend fun assessBuildingsAt(latitude: Double, longitude: Double, instrumentalIntensity: Double) =
        app.buildingAssessments.atPoint(latitude, longitude, instrumentalIntensity)
    val saving = MutableStateFlow(false)
    private val saveOperations = SaveOperations(saving)
    val saveStates = saveOperations.states
    fun careSaveKey(subject: String) = "care:${group.value}:${incidentUi.value.selection}:$subject"
    val pickedPoint = MutableStateFlow<MapPoint?>(null)
    val openSafety = MutableStateFlow(false)
    var notificationPrompted: Boolean
        get() = app.installation.notificationPrompted
        set(value) { app.installation.notificationPrompted = value }
    fun saveProfile(groupId: String, name: String, lat: String, lon: String, floor: String, room: String, homeAddress:String="",homeReference:BuildingLocationReference?=null) {
        if (!saveOperations.begin("profile")) return
        viewModelScope.launch {
            val g = groupId.trim()
            val changingGroup = group.value != g
            saveOperations.complete("profile", "登録情報", persist = {
                val venue=activeVenue.value?.takeIf {it.groupId==g&&it.positionMode=="KYOTO"}
                if(venue!=null) {
                    saveDemoHome(venue.sessionId,lat.toDouble(),lon.toDouble(),homeAddress,floor.trim(),room.trim(),displayName=name.trim(),reference=homeReference)
                    return@complete
                }
                val candidate = Resident(g, localId, 1, 0, name.trim(), lat.toDoubleOrNull() ?: error("緯度を数値で入力してください"),
                    lon.toDoubleOrNull() ?: error("経度を数値で入力してください"), floor.trim(), room.trim())
                candidate.validate()
                if (changingGroup) { position.stop(); sharing.stop(); lab.stop(text = "グループを変更したため停止しました") }
                app.store.updateOwnWithHome(candidate,homeReference?:BuildingLocationReference(homeAddress.trim().ifBlank {"地図で選択した住居"}))
            }, publish = {
                if (changingGroup) { app.installation.setGroup(g); saveOperations.clearOtherResults() }
                pickedPoint.value = null
                app.recordsChanged()
            })
        }
    }

    private suspend fun saveDemoHome(session:String,lat:Double,lon:Double,address:String,floor:String,room:String,chosen:DemoHome?=null,displayName:String?=null,reference:BuildingLocationReference?=null) {
        val old=ownDemoHome.value
        val candidates=app.buildingAssessments.atPoint(lat,lon,6.2)
        val lookup=io.github.nevaseme.plandmk1.buildings.CityAddresses(getApplication())
        val plateau=reference?.plateauId?:candidates.singleOrNull()?.building?.id
        val same=old!=null&&kotlin.math.abs(old.latitude-lat)<1e-6&&kotlin.math.abs(old.longitude-lon)<1e-6
        val buildingId=plateau?.removePrefix("bldg_")?.takeIf {recordIdPattern.matches(it)}?:if(same)old!!.buildingId else java.util.UUID.randomUUID().toString()
        val h=chosen?:DemoHome(buildingId,address.trim().ifBlank {if(same)old!!.address else lookup.nearestTown(lat,lon)?.address?.plus("付近")?:"地図で選択した住居"},lat,lon,plateau,floor,room,displayName?:old?.displayName,reference?.searchReference)
        app.deviceRecords.append(DeviceRecord(group.value,java.util.UUID.randomUUID().toString(),localId,1,System.currentTimeMillis(),"DEMO_HOME",sessionId=session,home=h))
    }
    fun setSafety(safety: Safety, memo: String = "") {
        if (!saveOperations.begin("safety")) return
        val targetGroup=group.value;val incident=incidentUi.value.takeIf { it.groupId==targetGroup }?.selection.orEmpty()
        viewModelScope.launch {
            saveOperations.complete("safety", if (safety == Safety.SOS) "SOS" else "無事", persist = {
                val authorName=ownForOperation(targetGroup)?.name ?: app.quakes.roster(targetGroup)?.members?.find {localId in it.deviceIds}?.activityName
                check(!authorName.isNullOrBlank()) { "先にグループへ参加してください" }
                app.store.answerWithMemo(IncidentEvent(targetGroup,java.util.UUID.randomUUID().toString(),incident,localId,authorName,1,System.currentTimeMillis(),IncidentKind.ANSWER,safety=safety), if (safety == Safety.SOS) memo else "",app.cloud.venue(targetGroup))
            }, publish = { position.refresh(); app.recordsChanged() })
        }
    }

    fun saveSosMemo(answerId: String, text: String) {
        if (!saveOperations.begin("safety")) return
        val g = group.value
        val incident = incidentUi.value.takeIf { it.groupId == g }?.selection.orEmpty()
        viewModelScope.launch {
            saveOperations.complete("safety", "SOSメモ", persist = {
                app.store.saveSosMemo(g, incident, localId, answerId, text, System.currentTimeMillis())
            }, publish = { app.recordsChanged() })
        }
    }

    fun selectIncident(id: String) {
        if(saving.value)return
        try { app.installation.selectIncident(group.value,id);saveOperations.clearOtherResults() }
        catch(e: Exception) { message.value=e.localizedMessage }
    }
    fun startIncident(title: String, area: String, training: Boolean, administratorStart:Boolean=false) {
        if(!saveOperations.begin("incident"))return
        val g=group.value;val id=java.util.UUID.randomUUID().toString()
        var selected:String?=null
        viewModelScope.launch { saveOperations.complete("incident",if(training) "訓練開始" else "地震報告",persist={
            val own=ownForOperation(g)
            // Re-read durable authority when saving; a stale UI must never grant training permission.
            val participants=app.quakes.roster(g)
            if(training||administratorStart) {
                checkNotNull(participants) { "訓練を開始するには管理者の参加者設定が必要です" }
                check(participants.memberForDevice(localId).role==MemberRole.ADMIN) { "訓練を開始できるのは管理者だけです" }
            }
            if(participants!=null) {
                val action=if(administratorStart)QuakeAction.ADMIN_START else if(training) QuakeAction.TRAINING_START else QuakeAction.REPORT
                val states=app.quakes.command(QuakeEvent(g,id,localId,System.currentTimeMillis(),action,training,title=title.trim(),area=area.trim()))
                selected=states.lastOrNull {!it.closed&&it.formation.training==training}?.formation?.incidentId
            }else{
                app.store.mergeIncident(IncidentEvent(g,id,id,localId,checkNotNull(own).name,1,System.currentTimeMillis(),IncidentKind.START,title.trim(),area.trim(),training));selected=id
            }
        },publish={ selected?.let {app.installation.selectIncident(g,it)};saveOperations.clearOtherResults();app.recordsChanged() }) }
    }
    fun endIncident() {
        if(!saveOperations.begin("incident"))return
        val g=group.value;val current=incidentUi.value.takeIf { it.groupId==g }?.current
        viewModelScope.launch { saveOperations.complete("incident","安否確認の終了",persist={
            val own=ownForOperation(g)
            val selected=checkNotNull(current) { "開始中の災害を選んでください" }
            if(app.quakes.roster(g)!=null)app.quakes.command(QuakeEvent(g,java.util.UUID.randomUUID().toString(),localId,System.currentTimeMillis(),QuakeAction.END,selected.start.training,targetId=selected.start.eventId))
            else app.store.appendIncident(IncidentEvent(g,java.util.UUID.randomUUID().toString(),selected.start.eventId,localId,checkNotNull(own).name,1,System.currentTimeMillis(),IncidentKind.END))
        },publish={ app.recordsChanged() }) }
    }

    fun startSharing() {
        if (saving.value) return
        val targetGroup = group.value
        viewModelScope.launch {
            // Membership is persisted before the UI's roster flow catches up on joining.
            val own = ownForOperation(targetGroup)
            val member = app.quakes.roster(targetGroup)?.members?.find { localId in it.deviceIds }
            if (group.value != targetGroup) return@launch
            val credentials = cloud.state.value
            if (own == null && !(credentials.groupId == targetGroup && credentials.nearbyReady && member != null)) {
                message.value = "先に設定で自分の情報を登録してください"
            } else { lab.stop(); sharing.start(targetGroup, own?.name ?: member!!.activityName) }
        }
    }
    fun startLab(url: String, token: String) {
        if (saving.value) return
        if (residents.value.none { it.originId == localId }) { message.value = "先に自分の情報を登録してください"; return }
        sharing.stop(); lab.start(url.trim(), token.trim(), group.value)
    }
    fun saveCare(subjectId: String, action: CareAction, note: String, supersedes: String? = null, onSaved: (CareEvent) -> Unit = {}) {
        val key = careSaveKey(subjectId)
        if (!saveOperations.begin(key)) return
        val targetGroup = group.value
        val incident=incidentUi.value.takeIf { it.groupId==targetGroup }?.selection.orEmpty()
        viewModelScope.launch {
            var saved: CareEvent? = null
            saveOperations.complete(key, "対応記録", persist = {
                val own = checkNotNull(ownForOperation(targetGroup)) { "先に自分の情報を登録してください" }
                checkNotNull(personForOperation(targetGroup, subjectId)) { "対象住民が見つかりません" }
                saved = app.store.appendIncident(IncidentEvent(targetGroup,java.util.UUID.randomUUID().toString(),incident,localId,own.name,1,System.currentTimeMillis(),IncidentKind.CARE,subjectId=subjectId,action=action,note=note.trim(),supersedes=supersedes)).care()
            }, publish = { saved?.let(onSaved); app.recordsChanged() })
        }
    }

    fun revokeRescues(subjectId:String) {
        val key=careSaveKey(subjectId)
        if(!saveOperations.begin(key))return
        val targetGroup=group.value
        val incident=incidentUi.value.takeIf {it.groupId==targetGroup}?.selection.orEmpty()
        viewModelScope.launch {
            saveOperations.complete(key,"救助の取り消し",persist={
                val own=checkNotNull(ownForOperation(targetGroup)) {"先に自分の情報を登録してください"}
                checkNotNull(personForOperation(targetGroup,subjectId)) {"対象住民が見つかりません"}
                app.store.revokeRescues(targetGroup,incident,subjectId,localId,own.name,System.currentTimeMillis())
            },publish={app.recordsChanged()})
        }
    }

    fun placeSaveKey(kind: PlaceKind,target: String="")="place:${group.value}:${if(kind==PlaceKind.CARE)incidentUi.value.selection else ""}:$kind:$target"
    private fun savePlace(kind: PlaceKind,target: String="",make: (PlaceEvent)->PlaceEvent,onSaved: (PlaceEvent)->Unit={}) {
        val key=placeSaveKey(kind,target);if(!saveOperations.begin(key))return
        val g=group.value;val incident=incidentUi.value.takeIf {it.groupId==g}?.selection.orEmpty()
        viewModelScope.launch {
            var saved: PlaceEvent?=null
            saveOperations.complete(key,if(kind==PlaceKind.CARE)"建物の対応記録" else "建物・住戸の登録",persist={
                val own=checkNotNull(ownForOperation(g)) {"先に自分の情報を登録してください"}
                val base=PlaceEvent(g,java.util.UUID.randomUUID().toString(),localId,own.name,1,System.currentTimeMillis(),kind,incidentId=if(kind==PlaceKind.CARE)incident else null)
                saved=if(kind==PlaceKind.HOME)app.store.assignOwnHome(make(base))else app.store.appendPlace(make(base))
            },publish={saved?.let(onSaved);app.recordsChanged()})
        }
    }
    fun registerBuilding(buildingId: String?,label: String,lat: String,lon: String,locationReference:BuildingLocationReference?=null,onSaved: (PlaceEvent)->Unit={})=savePlace(PlaceKind.BUILDING,buildingId.orEmpty(),{
        it.copy(buildingId=buildingId?:it.eventId,label=label.trim(),latitude=lat.toDoubleOrNull()?:error("緯度を数値で入力してください"),longitude=lon.toDoubleOrNull()?:error("経度を数値で入力してください"),locationReference=locationReference)
    },onSaved)
    fun registerUnit(buildingId: String,label: String,unitId: String?=null,onSaved: (PlaceEvent)->Unit={})=savePlace(PlaceKind.UNIT,buildingId,{it.copy(buildingId=buildingId,unitId=unitId?:it.eventId,label=label.trim())},onSaved)
    fun assignHome(buildingId: String?,unitId: String?=null) {
        val venue=activeVenue.value?.takeIf {it.positionMode=="KYOTO"}
        if(venue==null){savePlace(PlaceKind.HOME,make={it.copy(buildingId=buildingId,unitId=unitId)});return}
        if(buildingId==null){message.value="訓練の住居は別の建物を選んで変更してください";return}
        val building=placeBuildings(places.value).find {it.buildingId==buildingId}?:return
        val same=ownDemoHome.value?.buildingId==buildingId
        val home=DemoHome(buildingId,building.locationReference?.address?:building.label,building.latitude!!,building.longitude!!,building.locationReference?.plateauId,if(same)ownDemoHome.value?.floor.orEmpty() else "",placeUnits(places.value,buildingId).find {it.unitId==unitId}?.label.orEmpty(),ownDemoHome.value?.displayName,building.locationReference?.searchReference)
        if(!saveOperations.begin("place:HOME:"))return
        viewModelScope.launch {saveOperations.complete("place:HOME:","訓練住居",persist={saveDemoHome(venue.sessionId,home.latitude,home.longitude,home.address,home.floor,home.room,home)},publish={app.recordsChanged()})}
    }
    fun saveBuildingCare(buildingId: String,action: BuildingAction,scope: String,unresolved: String,note: String,supersedes: String?,onSaved: (PlaceEvent)->Unit={})=savePlace(PlaceKind.CARE,buildingId,{
        it.copy(buildingId=buildingId,action=action,scope=scope.trim(),unresolved=unresolved.trim(),note=note.trim(),supersedes=supersedes)
    },onSaved)
    fun stopSharing() = sharing.stop()
}

