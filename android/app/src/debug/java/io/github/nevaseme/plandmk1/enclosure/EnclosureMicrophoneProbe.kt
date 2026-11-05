package io.github.nevaseme.plandmk1.enclosure

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Short, explicit foreground trial. Returns features, never PCM; no playback or file/network IO. */
object EnclosureMicrophoneProbe {
    @SuppressLint("MissingPermission") // Permission is checked before construction; revocation still fails closed.
    suspend fun capture(context: Context, label: String): JSONObject = withContext(Dispatchers.IO) {
        require(label.isNotBlank() && label.length <= 120)
        check(context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) { "録音が許可されていません" }
        val manager = checkNotNull(context.getSystemService(AudioManager::class.java))
        val unprocessed = manager.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) == "true"
        val source = if (unprocessed) MediaRecorder.AudioSource.UNPROCESSED else MediaRecorder.AudioSource.VOICE_RECOGNITION
        val input = manager.getDevices(AudioManager.GET_DEVICES_INPUTS).firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_MIC }
        check(input != null) { "内蔵マイクを確認できません" }
        val rate = 48000
        val minimum = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        check(minimum > 0) { "48kHzの録音を利用できません" }
        val recorder = AudioRecord.Builder().setAudioSource(source).setAudioFormat(AudioFormat.Builder()
            .setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_IN_MONO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
            .setBufferSizeInBytes(maxOf(minimum * 2, 16384)).build()
        val pcm = ShortArray(1024)
        val window = DoubleArray(4096)
        val frames = JSONArray()
        val bands = EnclosureFeatures.passiveAudioBands48kHz
        var count = 0; var used = 0; var clipped = 0; var systemSilenced: Boolean? = null
        var silencingUnknown = Build.VERSION.SDK_INT < 29
        var startedAt = 0L
        var started = 0L
        var recording = false
        try {
            check(recorder.state == AudioRecord.STATE_INITIALIZED && recorder.sampleRate == rate) { "録音を初期化できません" }
            check(recorder.setPreferredDevice(input)) { "内蔵マイクを選べません" }
            recorder.startRecording()
            recording = recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING
            check(recording) { "録音を開始できません" }
            startedAt = System.currentTimeMillis()
            started = SystemClock.elapsedRealtimeNanos()
            while (count < rate * 3) {
                currentCoroutineContext().ensureActive()
                check(SystemClock.elapsedRealtimeNanos() - started < 5_000_000_000L) { "3秒分の音を取得できませんでした" }
                val read = recorder.read(pcm, 0, minOf(pcm.size, rate * 3 - count), AudioRecord.READ_NON_BLOCKING)
                check(read >= 0) { "録音の取得に失敗しました（$read）" }
                if (Build.VERSION.SDK_INT >= 29) {
                    val configuration = recorder.activeRecordingConfiguration
                    if (configuration == null) silencingUnknown = true
                    else systemSilenced = systemSilenced == true || configuration.isClientSilenced
                }
                for (i in 0 until read) {
                    if (pcm[i] == Short.MIN_VALUE || pcm[i] == Short.MAX_VALUE) clipped++
                    window[used++] = pcm[i].toDouble() / 32768.0
                    count++
                    if (used == window.size) {
                        val feature = EnclosureFeatures.audio(window, rate, bands)
                        frames.put(JSONObject().put("firstSample", count - window.size).put("sampleCount", window.size)
                            .put("dc", feature.dc).put("rms", feature.rms).put("acRms", feature.acRms).put("peak", feature.peak)
                            .put("bandEnergyFractions", JSONObject().also { json -> feature.bandEnergyFractions.forEach { (key, value) -> json.put(key, value ?: JSONObject.NULL) } })
                            .put("bandPowers", JSONObject(feature.bandPowers)).put("totalSpectralPower", feature.totalSpectralPower))
                        used = 0
                    }
                }
                pcm.fill(0)
                if (read == 0) delay(5)
            }
            val routed = recorder.routedDevice
            check(routed?.type == AudioDeviceInfo.TYPE_BUILTIN_MIC) { "録音経路が内蔵マイクではありません。接続機器を外して再試行してください" }
            JSONObject().put("method", "enclosure-passive-audio-features-v2").put("decision", "not-configured")
                .put("label", label).put("startedAt", startedAt).put("elapsedNanos", SystemClock.elapsedRealtimeNanos() - started)
                .put("sampleRateHz", rate).put("sampleCount", count).put("durationSeconds", count.toDouble() / rate)
                .put("source", if (unprocessed) "UNPROCESSED" else "VOICE_RECOGNITION")
                .put("unprocessedAdvertised", unprocessed).put("inputDeviceType", routed.type)
                .put("systemSilenced", if (systemSilenced == true) true else if (silencingUnknown) JSONObject.NULL else systemSilenced ?: JSONObject.NULL)
                .put("pcm16EndpointFraction", clipped.toDouble() / count)
                .put("bandsHz", JSONArray(bands.map { JSONObject().put("label", it.label).put("lowInclusive", it.lowHz)
                    .put("highExclusive", it.highHz).put("includesNyquist", it.highHz == rate / 2.0) }))
                .put("spectralPowerDefinition", "DC-removed PCM; symmetric Hann; one-sided FFT with DC bin excluded; sum(abs(FFT)^2*oneSidedFactor)/(N*sum(Hann^2)); normalized PCM squared")
                .put("partialFrameSamplesNotAnalyzed", used).put("frames", frames).put("rawAudioRetained", false)
        } finally {
            if (recording) runCatching { recorder.stop() }
            recorder.release()
            pcm.fill(0); window.fill(0.0)
        }
    }
}
