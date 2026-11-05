package io.github.nevaseme.plandmk1.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nevaseme.plandmk1.PlanViewModel
import java.text.DateFormat
import java.util.Date

@Composable
fun PositionSettings(model: PlanViewModel, registered: Boolean) {
    val context = LocalContext.current
    val state by model.position.state.collectAsStateWithLifecycle()
    val positions by model.positions.collectAsStateWithLifecycle()
    val last = positions.find { it.originId == model.localId }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(menuText(if (state.active) "位置記録中" else "位置記録は停止中"), style = MaterialTheme.typography.headlineSmall)
        Text(menuText(state.message))
        if (last != null) {
            Text(menuText("最後に保存した位置"), style = MaterialTheme.typography.titleMedium)
            Text(menuText("${io.github.nevaseme.plandmk1.core.usDateTime(last.acquiredAt)}取得\n精度 約${last.accuracyMeters.toInt()}m\n緯度 ${last.latitude}・経度 ${last.longitude}"))
        } else Text(menuText("位置はまだ取得していません"))
        if (!registered) Text(menuText("先にメニューの「自分の情報」を登録してください"))
        if (state.active) {
            Button(onClick = { model.position.refresh() }, modifier = Modifier.fillMaxWidth()) { Text(menuText("今の位置を取得")) }
            OutlinedButton(onClick = { model.position.stop() }, modifier = Modifier.fillMaxWidth()) { Text(menuText("位置記録を停止")) }
        } else Button(enabled = registered, onClick = {
            generateSequence(context) { (it as? android.content.ContextWrapper)?.baseContext }
                .filterIsInstance<io.github.nevaseme.plandmk1.MainActivity>().firstOrNull()?.requestPositionAccess()
        }, modifier = Modifier.fillMaxWidth()) { Text(menuText("位置記録を開始")) }
        Text(menuText("平常時は5分ごと、災害中は1分ごとに位置を取得します。"), style = MaterialTheme.typography.bodyMedium)
        TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }) { Text(menuText("端末の位置情報設定を開く")) }
        TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) }) { Text(menuText("アプリの権限設定を開く")) }
    }
}
