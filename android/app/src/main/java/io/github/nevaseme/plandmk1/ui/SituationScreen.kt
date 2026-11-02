package io.github.nevaseme.plandmk1.ui

import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.border
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.nevaseme.plandmk1.MapPoint
import io.github.nevaseme.plandmk1.R
import io.github.nevaseme.plandmk1.core.SOS_MEMO_LIMIT
import io.github.nevaseme.plandmk1.buildings.BuildingMapModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun StateSymbol(state: MapSafety, modifier: Modifier = Modifier, description: String? = state.label) {
    Canvas(modifier.size(36.dp).semantics { description?.let { contentDescription = it } }) {
        drawState(drawContext.canvas.nativeCanvas, android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG), state,
            size.width / 2, size.height / 2, size.minDimension / 2 - 3.dp.toPx())
    }
}

fun positionDescription(person: MapPerson, now: Long): String = when {
    person.acquiredAt == null || person.point == null -> "位置は未受信"
    else -> "${SimpleDateFormat("HH:mm", Locale.JAPAN).format(Date(person.acquiredAt))}取得・${((now - person.acquiredAt) / 60_000).coerceAtLeast(0)}分前" + (person.accuracy?.let { "・精度 約${it}m" } ?: "")
}

@Composable fun safetyText(state:MapSafety)=if(state==MapSafety.SOS)"SOS" else uiText(when(state){MapSafety.SOS->"my_sos";MapSafety.UNANSWERED->"no_response";MapSafety.SAFE->"safe";MapSafety.RESCUED->"rescued"})
@Composable fun personText(person:MapPerson)=when {person.demo->person.name;person.state==MapSafety.SAFE->uiText("safe_person");person.state==MapSafety.RESCUED->uiText("rescued_person");!person.identityVisible->uiText("no_response");person.name.isBlank()->uiText("registered_resident");else->person.name}
@Composable private fun positionText(person:MapPerson,now:Long):String=if(person.fictional)if(LocalUiLanguage.current=="en")"Fictional resident" else "架空住民" else if(person.demo)if(LocalUiLanguage.current=="en")"Demo position" else "会場デモの位置" else if(person.point==null||person.acquiredAt==null)uiText("position_unreceived") else uiText("position_time",SimpleDateFormat("HH:mm",Locale.getDefault()).format(Date(person.acquiredAt)),((now-person.acquiredAt)/60_000).coerceAtLeast(0))+(person.accuracy?.let {" · "+uiText("accuracy",it)}?:"")

@OptIn(ExperimentalMaterial3Api::class,ExperimentalLayoutApi::class)
@Composable
fun SituationScreen(
    people: List<MapPerson>, homes: List<MapHome>, center: MapPoint, now: Long,
    preview: Boolean = false, canRescue: Boolean = true,
    canUndo: (MapPerson) -> Boolean = { true },
    onRescue: (MapPerson) -> Unit = {}, onUndo: (MapPerson) -> Unit = {},
    history: @Composable (MapPerson) -> Unit = {},
    feedback: @Composable (MapPerson) -> Unit = {},
    openList: Boolean = false, onListClosed: () -> Unit = {},
    listOnly: Boolean = false,
    assessmentIntensity: Double = BuildingMapModel.DEFAULT_INTENSITY,
    onAddReport:((MapPoint)->Unit)?=null,
    reports:List<MapReport> = emptyList(),onReport:(String)->Unit = {},
    statisticsLabel:String=uiText("group_label"), initialZoom:Double=16.8,
) {
    var choosingReport by rememberSaveable {mutableStateOf(false)}
    var showSafe by rememberSaveable { mutableStateOf(true) }
    var tsunami by rememberSaveable { mutableStateOf(false) }
    var tsunamiStatus by remember {mutableStateOf("津波の高さを入力してください")}
    var tsunamiHeight by rememberSaveable {mutableStateOf("")}
    val keyboard=LocalSoftwareKeyboardController.current
    var tsunamiResult by remember { mutableStateOf<io.github.nevaseme.plandmk1.tsunami.TsunamiComputedResult?>(null) }
    var sheet by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var homeId by rememberSaveable { mutableStateOf<String?>(null) }
    var areaIds by rememberSaveable { mutableStateOf<List<String>?>(null) }
    var listFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var extraHome by remember { mutableStateOf<MapHome?>(null) }
    var failed by remember { mutableStateOf(false) }
    val camera = remember { SituationCamera() }
    val unknown = people.filterNot { it.hasPosition(now) }
    LaunchedEffect(openList) { if (openList) { sheet = "all"; listFilter = null; areaIds = null } }
    val close = { sheet = null; onListClosed() }
    val visible=LocalMapVisible.current
    LaunchedEffect(visible){if(!visible){sheet=null;choosingReport=false}}
    Box(Modifier.fillMaxSize()) {
    if (listOnly) {
        val rows = filteredPeople(people,listFilter,now)
        LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface),
            contentPadding = PaddingValues(horizontal=18.dp,vertical=16.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
            item {
                Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLow).padding(horizontal=0.dp,vertical=8.dp)) {
                    Text(uiText("group_safety"),Modifier.fillMaxWidth().clickable {sheet="statistics"}.heightIn(min=48.dp).padding(vertical=10.dp),fontSize=15.sp,lineHeight=22.sp,fontWeight=FontWeight.Bold)
                    Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    MapSafety.entries.chunked(if(LocalDensity.current.fontScale>=1.5f)2 else 4).forEach { states ->
                    Row(Modifier.fillMaxWidth().padding(bottom=12.dp),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                        states.forEach {state->
                            Column(Modifier.weight(1f),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(8.dp)) {
                                Text(safetyText(state),fontSize=12.sp,lineHeight=18.sp,textAlign=androidx.compose.ui.text.style.TextAlign.Center)
                                Text(people.count {it.state==state}.toString(),fontSize=22.sp,lineHeight=30.sp,fontWeight=FontWeight.Bold)
                            }
                        }
                    }
                    }
                    }
                }
                Text(uiText("people_heading"),Modifier.padding(top=18.dp,bottom=4.dp),fontSize=24.sp,lineHeight=36.sp,fontWeight=FontWeight.Bold)
                Text(uiText("people_subtitle"),Modifier.padding(bottom=14.dp),fontSize=13.sp,lineHeight=21.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                SituationFilters(people,listFilter,now){listFilter=it}
            }
            items(rows, key = { it.id }) { person -> PersonRow(person, now, false) { selectedId = person.id; sheet = "person" } }
            if (rows.isEmpty()) item { Text(uiText("no_people"), Modifier.padding(vertical = 20.dp)) }
        }
    } else Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        FlowRow(Modifier.fillMaxWidth().heightIn(min=60.dp).padding(horizontal=12.dp),horizontalArrangement=Arrangement.SpaceBetween,verticalArrangement=Arrangement.Center) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Text(uiText("show_safe"),fontWeight=FontWeight.Bold,fontSize=12.sp)
                ApprovedSwitch(showSafe,{showSafe=it},uiText("show_safe"))
            }
            if(!preview) Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(3.dp)) {
                FilterChip(selected=tsunami,onClick={tsunami=!tsunami},label={Text("津波",fontSize=13.sp)},modifier=Modifier.heightIn(min=48.dp),colors=FilterChipDefaults.filterChipColors(selectedContainerColor=Color(0x2E155EEF),selectedLabelColor=MaterialTheme.colorScheme.primary))
                BasicTextField(value=tsunamiHeight,onValueChange={tsunamiHeight=it},singleLine=true,
                    textStyle=MaterialTheme.typography.bodyMedium.copy(color=MaterialTheme.colorScheme.onSurface,textAlign=androidx.compose.ui.text.style.TextAlign.Center),
                    keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number,imeAction=ImeAction.Done),keyboardActions=KeyboardActions(onDone={keyboard?.hide()}),
                    modifier=Modifier.width(44.dp).heightIn(min=48.dp).semantics {contentDescription="津波の高さ（m）";stateDescription=tsunamiStatus},
                    decorationBox={inner->Box(Modifier.fillMaxWidth().heightIn(min=48.dp).padding(vertical=4.dp).border(1.dp,if(tsunamiHeight.isNotBlank() && io.github.nevaseme.plandmk1.tsunami.TsunamiPrecomputedHeight.parse(tsunamiHeight)==null)MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline,RoundedCornerShape(6.dp)).padding(horizontal=4.dp),contentAlignment=Alignment.Center){inner()}})
                Text("m",fontSize=13.sp)
            }
            TextButton(onClick={sheet="unknown";listFilter=null;areaIds=null},contentPadding=PaddingValues(horizontal=2.dp)) {
                Text(uiText("unknown_location"),color=MaterialTheme.colorScheme.onSurface,fontWeight=FontWeight.Bold,fontSize=12.sp)
                Text(" ›",color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            SituationMap(people, homes, center, showSafe, now, preview, camera, initialZoom=initialZoom, tsunamiResult=tsunamiResult,
                onPerson = { selectedId = it.id; sheet = "person" },
                onHome = { extraHome = it; homeId = it.id; sheet = "home" },
                onOverlapping = { areaIds = it.map(MapPerson::id); listFilter = null; sheet = "overlap" }, onError = { failed = true }, modifier = Modifier.fillMaxSize(),
                assessmentIntensity=assessmentIntensity,onReportPoint=if(choosingReport){{point->choosingReport=false;onAddReport?.invoke(point)}}else null,reports=reports,onReport=onReport)
            if(onAddReport!=null)Button(onClick={choosingReport=!choosingReport},modifier=Modifier.align(Alignment.BottomEnd).padding(end=14.dp,bottom=96.dp).heightIn(min=48.dp),shape=RoundedCornerShape(10.dp),contentPadding=PaddingValues(horizontal=15.dp,vertical=10.dp)){Text(uiText(if(choosingReport)"cancel" else "report_add"),fontSize=14.sp,fontWeight=FontWeight(750))}
            if(choosingReport)Surface(Modifier.align(Alignment.TopCenter).padding(top=80.dp,start=12.dp,end=12.dp),shape=RoundedCornerShape(10.dp)){Text(uiText("choose_report_point"),Modifier.padding(12.dp))}
            Surface(Modifier.align(Alignment.TopEnd).padding(12.dp), shape = RoundedCornerShape(14.dp), shadowElevation = 3.dp, color = MaterialTheme.colorScheme.surface) {
                Column {
                    IconButton(onClick = { camera.zoom(1.0) }) { Text("+", fontSize = 26.sp, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.semantics { contentDescription = "地図を拡大" }) }
                    HorizontalDivider(Modifier.width(40.dp))
                    IconButton(onClick = { camera.zoom(-1.0) }) { Text("−", fontSize = 26.sp, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.semantics { contentDescription = "地図を縮小" }) }
                }
            }
            Surface(Modifier.align(Alignment.BottomStart).padding(start = 12.dp, bottom = 32.dp), shape = RoundedCornerShape(20.dp), shadowElevation = 2.dp, color = MaterialTheme.colorScheme.surface) {
                TextButton(onClick = { sheet = "legend" }) { Text("凡例", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface); Text(" ›") }
            }
            Surface(Modifier.align(Alignment.BottomEnd).padding(end = 12.dp, bottom = 36.dp), shape = CircleShape, shadowElevation = 2.dp, color = MaterialTheme.colorScheme.surface) {
                IconButton(onClick = { camera.move(center) }) { PlanIcon("target", "表示の中心に戻る") }
            }
            if(!preview) TsunamiMapCalculation(enabled=tsunami,height=tsunamiHeight,onResult={tsunamiResult=it},onStatus={tsunamiStatus=it})
            if (failed) Surface(Modifier.align(Alignment.TopStart).padding(12.dp), shape = RoundedCornerShape(12.dp)) {
                TextButton(onClick = { sheet = "all" }) { Text("地図を読み込めません・一覧を見る", fontSize = 12.sp) }
            }
        }
    }
    if (sheet != null && LocalMapVisible.current) {
        key(sheet) {
        ApprovedSheet("",close,scrollContent=false) {
            when (sheet) {
                "statistics" -> Column(Modifier.fillMaxWidth().padding(22.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
                    DetailHeading(uiText("safety_statistics"),uiText("current_group_only"),close)
                    MapSafety.entries.forEach {state->Row(Modifier.fillMaxWidth()){Text(safetyText(state),Modifier.weight(1f));Text(people.count {it.state==state}.toString())}}
                    Text(uiText("unknown_short",people.count {!it.hasPosition(now)}));Text(uiText("region_unset"))
                }
                "person" -> people.find { it.id == selectedId }?.let { person ->
                    PersonMapDetail(person, now, canRescue, canUndo(person), onRescue, onUndo, onHome = { home -> extraHome = home; homeId = home.id; sheet = "home" }, onClose = close, history = { history(person) }, feedback = { feedback(person) })
                }
                "home" -> (extraHome?.takeIf {it.id==homeId} ?: homes.find { it.id == homeId })?.let { home ->
                    Column(Modifier.fillMaxWidth().fillMaxHeight(.86f).verticalScroll(rememberScrollState()).padding(horizontal = 22.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        DetailHeading(uiText("building_details"), home.address, close)
                        HomeAttributes(home)
                        Text(uiText("registered_people"), style = MaterialTheme.typography.titleMedium)
                        val members = people.filter { it.home?.id == home.id }
                        if (members.isEmpty()) Text(uiText("no_registration"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        members.forEach { person -> PersonRow(person, now, false) {
                            selectedId = person.id; sheet = "person"
                            if (person.hasPosition(now)) camera.move(person.point!!)
                        } }
                        Spacer(Modifier.height(20.dp))
                    }
                }
                "legend" -> Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    DetailHeading(uiText("legend"), null, close)
                    Text(uiText("phone_safety"), style = MaterialTheme.typography.titleMedium)
                    MapSafety.entries.forEach { state -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) { StateSymbol(state); Text(safetyText(state)) } }
                    if(!preview) TsunamiLegend()
                    Text(if(preview)"建物の強さ" else "全壊率（想定震度 $assessmentIntensity）", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(0xFFFBE8C5 to if(preview)"強い" else "低 <10%", 0xFFF2AE5D to if(preview)"中間" else "中 <40%", 0xFFC5672E to if(preview)"弱い" else "高 ≥40%", 0xFFD4D7DC to "不明").forEach { (color, text) ->
                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) { Box(Modifier.fillMaxWidth().height(22.dp).background(Color(color), RoundedCornerShape(4.dp))); Text(text, fontSize = 12.sp) }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) { Observation(R.drawable.impact, uiText("impact")); Observation(R.drawable.enclosed, uiText("enclosed")) }
                    TextButton(onClick = { sheet = "all" }) { Text(uiText("open_people")) }
                    Spacer(Modifier.height(20.dp))
                }
                else -> {
                    val base = situationList(people, null, sheet == "unknown", if (sheet == "overlap") areaIds?.toSet() else null, now)
                    val rows = filteredPeople(base,listFilter,now)
                    LazyColumn(Modifier.fillMaxWidth().fillMaxHeight(.8f), contentPadding = PaddingValues(horizontal = 22.dp, vertical = 8.dp)) {
                        item { DetailHeading(when (sheet) { "unknown" -> uiText("unknown_short",rows.size); else -> uiText("people_heading")+" · ${rows.size}" }, null, close) }
                        if (sheet != "unknown") item { SituationFilters(base, listFilter, now) { listFilter = it } }
                        items(rows, key = { it.id }) { person -> PersonRow(person, now, sheet == "unknown") { selectedId = person.id; sheet = "person" } }
                        if (rows.isEmpty()) item { Text(uiText("no_people"), Modifier.padding(vertical = 20.dp)) }
                    }
                }
            }
        }
        }
    }
    }
}

@Composable private fun SituationFilters(people: List<MapPerson>, selected: String?, now: Long, onSelect: (String?) -> Unit) {
    val options=listOf(null to uiText("all_count",people.size),MapSafety.SOS.name to uiText("sos_count",people.count {it.state==MapSafety.SOS}),MapSafety.UNANSWERED.name to uiText("no_response_count",people.count {it.state==MapSafety.UNANSWERED}),"safe" to uiText("safe_rescued_count",people.count {it.state in listOf(MapSafety.SAFE,MapSafety.RESCUED)}))
    Column(verticalArrangement=Arrangement.spacedBy(9.dp)) {
        options.chunked(2).forEach {pair->Row(horizontalArrangement=Arrangement.spacedBy(9.dp)) {
            pair.forEach {(id,label)->val active=selected==id
                Surface(onClick={onSelect(id)},modifier=Modifier.weight(1f),shape=RoundedCornerShape(10.dp),border=androidx.compose.foundation.BorderStroke(1.dp,if(active)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),color=if(active)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface) {
                    Box(Modifier.heightIn(min=48.dp).padding(horizontal=10.dp,vertical=8.dp),contentAlignment=Alignment.Center){Text(label,fontSize=13.sp,lineHeight=20.sp,fontWeight=FontWeight.Bold,color=if(active)MaterialTheme.colorScheme.onPrimary else if(id==MapSafety.SOS.name)MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)}
                }
            }
        }}
        Row(Modifier.fillMaxWidth().clickable{onSelect("unknown")}.heightIn(min=48.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            PlanIcon("person",null,Modifier.size(18.dp));Text(uiText("unknown_count",people.count {!it.hasPosition(now)}),Modifier.weight(1f),fontSize=14.sp,fontWeight=FontWeight.Bold);PlanIcon("chevron",null,Modifier.size(16.dp))
        }
    }
}

fun filteredPeople(people:List<MapPerson>,filter:String?,now:Long)=when(filter){"safe"->people.filter {it.state in listOf(MapSafety.SAFE,MapSafety.RESCUED)};"unknown"->people.filterNot {it.hasPosition(now)};null->people;else->people.filter {it.state.name==filter}}

@Composable private fun DetailHeading(title: String, subtitle: String?, close: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); subtitle?.let { Text(it, style = MaterialTheme.typography.titleMedium) } }
        ApprovedClose(close)
    }
}

@Composable private fun Observation(resource: Int, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) { Image(painterResource(resource), null, Modifier.size(24.dp)); Text(text, style = MaterialTheme.typography.bodyMedium) }
}

@Composable private fun PersonRow(person: MapPerson, now: Long, unknown: Boolean, click: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = click).padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (!unknown) StateSymbol(person.state) else PlanIcon("person", null)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(personText(person),fontSize=15.sp,lineHeight=22.sp,fontWeight=FontWeight.Bold)
            Text(if(unknown&&person.registeredPlaceVisible)person.home?.address?:uiText("unregistered")else if(person.hasPosition(now))safetyText(person.state)+" · "+uiText("position_age",((now-(person.acquiredAt?:now))/60_000).coerceAtLeast(0))else uiText("unknown_location"),fontSize=13.sp,lineHeight=21.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            if(!person.hasPosition(now)&&person.registeredPlaceVisible&&person.bedroom.isNotBlank())Text(person.bedroom,fontSize=13.sp,lineHeight=21.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)

        }
        PlanIcon("chevron",null,Modifier.size(16.dp))
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable private fun HomeAttributes(home: MapHome) {
    if(home.assessmentIntensity!=null) {
        Text("想定震度 ${home.assessmentIntensity}・全壊率 ${home.fullDamagePercent?.let {String.format(Locale.JAPAN,"%.1f%%",it)} ?: "不明"}",style=MaterialTheme.typography.titleMedium)
        if(home.assessmentAssumed)Text("16階以上は被害率0とする仮定を適用",style=MaterialTheme.typography.bodySmall)

    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(if(home.assessmentIntensity!=null)"全壊リスク" else "建物の強さ", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val color = when (home.rating) { 0 -> Color(0xFFFBE8C5); 1 -> Color(0xFFF2AE5D); 2 -> Color(0xFFC5672E); else -> Color(0xFFD4D7DC) }
        Surface(color = color.copy(alpha = .58f), shape = RoundedCornerShape(6.dp)) {
            Text(if(home.assessmentIntensity!=null)when(home.rating){0->"低";1->"中";2->"高";else->"不明"} else when (home.rating) { 0 -> "強い"; 1 -> "中間"; 2 -> "弱い"; else -> "不明" }, Modifier.padding(horizontal = 10.dp, vertical = 4.dp), color = Color(0xFF183447), style = MaterialTheme.typography.labelMedium)
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        listOf("築年数" to (home.built?.let { "築${java.util.Calendar.getInstance().get(java.util.Calendar.YEAR) - it}年" } ?: "不明"), "建材・構造" to home.material, "階数" to (home.floors?.let { "${it}階建" } ?: "不明")).forEach { (label, value) ->
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) { Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant); Text(value, style = MaterialTheme.typography.titleSmall) }
        }
    }
}

@Composable fun PersonMapDetail(person: MapPerson, now: Long, canRescue: Boolean, canUndo: Boolean, rescue: (MapPerson) -> Unit, undo: (MapPerson) -> Unit, onHome: (MapHome) -> Unit, onClose: () -> Unit, history: @Composable () -> Unit, feedback: @Composable () -> Unit = {}) {
    val needsHelp = person.state in listOf(MapSafety.SOS, MapSafety.UNANSWERED)
    Column(Modifier.fillMaxWidth().heightIn(max = 640.dp).verticalScroll(rememberScrollState()).padding(horizontal = 22.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (person.hasPosition(now)) StateSymbol(person.state, Modifier.size(46.dp)) else PlanIcon("person", null)
            Column(Modifier.weight(1f)) {
                Text(personText(person),fontSize=23.sp,lineHeight=30.sp,fontWeight=FontWeight.ExtraBold)
                Text(if (person.hasPosition(now)) safetyText(person.state) else uiText("unknown_location"), color = if (person.hasPosition(now)) Color(person.state.argb) else MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge)
            }
            ApprovedClose(onClose,"詳細を閉じる")
        }
        Text("${uiText("last_position")} · ${positionText(person, now)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if(!person.identityVisible)Text(uiText("waiting_disclosure"),style=MaterialTheme.typography.bodySmall)
        if (needsHelp) {
            if (person.hasPosition(now) && person.state == MapSafety.SOS && person.memo.isNotBlank()) {
                Text(uiText("sos_memo"), style = MaterialTheme.typography.titleMedium)
                Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(12.dp)) { Text(person.memo, Modifier.fillMaxWidth().padding(16.dp), color = MaterialTheme.colorScheme.onErrorContainer) }
            }
        }
        if(person.registeredPlaceVisible) {
            HorizontalDivider()
            Text(uiText("home_sleeping"), style = MaterialTheme.typography.titleMedium)
            person.home?.let { home ->
                Row(verticalAlignment = Alignment.CenterVertically) { Text(home.address, Modifier.weight(1f), fontWeight = FontWeight.Bold); TextButton(onClick = { onHome(home) }) { Text(uiText("see_building")) } }
            }
            Text(person.bedroom, style = MaterialTheme.typography.bodyLarge)
            person.home?.let { HomeAttributes(it) }
        }
        if(needsHelp) {
            if (person.impactAt != null || person.enclosedAt != null) {
                HorizontalDivider(); Text(uiText("phone_observations"), style = MaterialTheme.typography.titleMedium)
                person.impactAt?.let { Observation(R.drawable.impact, "${uiText("impact")} ${formatTime(it)}") }
                person.enclosedAt?.let { Observation(R.drawable.enclosed, "${uiText("enclosed")} ${formatTime(it)}") }
            }
        }
        var expanded by rememberSaveable(person.id) { mutableStateOf(false) }
        if(person.identityVisible)TextButton(onClick = { expanded = !expanded }, contentPadding = PaddingValues(0.dp)) { Text(uiText("care_history")+if(expanded)" ⌃"else " ⌄") }
        if (expanded&&person.identityVisible) history()
        if (canRescue && needsHelp&&person.identityVisible) Button(onClick = { rescue(person) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00836C),contentColor=Color.White), shape = RoundedCornerShape(14.dp)) { Text(uiText("mark_rescued"), fontWeight = FontWeight.Bold) }
        if (canRescue && canUndo && person.state == MapSafety.RESCUED) TextButton(onClick = { undo(person) }, modifier = Modifier.fillMaxWidth()) { Text(uiText("correct_rescued")) }
        feedback()
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun AnswerPanel(first: Boolean, initial: MapSafety?, initialMemo: String, saving: Boolean, allowMemo: Boolean = true, save: (MapSafety, String) -> Unit, saveMemo: ((String) -> Unit)? = null, feedback: @Composable () -> Unit = {}) {
    var selected by rememberSaveable(initial) { mutableStateOf(initial?.name) }
    var memo by rememberSaveable(initialMemo) { mutableStateOf(initialMemo) }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).imePadding().verticalScroll(rememberScrollState()).padding(horizontal=22.dp,vertical=24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(uiText(if (first) "safety_first" else "my_safety"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(uiText(if (first) "answer_opens_map" else "update_when_changed"),fontSize=14.sp,lineHeight=24.sp,color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(MapSafety.SAFE, MapSafety.SOS).forEach { state ->
                val chosen = selected == state.name
                OutlinedCard(onClick = { selected = state.name }, modifier = Modifier.weight(1f).heightIn(min=64.dp), enabled = !saving,
                    border = androidx.compose.foundation.BorderStroke(if (chosen) 2.dp else 1.dp, if (chosen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                    colors = CardDefaults.outlinedCardColors(containerColor = if (chosen) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)) {
                    Row(Modifier.fillMaxWidth().padding(horizontal=10.dp,vertical=16.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) { StateSymbol(state, Modifier.size(34.dp), null); Spacer(Modifier.width(8.dp)); Text(uiText(if(state==MapSafety.SAFE)"my_safe"else "my_sos"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                }
            }
        }
        if (allowMemo && selected == MapSafety.SOS.name) {
            ApprovedField(memo,{memo=it.take(SOS_MEMO_LIMIT)},uiText("sos_memo"),Modifier.fillMaxWidth(),enabled=!saving,minLines=3,placeholder=uiText("sos_placeholder"))
            Text("${memo.length} / $SOS_MEMO_LIMIT",Modifier.fillMaxWidth(),textAlign=androidx.compose.ui.text.style.TextAlign.End,fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (saveMemo != null && initial == MapSafety.SOS && selected == MapSafety.SOS.name) {
            OutlinedButton(onClick = { saveMemo(memo) }, enabled = !saving && memo != initialMemo, modifier = Modifier.fillMaxWidth()) {
                Text(uiText("save_memo"))
            }
        }
        Button(onClick = { selected?.let { save(MapSafety.valueOf(it), memo) } }, enabled = selected != null && !saving,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = RoundedCornerShape(13.dp)) {
            if (saving) { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp); Spacer(Modifier.width(10.dp)) }
            Text(uiText(if (saving) "saving" else if (first) "register_safety" else "update_safety"),fontSize=16.sp,lineHeight=24.sp,fontWeight=FontWeight(750))
        }
        feedback()
    }
}
