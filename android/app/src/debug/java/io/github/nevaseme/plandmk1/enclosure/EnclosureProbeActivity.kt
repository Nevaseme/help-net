package io.github.nevaseme.plandmk1.enclosure

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.AtomicFile
import java.io.File
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.nevaseme.plandmk1.ui.theme.PlanDMk1Theme
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject

/** Standalone debug trial screen. Opening it never starts sensors or the microphone. */
class EnclosureProbeActivity : ComponentActivity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var running: Job? = null
    private var label by mutableStateOf("")
    private var status by mutableStateOf("試行名を入力して測定を選んでください")
    private var busy by mutableStateOf(false)
    private var seconds by mutableIntStateOf(0)
    private var reports by mutableStateOf<List<String>>(emptyList())
    private var pendingAudioLabel: String? = null
    private var exportText: String? = null
    private val persistCompleted by lazy {
        @Suppress("DEPRECATION")
        packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
            .metaData?.getBoolean("enclosure.persist_completed_trials", false) == true
    }
    private val completedFile by lazy { AtomicFile(File(filesDir, "enclosure-trials.json")) }
    private var storageReadable = true
    private fun reportDocument(values: List<String>) = JSONObject().put("format", "enclosure-trials-v1")
        .put("classification", "not-configured").put("trials", JSONArray(values.map(::JSONObject))).toString(2)
    private fun restoreCompleted() {
        if (!persistCompleted || !completedFile.baseFile.exists()) return
        try {
            val document = JSONObject(completedFile.openRead().bufferedReader().use { it.readText() })
            check(document.getString("format") == "enclosure-trials-v1")
            val trials = document.getJSONArray("trials")
            reports = (0 until trials.length()).map { trials.getJSONObject(it).toString() }
            status = "保存した測定結果を復元しました"
        } catch (_: Exception) {
            storageReadable = false
            status = "保存済みの結果を読み込めません。元の保存ファイルを保護するため、自動保存は停止しています"
        }
    }
    private fun saveCompleted() {
        check(storageReadable) { "元の保存ファイルを保護しています。結果を書き出してください" }
        val stream = completedFile.startWrite()
        try {
            stream.write(reportDocument(reports).toByteArray(Charsets.UTF_8))
            completedFile.finishWrite(stream)
        } catch (error: Exception) { completedFile.failWrite(stream); throw error }
    }
    private val requestAudio = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val trial = pendingAudioLabel
        pendingAudioLabel = null
        if (granted && trial != null) startAudio(trial)
        else status = "録音しませんでした。センサーだけの測定は使えます"
    }
    private val export = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val text = exportText; exportText = null
        if (uri != null && text != null) {
            runCatching { checkNotNull(contentResolver.openOutputStream(uri)).bufferedWriter().use { it.write(text) } }
                .onSuccess { status = "測定結果を書き出しました（生音声は含みません）" }
                .onFailure { status = "書き出せませんでした：${it.message}" }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (persistCompleted) window.attributes = window.attributes.apply { screenBrightness = .35f }
        restoreCompleted()
        setContent { PlanDMk1Theme {
            Surface(Modifier.fillMaxSize()) {
                Column(Modifier.statusBarsPadding().navigationBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("閉所の測定", style = MaterialTheme.typography.headlineMedium)
                    Text("検証専用。ポケット・かばん・暗室・覆いを別々に記録します。閉所や人の埋没を判定する画面ではありません。")
                    OutlinedTextField(label, { if (it.length <= 120) label = it }, label = { Text("試行名（例：かばん・表向き・1回目）") }, enabled = !busy, modifier = Modifier.fillMaxWidth())
                    Text("センサー測定は20秒です。画面を開いたまま、最初の5秒は露出、次の10秒は指定条件、最後の5秒は露出に戻します。画面を離れると中止します。")
                    Button(onClick = { startSensors(label.trim()) }, enabled = !busy && label.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("センサーを20秒測る") }
                    Text("音響は5秒の準備時間の後、内蔵マイクで3秒測ります。準備中に指定条件へ置いてください。音量・周波数の特徴だけを残し、生音声は保存・送信しません。音の再生は行いません。")
                    OutlinedButton(onClick = {
                        val trial = label.trim()
                        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startAudio(trial)
                        else { pendingAudioLabel = trial; requestAudio.launch(Manifest.permission.RECORD_AUDIO) }
                    }, enabled = !busy && label.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("5秒後に環境音を3秒測る") }
                    Text(status, style = MaterialTheme.typography.titleMedium)
                    if (busy) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text("経過 ${seconds}秒")
                        OutlinedButton(onClick = { running?.cancel() }, modifier = Modifier.fillMaxWidth()) { Text("測定を中止") }
                    }
                    Text("完了した測定：${reports.size}件")
                    OutlinedButton(onClick = {
                        exportText = reportDocument(reports)
                        export.launch("enclosure-trials-${System.currentTimeMillis()}.json")
                    }, enabled = !busy && reports.isNotEmpty(), modifier = Modifier.fillMaxWidth()) { Text("結果を書き出す") }
                    Text(if (persistCompleted) "完了した結果はこの測定アプリ内に自動保存します。再起動後も復元できます。共有・保管用には「結果を書き出す」を使ってください。アプリを削除すると内部の結果は消えます。"
                        else "結果はこの画面のメモリに保持します。画面を閉じる前に書き出してください。")
                    reports.lastOrNull()?.let { Text(summary(JSONObject(it))) }
                }
            }
        } }
    }

    private fun startSensors(trial: String) = startTrial("センサーを測定中", 20) {
        EnclosureSensorProbe.capture(this, trial, 20_000, includeSensorSamples = true)
    }
    private fun startAudio(trial: String) = startTrial("5秒後に環境音を測定します", 8) {
        for (remaining in 5 downTo 1) {
            status = "指定した条件にしてください。測定開始まで${remaining}秒"
            delay(1000)
        }
        status = "環境音を3秒測定中"
        EnclosureMicrophoneProbe.capture(this, trial)
    }
    private fun startTrial(message: String, expectedSeconds: Int, capture: suspend () -> JSONObject) {
        if (busy || isFinishing) return
        busy = true; seconds = 0; status = message
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        running = scope.launch {
            val timer = launch { while (true) { delay(1000); seconds++; if (expectedSeconds == 20) status = when { seconds < 5 -> "露出した状態で待ってください"; seconds < 15 -> "指定した条件にしてください"; else -> "覆いを外し、露出に戻してください" } } }
            try {
                val result = capture()
                @Suppress("DEPRECATION")
                val appInfo = packageManager.getPackageInfo(packageName, 0)
                result.put("device", JSONObject().put("manufacturer", android.os.Build.MANUFACTURER)
                    .put("model", android.os.Build.MODEL).put("device", android.os.Build.DEVICE).put("api", android.os.Build.VERSION.SDK_INT))
                    .put("application", JSONObject().put("package", packageName).put("versionName", appInfo.versionName ?: JSONObject.NULL)
                        .put("versionCode", if (android.os.Build.VERSION.SDK_INT >= 28) appInfo.longVersionCode else {
                            @Suppress("DEPRECATION") appInfo.versionCode.toLong()
                        }))
                    .put("featureVersion", "enclosure-features-2")
                    .put("brightnessOverride", if (persistCompleted) .35 else JSONObject.NULL)
                reports = reports + result.toString()
                status = if (persistCompleted) runCatching { saveCompleted() }.fold(
                    onSuccess = { "測定が完了しました" },
                    onFailure = { "測定は完了しましたが、自動保存できませんでした。結果を書き出してください：${it.message}" },
                ) else "測定が完了しました"
            } catch (_: CancellationException) { status = "測定を中止しました。途中の結果は残していません" }
            catch (error: Exception) { status = "測定できませんでした：${error.message}" }
            finally { timer.cancel(); busy = false; window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
        }
    }

    private fun summary(result: JSONObject): String = if (result.has("channels")) {
        val channels = result.getJSONArray("channels")
        (0 until channels.length()).joinToString("\n") { index ->
            val channel = channels.getJSONObject(index)
            val features = channel.getJSONObject("features")
            val count = if (features.has("magnitude")) features.getJSONObject("magnitude").optInt("count") else features.optInt("count")
            "${channel.getString("channel")}：${channel.getString("state")} / ${count}件"
        }
    } else "音響：${result.getDouble("durationSeconds")}秒 / ${result.getString("source")}\n端末側の無音化：${if (result.isNull("systemSilenced")) "不明" else result.getBoolean("systemSilenced")}\n生音声は保持していません"

    override fun onStop() { running?.cancel(); super.onStop() }
    override fun onDestroy() { pendingAudioLabel = null; scope.cancel(); super.onDestroy() }
}
