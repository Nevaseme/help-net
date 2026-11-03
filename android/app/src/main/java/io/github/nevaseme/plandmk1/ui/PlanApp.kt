package io.github.nevaseme.plandmk1.ui

import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.activity.compose.BackHandler
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nevaseme.plandmk1.*
import io.github.nevaseme.plandmk1.cloud.mapMunicipalityOrNull
import io.github.nevaseme.plandmk1.core.*
import io.github.nevaseme.plandmk1.nearby.NearbyTransport
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanApp(model: PlanViewModel) {
    PlanLocalization { PlanAppContent(model) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlanAppContent(model: PlanViewModel) {
    val residents by model.residents.collectAsStateWithLifecycle()
    val careEvents by model.careEvents.collectAsStateWithLifecycle()
    val group by model.group.collectAsStateWithLifecycle()
    val roster by model.roster.collectAsStateWithLifecycle()
    val cloudState by model.cloud.state.collectAsStateWithLifecycle()
    val groupLabel=cloudState.takeIf {it.groupId==group}?.groupName?.takeIf {it.isNotBlank()}?:uiText("group_label")
    val incident by model.incidentUi.collectAsStateWithLifecycle()
    val places by model.places.collectAsStateWithLifecycle()
    val nearby by model.sharing.state.collectAsStateWithLifecycle()
    val message by model.message.collectAsStateWithLifecycle()
    val saving by model.saving.collectAsStateWithLifecycle()
    val own = residents.find { it.originId == model.localId }
    val storedOwn by model.storedOwn.collectAsStateWithLifecycle()
    val activeVenue by model.activeVenue.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(if(model.hasCloudSession())1 else 4) }
    var lastMain by rememberSaveable { mutableIntStateOf(1) }
    var preview by rememberSaveable { mutableStateOf(false) }
    var showPeople by rememberSaveable { mutableStateOf(false) }
    var languageSettings by rememberSaveable { mutableStateOf(false) }
    var responseSummary by rememberSaveable { mutableStateOf(false) }
    var incidentDetails by rememberSaveable { mutableStateOf(false) }
    var buildingFromProfile by rememberSaveable { mutableStateOf(false) }
    var appSettingsChild by rememberSaveable { mutableStateOf(false) }
    val internet = internetAvailable()
    var reportPoint by remember { mutableStateOf<MapPoint?>(null) }
    var reportTarget by remember {mutableStateOf<ResponseTarget?>(null)}
    // A group switch arrives before its incident flow. Do not navigate using
    // the previous group's unanswered incident while the new one is loading.
    val firstAnswer = incident.groupId == group && mainDestination(incident.canEdit, incident.current?.answers?.containsKey(model.localId) == true) == MainDestination.ANSWER
    var awaitingSavedAnswer by rememberSaveable(group, incident.selection) { mutableStateOf(firstAnswer) }
    val saveStates by model.saveStates.collectAsStateWithLifecycle()
    var previousSafetyPhase by remember { mutableStateOf(saveStates["safety"]?.phase) }
    LaunchedEffect(group,incident.groupId,incident.selection) { if(firstAnswer){tab=0;lastMain=0;awaitingSavedAnswer=true} }
    LaunchedEffect(firstAnswer, saveStates["safety"]?.phase) {
        if (!firstAnswer && awaitingSavedAnswer) { tab = 1; lastMain = 1; awaitingSavedAnswer = false }
        else if (previousSafetyPhase == SavePhase.SAVING && saveStates["safety"]?.phase == SavePhase.SAVED && tab == 0) { tab = 1; lastMain = 1 }
        previousSafetyPhase = saveStates["safety"]?.phase
    }
    if (preview && BuildConfig.DEBUG) { DesignDemoScreen { preview = false }; return }
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    var communicationDetails by rememberSaveable(group) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var picking by rememberSaveable { mutableStateOf(false) }
    var detailId by rememberSaveable(group,incident.selection) { mutableStateOf<String?>(null) }
    var buildingId by rememberSaveable(group,incident.selection) {mutableStateOf<String?>(null)}
    val snackbar = remember { SnackbarHostState() }
    val screenState = rememberSaveableStateHolder()
    val context = LocalContext.current
    val openSafety by model.openSafety.collectAsStateWithLifecycle()
    var notificationsEnabled by remember { mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled() }
    LaunchedEffect(openSafety) { if (openSafety) { menuOpen=false; tab = 0; lastMain = 0; picking = false; detailId = null; buildingId = null; model.openSafety.value = false } }

    fun startWithRadios() {
        if (cloudState.groupId == group && cloudState.nearbyReady) { model.startSharing(); return }
        val ready = try {
            val bluetooth = context.getSystemService(BluetoothManager::class.java)?.adapter?.isEnabled == true
            val wifi = (context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager).isWifiEnabled
            bluetooth && wifi
        } catch (_: SecurityException) { false }
        if (ready) model.startSharing()
        else model.sharing.error("BluetoothとWi-FiをONにして、もう一度「共有開始」を押してください。インターネットへの接続は不要です")
    }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
        if (notificationsEnabled) startWithRadios()
        else model.sharing.error("通知を許可して、もう一度「共有開始」を押してください")
    }
    fun requestNotificationAndStart() {
        notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
        if (notificationsEnabled) {
            startWithRadios()
            return
        }
        val activity = generateSequence(context) { (it as? android.content.ContextWrapper)?.baseContext }
            .filterIsInstance<android.app.Activity>().firstOrNull()
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            (!model.notificationPrompted || activity?.let {
                androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale(it, android.Manifest.permission.POST_NOTIFICATIONS)
            } == true)) {
            model.notificationPrompted = true
            notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        } else {
            val settings = if (android.os.Build.VERSION.SDK_INT >= 26) {
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            } else {
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:${context.packageName}"))
            }
            context.startActivity(settings)
        }
    }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.all { it }) requestNotificationAndStart()
        else model.sharing.error("近くの端末と共有する権限が必要です。端末のアプリ設定で許可してから再試行してください")
    }
    fun start() {
        val required = NearbyTransport.permissions().filter { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }.toMutableList()
        // Android 12 requires coarse and fine location in the same precision request.
        if (android.os.Build.VERSION.SDK_INT in 31..32 && android.Manifest.permission.ACCESS_FINE_LOCATION in required && android.Manifest.permission.ACCESS_COARSE_LOCATION !in required) {
            required.add(android.Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        if (required.isEmpty()) requestNotificationAndStart() else permissions.launch(required.toTypedArray())
    }
    // Start once after membership/credential provisioning, and again on a later app visit.
    // Permission refusals do not cause a composition/request loop.
    var autoAttempted by remember(group) { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (cloudState.groupId==group && cloudState.nearbyReady && !nearby.active &&
            NearbyTransport.permissions().all { ContextCompat.checkSelfPermission(context,it)==PackageManager.PERMISSION_GRANTED } &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()) model.startSharing()
    }
    LaunchedEffect(group,cloudState.nearbyReady,roster) {
        if (cloudState.groupId==group && cloudState.nearbyReady && !autoAttempted && roster?.members?.any { model.localId in it.deviceIds }==true) {
            autoAttempted=true
            if (!nearby.active) start()
        }
    }
    LaunchedEffect(message) {
        message?.let { snackbar.showSnackbar(it); if (model.message.value == it) model.message.value = null }
    }
    fun navigate(destination: Int) {
        incidentDetails=false
        buildingFromProfile=tab==3&&destination==2
        if (tab in listOf(0, 1, 8, 10) && !picking) lastMain = tab
        tab = destination; picking = false
        scope.launch { menuOpen=false }
    }
    fun goBack() {
        if(picking){picking=false;tab=3}
        else if(tab==2&&buildingFromProfile){tab=3;buildingFromProfile=false}
        else {tab=lastMain;if(appSettingsChild){appSettingsChild=false;languageSettings=true}}
    }
    BackHandler(enabled = picking || tab !in listOf(0, 1, 8, 10)) {goBack()}
    Box {
        Scaffold(
            topBar = { Column(Modifier.background(MaterialTheme.colorScheme.surface).statusBarsPadding()) {
                ApprovedHeader(
                    title=when {picking->"住居の位置を選択";tab in listOf(0,1,8,10)->if(incident.current!=null)uiText(if(incident.current!!.start.training)"training" else "earthquake")else "";else->uiText(when(tab){2->"home_building";3->"my_registration";4->"group_sharing";5->"offline_map";6->"training_incidents";9->"position_history";11->"home";else->"app_information"})},
                    date=null,
                    back=picking||tab !in listOf(0,1,8,10),
                    onNavigate={if(picking||tab !in listOf(0,1,8,10))goBack() else {val opening=!menuOpen;languageSettings=false;incidentDetails=false;responseSummary=false;detailId=null;buildingId=null;menuOpen=opening}},
                    compact=tab in listOf(0,1,8,10)&&!picking,
                    noInternet=!internet,
                    onDetails=if(tab in listOf(0,1,8,10)&&!picking&&incident.current!=null){{incidentDetails=true}}else null)
            } },
            bottomBar = { if(tab in listOf(0,1,8,10) && !picking) Column(Modifier.background(MaterialTheme.colorScheme.surface).navigationBarsPadding()){ApprovedTabs(tab){tab=it;lastMain=it;languageSettings=false;responseSummary=false;incidentDetails=false;menuOpen=false}} },
            snackbarHost = { SnackbarHost(snackbar) },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                val mapVisible = tab == 1 && !picking
                // Retain pan/filter across tabs, but a different group or venue starts its own map.
                key(group,activeVenue?.sessionId) { RetainedMapContent(mapVisible) {
                    MapScreen(model, residents, own, false,
                        onPick={}, onCancelPick={}, onSelect={detailId=it.originId},onBuilding={buildingId=it},
                        openList=showPeople,onListClosed={showPeople=false},onAddReport={point->reportPoint=point;tab=10;lastMain=10},onReport={id->reportTarget=ResponseTarget(TargetKind.REPORT,id);tab=10;lastMain=10})
                } }
                if (!mapVisible) screenState.SaveableStateProvider("$group:$tab") {
                    when {
                        else -> when(tab) {
                        0 -> SafetyScreen(model, own, saving, onProfile={navigate(3)}, onIncident={navigate(6)})
                        1, 8 -> MapScreen(model, residents, own, picking,
                            onPick={model.pickedPoint.value=it;picking=false;tab=3},
                            onCancelPick={picking=false;tab=3},onSelect={detailId=it.originId},onBuilding={buildingId=it},openList=showPeople,onListClosed={showPeople=false},listOnly=tab==8)
                        2 -> BuildingsScreen(model, onProfile={navigate(3)}) {buildingId=it}
                        10 -> ResponseScreen(model,responseSummary,{responseSummary=false},reportPoint,{reportPoint=null},reportTarget,{reportTarget=null},onOpenSummary={responseSummary=true})
                        11 -> PeaceHome(model, own != null, onProfile={navigate(3)}, onIncident={navigate(6)}, onPreview={preview=true})
                        3 -> SettingsScreen(model, storedOwn, group, saving, onBuildings={navigate(2)}, onPick={picking=true;tab=1})
                        9 -> PositionSettings(model, own != null)
                        4 -> LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                        if(!communicationDetails)item { CloudSharingPanel(model,own?.name.orEmpty(),onCommunication={communicationDetails=true}) }
                        if(communicationDetails) {
                        item { TextButton(onClick={communicationDetails=false}) {Text("グループに戻る")} }
                        item { CloudCommunicationDetails(model) }
                        item { SectionTitle("近くの端末と共有") }
                        item { StatusFeedback(when {
                            nearby.starting -> "共有を開始しています…"
                            nearby.verifications.isNotEmpty() -> "接続コードを確認してください"
                            nearby.connected.isNotEmpty() -> "接続済み・自動同期中（" + nearby.connected.size + " 台）"
                            nearby.active -> "共有中・接続相手を探しています"
                            else -> "共有停止中"
                        }, busy = nearby.starting) }
                        item {
                            if (nearby.active || nearby.starting) OutlinedButton(onClick = model::stopSharing, modifier = Modifier.fillMaxWidth()) { Text("共有停止") }
                            else Button(onClick = ::start, enabled = own != null && !saving, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("共有開始") }
                        }
                        nearby.error?.let { error -> item {
                            InfoCard(error, true)
                            if (nearby.showPlayServicesSettings) TextButton(onClick = {
                                context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:com.google.android.gms")))
                            }) { Text("Google Playの権限設定を開く") }
                            TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS)) }) { Text("通信設定を開く") }
                            TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:${context.packageName}"))) }) { Text("アプリの権限設定を開く") }
                        } }
                        if (nearby.active && nearby.peers.isEmpty()) item { Text("近くの端末を探しています…") }
                        items(nearby.peers, key = { it.id }) { peer ->
                            OutlinedCard { Column(Modifier.fillMaxWidth().padding(12.dp)) {
                                NamedEntity("接続相手の名前", samplePersonName(group,peer.name))
                                when (peer.id) {
                                    in nearby.connected -> Text("接続済み・自動同期中")
                                    in nearby.connecting -> Text("接続確認中…")
                                    else -> TextButton(onClick = { model.sharing.connect(peer) }) { Text("接続する") }
                                }
                            } }
                        }
                        item { Text("相手が受け取った情報：${nearby.receiptCount}件", style = MaterialTheme.typography.bodySmall) }
                        }
                        }
                        5 -> OfflineMapScreen(model,own)
                        6 -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
                            IncidentActions(model,own!=null)
                            if(roster==null)OutlinedButton(onClick={navigate(4)}){Text(uiText("group_sharing"))}
                            HorizontalDivider()
                            IncidentSelector(model)
                            if(own==null) Button(onClick={navigate(3)}){Text("自分の情報を登録")}
                        }
                        else -> AppInformation()
                    }
                    }
                }
                if(menuOpen) ApprovedMenu(groupLabel,{menuOpen=false},::navigate,{menuOpen=false;languageSettings=true})
                if(languageSettings) LanguageSettings(onClose={languageSettings=false},onNavigate={languageSettings=false;navigate(it);appSettingsChild=true})
                if(incidentDetails)incident.current?.let {current->
                    var now by remember(current.start.eventId) {mutableLongStateOf(System.currentTimeMillis())}
                    LaunchedEffect(current.start.eventId){while(true){now=System.currentTimeMillis();kotlinx.coroutines.delay(60_000)}}
                    ApprovedSheet(uiText(if(current.start.training)"training_details" else "earthquake_details"),{incidentDetails=false}) {
                        Text(uiText("incident_started"),color=MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(DateFormat.getDateTimeInstance(DateFormat.MEDIUM,DateFormat.SHORT,java.util.Locale.forLanguageTag(LocalUiLanguage.current)).format(Date(current.start.createdAt)))
                        Text(uiText("elapsed",((now-current.start.createdAt)/60_000).coerceAtLeast(0)))
                    }
                }
            }
        }
    }
    nearby.verifications.firstOrNull()?.let { verification ->
        AlertDialog(onDismissRequest = { model.sharing.verify(verification.id, false) },
            title = { Text("接続コードの確認") },
            text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("相手：${samplePersonName(group,verification.name)}")
                Text(verification.code, style = MaterialTheme.typography.headlineLarge)
                Text("相手の端末とコードを照合")
            } },
            confirmButton = { TextButton(onClick = { model.sharing.verify(verification.id, true) }) { Text("一致している・接続") } },
            dismissButton = { TextButton(onClick = { model.sharing.verify(verification.id, false) }) { Text("接続しない") } })
    }
    buildingId?.let {id->key(group,incident.selection,id){BuildingDetail(model,id,{buildingId=null;detailId=it.originId},{buildingId=null}, registration = tab == 2)}}
    residents.find { it.originId == detailId }?.let { resident ->
        val person = projectPersonPrivacy(residentMapPerson(resident, places, careEvents),incident.current?.start?.createdAt,System.currentTimeMillis())
        PlanModalBottomSheet(onDismissRequest = { detailId = null }) {
            PersonMapDetail(person, System.currentTimeMillis(), false, false, {}, {},
                onHome = { home -> if (placeBuildings(places).any { it.buildingId == home.id }) { detailId = null; buildingId = home.id } },
                onClose = { detailId = null }, history = { CareHistoryAndEditor(model, resident, careEvents, own != null && incident.canEdit, saving) })
        }
    }
}

@Composable
private fun SettingsScreen(model: PlanViewModel, own: Resident?, group: String, saving: Boolean, onBuildings: () -> Unit, onPick: () -> Unit) {
    val demoHome by model.ownDemoHome.collectAsStateWithLifecycle()
    val homeRows by model.places.collectAsStateWithLifecycle()
    val storedReference=placeBuildings(homeRows).find{it.buildingId==validHome(homeRows,model.localId)?.buildingId}?.locationReference
    val effectiveReference=demoHome?.let {BuildingLocationReference(it.address,it.plateauId,if(it.plateauId!=null)"plateau-26100-kyoto-shi-2025" else null,it.searchReference)}?:storedReference
    var pickedReference by remember(group,demoHome?.buildingId,storedReference) {mutableStateOf(effectiveReference)}
    var pickingHome by remember {mutableStateOf(false)}
    var searchHome by remember {mutableStateOf(false)}
    var query by rememberSaveable(group) {mutableStateOf("")}
    val keyboard=androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val focus=androidx.compose.ui.platform.LocalFocusManager.current
    val homeLookupContext=LocalContext.current.applicationContext
    val homeLookup=remember {io.github.nevaseme.plandmk1.buildings.CityAddresses(homeLookupContext)}
    val homeScope=rememberCoroutineScope()
    var searchingHome by remember {mutableStateOf(false)}
    var homeSearchError by remember {mutableStateOf("")}
    var homeCandidates by remember {mutableStateOf<List<io.github.nevaseme.plandmk1.buildings.AddressCandidate>>(emptyList())}
    var homeCandidate by remember {mutableStateOf<io.github.nevaseme.plandmk1.buildings.AddressCandidate?>(null)}
    fun selectHomeCandidate(candidate:io.github.nevaseme.plandmk1.buildings.AddressCandidate){homeCandidate=candidate;homeCandidates=emptyList();searchHome=true;pickingHome=true}
    fun openHome(search:Boolean){
        if(searchingHome)return
        keyboard?.hide();focus.clearFocus();homeSearchError="";homeCandidates=emptyList()
        if(!search){homeCandidate=null;searchHome=false;pickingHome=true;return}
        val requested=query.trim()
        homeScope.launch {searchingHome=true;try {
            val candidates=homeLookup.candidates(requested,model.cloud.state.value.mapMunicipalityOrNull()?.code)
            when(candidates.size){0->homeSearchError="場所が見つかりません。住所を詳しく入力してください";1->selectHomeCandidate(candidates.single());else->homeCandidates=candidates}
        }catch(e:kotlinx.coroutines.CancellationException){throw e}catch(_:Exception){homeSearchError="検索できませんでした。もう一度お試しください"}finally{searchingHome=false}}
    }
    var address by rememberSaveable(group,own?.originId,demoHome?.buildingId,storedReference?.address) {mutableStateOf(demoHome?.address?:storedReference?.address.orEmpty())}
    var name by rememberSaveable(group,own?.originId,demoHome?.buildingId,storedReference?.address) { mutableStateOf(own?.displayName().orEmpty()) }
    var lat by rememberSaveable(group,own?.originId,demoHome?.buildingId,storedReference?.address) { mutableStateOf(own?.latitude?.toString().orEmpty()) }
    var lon by rememberSaveable(group,own?.originId,demoHome?.buildingId,storedReference?.address) { mutableStateOf(own?.longitude?.toString().orEmpty()) }
    var floor by rememberSaveable(group,own?.originId,demoHome?.buildingId,storedReference?.address) { mutableStateOf(own?.floor.orEmpty()) }
    var room by rememberSaveable(group,own?.originId,demoHome?.buildingId,storedReference?.address) { mutableStateOf(own?.room.orEmpty()) }
    var coordinates by rememberSaveable {mutableStateOf(false)}
    val point by model.pickedPoint.collectAsStateWithLifecycle()
    LaunchedEffect(point) { point?.let { lat = "%.6f".format(java.util.Locale.US, it.latitude); lon = "%.6f".format(java.util.Locale.US, it.longitude); model.pickedPoint.value = null; address="";pickedReference=null;floor="";room="" } }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { SectionTitle("自分の情報") }
        item { OutlinedTextField(name, { name = it }, label = { Text("自分の名前") }, singleLine = true, enabled = !saving, modifier = Modifier.fillMaxWidth()) }
        item { SectionTitle("住居・寝室") }
        item {AddressSearchField(query,{query=it},{if(query.isNotBlank())openHome(true)},Modifier.fillMaxWidth(),enabled=!saving&&!searchingHome)}
        item {Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Button(enabled=!saving&&!searchingHome&&query.isNotBlank(),onClick={openHome(true)},modifier=Modifier.weight(1f)){Text(if(searchingHome)"検索中…" else "住所を検索")};OutlinedButton(enabled=!saving&&!searchingHome,onClick={openHome(false)},modifier=Modifier.weight(1f)){Text("地図で選ぶ")}}}
        if(homeSearchError.isNotBlank())item {Text(homeSearchError)}
        items(homeCandidates,key={it.id}){candidate->TextButton(onClick={selectHomeCandidate(candidate)}){Text(candidate.address+if(candidate.precision in listOf("town","block","area"))" 付近" else "")}}
        if(address.isNotBlank())item {Text("選択した住居",style=MaterialTheme.typography.labelLarge);Text(address)}
        item {TextButton(onClick={keyboard?.hide();focus.clearFocus();onBuildings()}){Text("登録済みの建物・部屋から選ぶ")}}
        item { TextButton(onClick={coordinates=!coordinates}) {Text(uiText("coordinates"))} }
        if(coordinates) {
        item { OutlinedTextField(lat, { lat = it;pickedReference=null;address="";floor="";room="" }, label = { Text("緯度") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text), singleLine = true, enabled = !saving, modifier = Modifier.fillMaxWidth()) }
        item { OutlinedTextField(lon, { lon = it;pickedReference=null;address="";floor="";room="" }, label = { Text("経度") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text), singleLine = true, enabled = !saving, modifier = Modifier.fillMaxWidth()) }
        }
        item { OutlinedTextField(floor, { floor = it }, label = { Text("階数（例：2階）") }, singleLine = true, enabled = !saving, modifier = Modifier.fillMaxWidth()) }
        item { OutlinedTextField(room, { room = it }, label = { Text("寝る部屋・場所の説明") }, minLines = 2, enabled = !saving, modifier = Modifier.fillMaxWidth()) }
        item { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OperationFeedback(model, "profile")
            Button(onClick = { model.saveProfile(group, name, lat, lon, floor, room, address,pickedReference?.copy(address=address.trim().ifBlank{"地図で選択した住居"})) }, enabled = !saving,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(if (saving) "保存しています…" else "登録情報を保存") }
        } }

    }
    if(pickingHome)BuildingLocationPicker(homeCandidate?.let {MapPoint(it.latitude,it.longitude)}?:MapPoint(lat.toDoubleOrNull()?:35.0145,lon.toDoubleOrNull()?:135.7625),{_,_->},{pickingHome=false},initialQuery=if(searchHome)query else "",initialCandidate=homeCandidate,initialReference=pickedReference,onHomeConfirm={point,ref->val moved=kotlin.math.abs((lat.toDoubleOrNull()?:point.latitude)-point.latitude)>1e-6||kotlin.math.abs((lon.toDoubleOrNull()?:point.longitude)-point.longitude)>1e-6;if(moved){floor="";room=""};lat=point.latitude.toString();lon=point.longitude.toString();address=ref.address;pickedReference=ref;pickingHome=false})

}

@Composable
private fun OfflineMapScreen(model:PlanViewModel,own:Resident?) {
    val offline by model.maps.state.collectAsStateWithLifecycle()
    val cloud by model.cloud.state.collectAsStateWithLifecycle()
    val group by model.group.collectAsStateWithLifecycle()
    val city=cloud.takeIf {it.groupId==group}?.mapMunicipalityOrNull()
    var deleteConfirm by remember {mutableStateOf(false)}
    LaunchedEffect(city?.code){model.maps.selectCity(city?.code)}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        item {OutlinedCard {Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text(city?.let {"${it.name}全域の地図"}?:"オフライン地図",style=MaterialTheme.typography.titleLarge)
            if(city==null)Text("グループの活動地域が未設定です。管理者が「グループ」から市を設定してください。")
            else {
                Text(when {offline.busy->"保存状態を確認中…";offline.complete->"保存済み・圏外で利用可能";offline.downloading->"保存中：${offline.resources} / ${offline.expected} 項目";offline.exists->"保存は未完了です";else->"市全域の道路・地名を保存します"})
                if(!offline.exists)Text(if(city.code=="26100")"背景地図 約39MB・住所と建物データはアプリに収録済み" else "背景地図 約8MB・住所データはアプリに収録済み")
                if(offline.bytes>0)Text("保存容量：%.1f MB".format(offline.bytes/1048576.0))
                offline.error?.let {Text(it,color=MaterialTheme.colorScheme.error)}
                if(offline.downloading)OutlinedButton(onClick=model.maps::pause){Text("一時停止")}
                else if(!offline.busy&&!offline.complete)Button(onClick={if(offline.exists)model.maps.resume() else model.maps.downloadCity(city.code)}){Text(if(offline.exists)"保存を再開" else "市全域をダウンロード")}
                if(offline.exists&&!offline.busy)TextButton(onClick={deleteConfirm=true}){Text("保存地図を削除")}
            }
        }}}
    }
    if(deleteConfirm)AlertDialog(onDismissRequest={deleteConfirm=false},title={Text("保存地図を削除しますか？")},text={Text("登録情報・安否・建物の記録は残ります。")},confirmButton={TextButton(onClick={deleteConfirm=false;model.maps.delete()}){Text("地図を削除")}},dismissButton={TextButton(onClick={deleteConfirm=false}){Text("キャンセル")}})
}

fun formatTime(time: Long): String = if(time == 0L) "今回未回答" else DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(time))
