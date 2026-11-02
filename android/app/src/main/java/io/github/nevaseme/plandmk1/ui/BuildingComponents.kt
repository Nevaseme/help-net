package io.github.nevaseme.plandmk1.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import io.github.nevaseme.plandmk1.MapPoint
import io.github.nevaseme.plandmk1.buildings.*
import kotlinx.coroutines.launch
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nevaseme.plandmk1.PlanViewModel
import io.github.nevaseme.plandmk1.core.*

fun BuildingAction.label()=when(this) {BuildingAction.CHECKING->"確認中";BuildingAction.NEEDS_HELP->"要対応";BuildingAction.COMPLETED->"対応完了の記録";BuildingAction.RECHECK->"再確認が必要";BuildingAction.REVOKED->"記録を撤回"}
fun buildingSummary(rows: List<PlaceEvent>,buildingId: String,incident: String)=currentBuildingCare(buildingCare(rows,buildingId,incident)).takeIf {it.isNotEmpty()}?.joinToString(" / "){it.action!!.label()}?:"建物の対応記録なし"
fun homeLabel(rows: List<PlaceEvent>,person: String): String {
    val home=homeFor(rows,person)?:return "建物・住戸は未登録"
    if(home.buildingId==null)return "建物の関連付けなし"
    val valid=validHome(rows,person)?:return "建物・住戸の詳細を受信待ち"
    val building=placeBuildings(rows).first {it.buildingId==valid.buildingId}
    return "建物名：${building.displayLabel()}\n部屋・住戸："+(placeUnits(rows,valid.buildingId).find {it.unitId==valid.unitId}?.label?:"未登録")
}
@Composable fun BuildingCard(building: PlaceEvent,rows: List<PlaceEvent>,incident: String,residents: List<Resident>,onClick: ()->Unit) {
    val members=residents.filter {validHome(rows,it.originId)?.buildingId==building.buildingId}
    OutlinedCard(onClick=onClick,modifier=Modifier.fillMaxWidth()) {Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
        NamedEntity("建物名", building.displayLabel())
        building.locationReference?.let {Text(it.address,style=MaterialTheme.typography.bodyMedium)}
        BuildingResponse(rows,building.buildingId!!,incident)
        Text("住民の安否：SOS ${members.count {it.safety==Safety.SOS}}人 / 無事 ${members.count {it.safety==Safety.SAFE}}人 / 未回答 ${members.count {it.safety==Safety.UNREPORTED}}人")
        Text("詳細・対応履歴を見る",style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.primary)
    }}
}
@Composable fun BuildingsScreen(model: PlanViewModel,onProfile: ()->Unit = {},onSelect: (String)->Unit) {
    val rows by model.places.collectAsStateWithLifecycle();val residents by model.residents.collectAsStateWithLifecycle()
    val incident by model.incidentUi.collectAsStateWithLifecycle();val saving by model.saving.collectAsStateWithLifecycle()
    val group by model.group.collectAsStateWithLifecycle();val own=residents.find {it.originId==model.localId}
    var create by rememberSaveable(group){mutableStateOf(false)}
    var search by rememberSaveable(group){mutableStateOf("")}
    val cloud by model.cloud.state.collectAsStateWithLifecycle()
    val context=LocalContext.current;val lookup=remember {CityAddresses(context.applicationContext)};val searchScope=rememberCoroutineScope()
    var suggestions by remember(group){mutableStateOf<List<AddressCandidate>>(emptyList())}
    var searchStatus by remember(group){mutableStateOf("")}
    var initialCandidate by remember(group){mutableStateOf<AddressCandidate?>(null)}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item {Text("登録済みの建物・部屋",style=MaterialTheme.typography.headlineSmall)}
        if(own==null)item {Button(onClick=onProfile){Text("先に自分の情報を登録")}}
        item {Text("自分の住まい\n${homeLabel(rows,model.localId)}")}
        item {Button(enabled=own!=null&&!saving,onClick={initialCandidate=null;create=true}){Text("住所・地図から建物を追加")}}
        item {OperationFeedback(model,model.placeSaveKey(PlaceKind.BUILDING))}
        if(homeFor(rows,model.localId)?.buildingId!=null)item {TextButton(enabled=!saving,onClick={model.assignHome(null)}){Text("自分の建物の関連付けを解除")}}
        item {OperationFeedback(model,model.placeSaveKey(PlaceKind.HOME))}
        item {AddressSearchField(search,{search=it},{},Modifier.fillMaxWidth(),label="登録済みの建物を絞り込む")}
        if(placeBuildings(rows).isNotEmpty())item {Text("登録済みの建物",style=MaterialTheme.typography.titleSmall)}
        if(placeBuildings(rows).isEmpty()&&search.isBlank())item {Text("登録済みの建物はありません。住所・地図から追加できます。")}
        if(search.isNotBlank()&&placeBuildings(rows).isNotEmpty()&&placeBuildings(rows).none{normalizeAddress(it.label+it.locationReference?.address.orEmpty()).contains(normalizeAddress(search))})item {Text("一致する登録済みの建物はありません")}

        items(placeBuildings(rows).filter {normalizeAddress(it.label+it.locationReference?.address.orEmpty()).contains(normalizeAddress(search))},key={it.buildingId!!}) {b->BuildingCard(b,rows,incident.selection,residents){onSelect(b.buildingId!!)}}
    }
    if(create)BuildingForm(model,null,own,initialCandidate){create=false}
}
@Composable private fun BuildingForm(model: PlanViewModel,building: PlaceEvent?,own: Resident?,initialCandidate:AddressCandidate?=null,close: ()->Unit) {
    val saving by model.saving.collectAsStateWithLifecycle()
    val cloud by model.cloud.state.collectAsStateWithLifecycle()
    val group by model.group.collectAsStateWithLifecycle()
    val context=LocalContext.current;val repo=remember {CityAddresses(context.applicationContext)};val scope=rememberCoroutineScope()
    var label by rememberSaveable {mutableStateOf(building?.displayLabel()?:initialCandidate?.let {if(it.precision=="poi")it.address else "自宅"}.orEmpty())}
    var address by rememberSaveable {mutableStateOf(building?.locationReference?.address?:initialCandidate?.takeUnless {it.precision=="poi"}?.address.orEmpty())}
    var lat by rememberSaveable {mutableStateOf(building?.latitude?:initialCandidate?.latitude?:own?.latitude?:35.0145)}
    var lon by rememberSaveable {mutableStateOf(building?.longitude?:initialCandidate?.longitude?:own?.longitude?:135.7625)}
    var plateauId by rememberSaveable {mutableStateOf(building?.locationReference?.plateauId)}
    var confirmed by rememberSaveable {mutableStateOf(building!=null)}
    var picking by rememberSaveable {mutableStateOf(building==null)}
    var searching by remember {mutableStateOf(false)};var message by remember {mutableStateOf("")}
    var searchRef by remember {mutableStateOf(building?.locationReference?.searchReference?:initialCandidate?.reference(initialCandidate.address))}
    var candidates by remember {mutableStateOf<List<AddressCandidate>>(emptyList())}
    var selectedCandidate by remember {mutableStateOf(initialCandidate)}
    AlertDialog(onDismissRequest={if(!saving)close()},title={Text(if(building==null)"建物を登録" else "建物情報を訂正")},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(label,{label=it.take(80)},label={Text("建物名")},enabled=!saving,singleLine=true)
        AddressSearchField(address,{address=it.take(240);candidates=emptyList();message=""},{picking=true},label="登録する住所・建物名",enabled=!saving)
        TextButton(enabled=!saving&&!searching&&address.isNotBlank(),onClick={scope.launch {searching=true;message="";try {val found=repo.candidates(address,cloud.municipality?.code);if(found.isEmpty())message="場所が見つかりません。住所を詳しく入力してください" else if(found.size==1){val c=found.single();lat=c.latitude;lon=c.longitude;confirmed=false;plateauId=null;searchRef=c.reference(address);selectedCandidate=c;picking=true}else candidates=found}catch(e:Exception){message=e.message.orEmpty()}finally{searching=false}}}){Text(if(searching)"検索中…" else "住所を検索")}

        candidates.forEach {c->TextButton(onClick={searchRef=c.reference(address);selectedCandidate=c;address=c.address;lat=c.latitude;lon=c.longitude;confirmed=false;plateauId=null;candidates=emptyList();picking=true}){Text(c.address+if(c.precision=="town")" 付近" else "")}}
        if(message.isNotEmpty())Text(message)
        OutlinedButton(enabled=!saving,onClick={picking=true}){Text(if(confirmed)"建物の位置を選び直す" else "地図で建物を選ぶ")}
        if(confirmed)Text("建物の位置を選択済み",style=MaterialTheme.typography.bodyMedium)
        OperationFeedback(model,model.placeSaveKey(PlaceKind.BUILDING,building?.buildingId.orEmpty()))
    }},confirmButton={TextButton(enabled=!saving&&confirmed&&label.isNotBlank()&&address.isNotBlank(),onClick={model.registerBuilding(building?.buildingId,label,lat.toString(),lon.toString(),BuildingLocationReference(address.trim(),plateauId,if(plateauId!=null)"plateau-26100-kyoto-shi-2025" else null,searchRef)){close()}}){Text("建物情報を保存")}},dismissButton={TextButton(enabled=!saving,onClick=close){Text("閉じる")}})
    if(picking)BuildingLocationPicker(MapPoint(lat,lon),{point,id->lat=point.latitude;lon=point.longitude;plateauId=id;confirmed=true;picking=false},{picking=false},initialQuery=address.ifBlank{label},initialCandidate=selectedCandidate,onHomeConfirm={point,ref->lat=point.latitude;lon=point.longitude;plateauId=ref.plateauId;address=ref.address;searchRef=ref.searchReference;confirmed=true;picking=false})
}
@Composable fun BuildingDetail(model: PlanViewModel,buildingId: String,onResident: (Resident)->Unit,onClose: ()->Unit,registration: Boolean = false) {
    val care by model.careEvents.collectAsStateWithLifecycle()
    val rows by model.places.collectAsStateWithLifecycle();val residents by model.residents.collectAsStateWithLifecycle()
    val incident by model.incidentUi.collectAsStateWithLifecycle();val saving by model.saving.collectAsStateWithLifecycle()
    val own=residents.find {it.originId==model.localId};val building=placeBuildings(rows).find {it.buildingId==buildingId}?:return
    var editing by rememberSaveable {mutableStateOf(false)};var newUnit by rememberSaveable {mutableStateOf(false)}
    var unitName by rememberSaveable {mutableStateOf("")}
    var editingUnit by rememberSaveable {mutableStateOf<String?>(null)}
    AlertDialog(onDismissRequest={if(!saving)onClose()},title={NamedEntity("建物名", building.displayLabel())},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        BuildingResponse(rows,buildingId,incident.selection)
        building.locationReference?.let {Text(it.address)}
        if(registration) {
        if(building.authorId==model.localId)TextButton(enabled=!saving,onClick={editing=true}){Text("建物情報を訂正")}
        OperationFeedback(model,model.placeSaveKey(PlaceKind.BUILDING,buildingId))
        Text("自分の住まい\n${homeLabel(rows,model.localId)}")
        TextButton(enabled=own!=null&&!saving,onClick={model.assignHome(buildingId)}){Text("この建物を自分の住居に設定（住戸未指定）")}
        Text("住戸",style=MaterialTheme.typography.titleMedium)
        placeUnits(rows,buildingId).forEach {unit->
            TextButton(enabled=own!=null&&!saving,onClick={model.assignHome(buildingId,unit.unitId)}){Text("${unit.label} を自分の住戸に設定")}
            if(unit.authorId==model.localId)TextButton(enabled=!saving,onClick={editingUnit=unit.unitId;unitName=unit.label;newUnit=true}){Text("${unit.label} の住戸名を訂正")}
        }
        TextButton(enabled=own!=null&&!saving,onClick={editingUnit=null;unitName="";newUnit=true}){Text("住戸を追加")}
        OperationFeedback(model,model.placeSaveKey(PlaceKind.UNIT,buildingId))
        OperationFeedback(model,model.placeSaveKey(PlaceKind.HOME))
        }
        HorizontalDivider()
        Text("この建物に登録されている人",style=MaterialTheme.typography.titleMedium)
        BuildingPeople(rows,buildingId,residents,model.localId,onResident,care)
        key(incident.selection,buildingId) {BuildingCareEditor(model,buildingId,rows,incident.selection,own!=null&&incident.canEdit,saving)}
    }},confirmButton={TextButton(enabled=!saving,onClick=onClose){Text("閉じる")}})
    if(editing)BuildingForm(model,building,own){editing=false}
    if(newUnit)AlertDialog(onDismissRequest={if(!saving)newUnit=false},title={Text(if(editingUnit==null)"住戸を追加" else "住戸名を訂正")},text={Column {
        OutlinedTextField(unitName,{unitName=it.take(80)},label={Text("住戸名（例：201号室・戸建て）")},enabled=!saving)
        OperationFeedback(model,model.placeSaveKey(PlaceKind.UNIT,buildingId))
    }},confirmButton={TextButton(enabled=!saving,onClick={model.registerUnit(buildingId,unitName,editingUnit){newUnit=false;unitName=""}}){Text("住戸を保存")}},dismissButton={TextButton(enabled=!saving,onClick={newUnit=false}){Text("閉じる")}})
}
@Composable private fun BuildingCareEditor(model: PlanViewModel,buildingId: String,rows: List<PlaceEvent>,incident: String,canEdit: Boolean,saving: Boolean) {
    val people by model.residents.collectAsStateWithLifecycle()
    val care by model.careEvents.collectAsStateWithLifecycle()
    var action by rememberSaveable {mutableStateOf(BuildingAction.CHECKING)}
    var scope by rememberSaveable {mutableStateOf("")};var unresolved by rememberSaveable {mutableStateOf("")};var note by rememberSaveable {mutableStateOf("")};var supersedes by rememberSaveable {mutableStateOf<String?>(null)}
    val history=buildingCare(rows,buildingId,incident).sortedWith(compareByDescending<PlaceEvent>{it.createdAt}.thenBy {it.eventId})
    val heads=currentBuildingCare(history).map {it.eventId}.toSet()
    HorizontalDivider();Text("建物への対応履歴",style=MaterialTheme.typography.titleMedium)
    if(history.isEmpty())Text("この災害の建物対応記録はありません")
    history.forEach {e->
        Text(e.action!!.label()+if(e.eventId !in heads)"（訂正前）" else "",style=MaterialTheme.typography.titleSmall)
        Text("記録した人：${publicRecordAuthor(e.authorId,people,care)}\n記録日時：${formatTime(e.createdAt)}\n確認範囲：${e.scope}\n未確認事項：${e.unresolved}\n${e.note}")
        if(canEdit&&e.authorId==model.localId&&e.eventId in heads)TextButton(enabled=!saving,onClick={supersedes=e.eventId;action=e.action;scope=e.scope;unresolved=e.unresolved;note=e.note}){Text("この建物記録を訂正")}
        HorizontalDivider()
    }
    if(canEdit) {
        Text(if(supersedes==null)"建物の対応を記録" else "自分の建物記録を訂正")
        if(supersedes!=null)TextButton(enabled=!saving,onClick={supersedes=null;action=BuildingAction.CHECKING;scope="";unresolved="";note=""}){Text("新しい建物記録に戻す")}
        BuildingAction.entries.filter {it!=BuildingAction.REVOKED||supersedes!=null}.forEach {a->FilterChip(enabled=!saving,selected=a==action,onClick={action=a},label={Text(a.label())})}
        OutlinedTextField(scope,{scope=it.take(240)},label={Text("確認した範囲（必須）")},enabled=!saving)
        OutlinedTextField(unresolved,{unresolved=it.take(240)},label={Text("未確認事項（ない場合は「なし」）")},enabled=!saving)
        OutlinedTextField(note,{note=it.take(500)},label={Text("建物の対応内容・訂正理由")},enabled=!saving,minLines=2)
        Button(enabled=!saving,onClick={model.saveBuildingCare(buildingId,action,scope,unresolved,note,supersedes){supersedes=it.eventId}}){Text(if(saving)"保存しています…" else "建物の対応記録を保存")}
        OperationFeedback(model,model.placeSaveKey(PlaceKind.CARE,buildingId))
    }else Text("対応記録は閲覧のみ")
}
