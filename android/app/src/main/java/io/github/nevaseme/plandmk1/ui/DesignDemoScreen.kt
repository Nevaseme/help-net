package io.github.nevaseme.plandmk1.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.*

/** Isolated presentation fixture: no resident, incident or location is written to Room or transmitted. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DesignDemoScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val now = rememberSaveable { System.currentTimeMillis() }
    val samples = remember(now) { loadDesignSamples(context, now) }
    var phase by rememberSaveable { mutableIntStateOf(0) }
    var selfState by rememberSaveable { mutableStateOf<String?>(null) }
    var memo by rememberSaveable { mutableStateOf("") }
    var rescued by rememberSaveable { mutableStateOf(listOf<String>()) }
    var tab by rememberSaveable { mutableIntStateOf(1) }
    var menu by rememberSaveable { mutableStateOf(false) }
    var list by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    var result by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(result) { result?.let { snackbar.showSnackbar(it); result = null } }
    BackHandler { if (menu) menu = false else onClose() }
    val people = samples.people.map { p ->
        when {
            p.id in rescued -> p.copy(state = MapSafety.RESCUED)
            p.id == "p5" && selfState != null -> p.copy(state = MapSafety.valueOf(selfState!!), memo = memo)
            else -> p
        }
    }
    Scaffold(
        topBar = { TopAppBar(title = {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(menuText(if (phase == 2) "地震発生" else "PlanD"), fontWeight = FontWeight.ExtraBold)
                Text(menuText(if (phase == 2) io.github.nevaseme.plandmk1.core.usDateTime(now) else "画面訓練"), style = MaterialTheme.typography.labelLarge)
            }
        }, navigationIcon = { IconButton(onClick = { menu = true }) { PlanIcon("menu", "訓練のメニュー") } }, colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)) },
        bottomBar = { if (phase == 2 && selfState != null) NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
            NavigationBarItem(tab == 1, { tab = 1 }, colors = planNavigationColors(), icon = { PlanIcon("map", null) }, label = { Text(menuText("地図")) })
            NavigationBarItem(tab == 0, { tab = 0 }, colors = planNavigationColors(), icon = { PlanIcon("person", null) }, label = { Text(menuText("安否")) })
        } }, snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                phase < 2 -> Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                    Spacer(Modifier.height(24.dp))
                    Text(menuText(if (phase == 0) "もしものときに、\n地域でつながる。" else "地震の報告を受け付けました"), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text(menuText(if (phase == 0) "位置の記録・共有を確認し、地震が起きたら報告します。" else "報告 1人 / 必要 2人"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = { phase = 1 }, enabled = phase == 0, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(menuText(if (phase == 0) "地震発生を報告" else "自分の報告を送信済み")) }
                    if (phase == 1) {
                        OutlinedButton(onClick = { phase = 2 }, modifier = Modifier.fillMaxWidth()) { Text(menuText("もう1人の報告を受信（模擬）")) }
                        TextButton(onClick = { phase = 0 }) { Text(menuText("自分の報告を取り消す")) }
                    }
                    Spacer(Modifier.weight(1f))
                    Text(menuText(""), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = onClose) { Text(menuText("訓練を終了")) }
                }
                selfState == null || tab == 0 -> AnswerPanel(selfState == null, selfState?.let(MapSafety::valueOf), memo, false, save = { state, text ->
                    selfState = state.name; memo = text; rescued = rescued - "p5"; tab = 1; result = "安否を保存しました"
                })
                else -> SituationScreen(people, samples.homes, io.github.nevaseme.plandmk1.MapPoint(32.7904, 130.819), now, preview = true,
                    onRescue = { rescued = rescued + it.id; result = "救助済みを記録しました" },
                    onUndo = { rescued = rescued - it.id; result = "救助済みを訂正しました" },
                    history = { person -> Text(menuText(if (person.id in rescued || person.state == MapSafety.RESCUED) "自分が救助済みを記録" else "対応記録はありません")) },
                    openList = list, onListClosed = { list = false })
            }
        }
    }
    if (menu) AlertDialog(onDismissRequest = { menu = false }, title = { Text(menuText("画面訓練")) }, text = { Column {
        TextButton(onClick = { phase = 2; selfState = selfState ?: MapSafety.SAFE.name; tab = 1; list = true; menu = false }) { Text(menuText("住民一覧")) }
        TextButton(onClick = { phase = 0; selfState = null; rescued = emptyList(); memo = ""; menu = false }) { Text(menuText("はじめから試す")) }
        TextButton(onClick = onClose) { Text(menuText("訓練を終了")) }
    } }, confirmButton = { TextButton(onClick = { menu = false }) { Text(menuText("閉じる")) } })
}
