package io.github.nevaseme.plandmk1.diagnostics

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.*
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import io.github.nevaseme.plandmk1.PlanDApplication
import io.github.nevaseme.plandmk1.core.managedIncidentOptions
import io.github.nevaseme.plandmk1.drops.*
import io.github.nevaseme.plandmk1.location.*
import io.github.nevaseme.plandmk1.motion.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/** Shell-only, opt-in, offline power experiment. Never included in release. */
class PowerComparisonService : Service() {
    private val app get() = application as PlanDApplication
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val power by lazy { getSystemService(PowerManager::class.java) }
    private val battery by lazy { getSystemService(BatteryManager::class.java) }
    private val alarm by lazy { getSystemService(AlarmManager::class.java) }
    private var sequence = listOf("maps", "position", "acceleration", "acceleration", "position", "maps")
    private var preflight = false
    private var levelPreflight = false
    private var lastRecordedLevel: Int? = null
    private var sampleJob: Job? = null
    private var boundaryJob: Job? = null
    private val bufferedSamples = mutableListOf<JsonObject>()
    private var alarmLatenessMs = 0L
    private var runId: String? = null
    private var log: File? = null
    private var phase = -1
    private var phaseSeconds = 900L
    private var armed = false
    private var finished = false
    private var deadline = 0L
    private var startSnapshot: JsonObject? = null
    private var position: PositionRecording? = null
    private var phaseJob: Job? = null
    private var drop: DropRecording? = null
    private var motion: MotionSubscription? = null
    private var wake: PowerManager.WakeLock? = null
    private var group = ""
    private var screenOns = 0
    private var positionRequests = 0
    private var positionFixes = 0
    private var disaster = false
    private val disasterModes = mutableSetOf<Boolean>()
    private val readingsLock = Any()
    private var received = 0L
    private var firstNanos = 0L
    private var lastNanos = 0L
    private var maxGapNanos = 0L
    private var maxDeliveryDelayNanos = 0L
    private var info: MotionSensorInfo? = null
    private var registeredReceiver = false
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_ON && phase >= 0) screenOns++
            if (intent?.action == Intent.ACTION_BATTERY_CHANGED && armed && deadline > 0) captureBattery("broadcast")
            if (intent?.action == Intent.ACTION_POWER_CONNECTED && armed && deadline > 0) finish("charging-interrupted")
            if (intent?.action == Intent.ACTION_POWER_DISCONNECTED && armed && deadline == 0L) warmup()
        }
    }
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onCreate() {
        super.onCreate()
        ContextCompat.registerReceiver(this, receiver, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON); addAction(Intent.ACTION_POWER_CONNECTED); addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(Intent.ACTION_BATTERY_CHANGED)
        }, ContextCompat.RECEIVER_NOT_EXPORTED)
        registeredReceiver = true
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (Build.VERSION.SDK_INT < 29) { stopSelf(); return START_NOT_STICKY }
        try {
            val notifications = getSystemService(NotificationManager::class.java)
            notifications.createNotificationChannel(NotificationChannel(CHANNEL, "電力比較試験", NotificationManager.IMPORTANCE_LOW))
            startForeground(98, notification("測定準備中"), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
            when (intent?.action) {
                ARM -> {
                    if (armed) return START_NOT_STICKY
                    check(Build.MODEL == "V2417A") { "This experiment targets V2417A only" }
                    levelPreflight = intent.getBooleanExtra("levelPreflight", false)
                    if (levelPreflight) {
                        val status = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                        check(status?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) == 100 &&
                            status.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) in 20..90) {
                            "Level preflight requires actual battery level 20–90%; full-charge plateau is excluded"
                        }
                    }
                    check(!app.position.state.value.active) { "Stop normal position recording explicitly before arming; restore it after the trial" }
                    check(Build.VERSION.SDK_INT < 31 || alarm.canScheduleExactAlarms()) { "Exact alarm permission is required; no inexact fallback" }
                    if (!levelPreflight) check(packageManager.checkPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION, MAPS) == PackageManager.PERMISSION_GRANTED) { "Maps always-location permission is required" }
                    preflight = levelPreflight || intent.getBooleanExtra("preflight", false)
                    // No boolean override can authorize an overnight run before a successful, reviewed v3 preflight.
                    check(preflight) { "Long trials disabled until the new preflight and analysis are validated" }
                    sequence = if (levelPreflight) listOf("position", "acceleration") else listOf("maps", "acceleration")
                    phaseSeconds = if (levelPreflight) 900 else 600
                    check(battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER) > 0) { "Charge counter unavailable" }
                    group = app.installation.group.value
                    runId = UUID.randomUUID().toString()
                    val directory = File(filesDir, "power-comparison").apply { check(isDirectory || mkdirs()) }
                    log = File(directory, "$runId.jsonl")
                    write(buildJsonObject {
                        put("kind", "armed"); put("runId", runId); put("model", Build.MODEL); put("firmware", Build.DISPLAY)
                        put("protocolVersion", if (levelPreflight) 3 else 2); put("preflight", preflight)
                        put("phaseSeconds", phaseSeconds); put("warmupSeconds", 300)
                        put("commonOverhead", if (levelPreflight) "diagnostic-location-foreground-service; battery broadcasts and boundaries only; no polling or baseline measurement wakelock"
                            else "diagnostic-location-foreground-service; battery reads every 5 awake seconds and on system battery broadcasts; no baseline measurement wakelock")
                        put("sequence", JsonArray(sequence.map(::JsonPrimitive)))
                        put("originalPositionActive", app.position.state.value.active)
                    })
                    // The operator manages normal recording explicitly, including restoration after the trial.
                    armed = true
                    notify("USBを外すと5分の準備時間後に測定します")
                    scope.launch { app.installation.group.filter { it != group }.first(); finish("group-changed") }
                    if (!plugged()) warmup()
                }
                TICK -> if (armed && intent.getStringExtra("runId") == runId && intent.getLongExtra("deadline", -1) == deadline) {
                    // Exact alarms can be delivered marginally early; wait without holding the CPU awake.
                    boundaryJob?.cancel()
                    boundaryJob = scope.launch {
                        val remaining = deadline - SystemClock.elapsedRealtime()
                        if (remaining > 0) delay(remaining)
                        runCatching { advance() }.onFailure { fail(it) }
                    }
                }
                    else if (!armed) stopSelf()
                STOP -> finish("operator-stopped")
                else -> { if (!armed) stopSelf() }
            }
        } catch (error: Exception) {
            fail(error)
        }
        return START_NOT_STICKY
    }
    private fun plugged(): Boolean = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        ?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) != 0
    private fun warmup() {
        if (finished) return
        write(buildJsonObject { put("kind", "unplugged-warmup"); put("battery", snapshot()) })
        sampleJob?.cancel()
        sampleJob = if (levelPreflight) null else scope.launch {
            while (isActive && !finished) {
                captureBattery("awake-poll")
                delay(5000) // Does not wake a suspended CPU. Gaps remain visible and invalidate integration.
            }
        }
        schedule(300_000)
        notify("準備時間：画面を消して置いてください")
    }
    private fun tickIntent() = PendingIntent.getService(this, 980, Intent(this, PowerComparisonService::class.java)
        .setAction(TICK).putExtra("runId", runId).putExtra("deadline", deadline), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    private fun schedule(delayMs: Long) {
        deadline = SystemClock.elapsedRealtime() + delayMs
        alarm.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, deadline, tickIntent())
        write(buildJsonObject { put("kind", "deadline"); put("phase", phase); put("elapsedMs", deadline) })
    }
    private fun advance() {
        if (finished || plugged()) { finish("charging-interrupted"); return }
        alarmLatenessMs = SystemClock.elapsedRealtime() - deadline
        if (phase >= 0) endPhase()
        else write(buildJsonObject { put("kind", "warmup-end"); put("alarmLatenessMs", alarmLatenessMs) })
        flushSamples()
        if (alarmLatenessMs > 5000) { finish("deadline-missed"); return }
        clearPhase()
        phase++
        if (phase >= sequence.size) { finish("completed"); return }
        val mode = sequence[phase]
        if (levelPreflight && snapshot()["level"]!!.jsonPrimitive.int !in 20..90) { finish("level-out-of-range"); return }
        screenOns = 0; positionRequests = 0; positionFixes = 0; disaster = false; info = null
        disasterModes.clear()
        synchronized(readingsLock) { received = 0; firstNanos = 0; lastNanos = 0; maxGapNanos = 0; maxDeliveryDelayNanos = 0 }
        if (mode != "maps") {
            phaseJob = SupervisorJob(scope.coroutineContext[Job])
            val worker = CoroutineScope(scope.coroutineContext + phaseJob!!)
            val source = AndroidPositionSource(this)
            position = PositionRecording(worker, { positionRequests++; source.acquire() }, { fix ->
                positionFixes++
                app.store.recordOwnPosition(group, app.installation.localId, fix).also { saved -> if (saved) { app.sharing.changed(); app.lab.changed() } }
            }).also { it.start(false) }
            worker.launch {
                combine(app.store.observeIncidents(group), app.quakes.observeRoster(group), app.quakes.observe(group)) { events, roster, quakes ->
                    managedIncidentOptions(events, roster, quakes).any { !it.closed }
                }.distinctUntilChanged().collect { disaster = it; disasterModes.add(it); position?.disaster(it) }
            }
            drop = DropRecording(SystemClock::elapsedRealtimeNanos, app.dropEvents::save).also { it.start(1, group, AndroidDropSource(this)) }
        }
        if (mode == "acceleration") {
            val trace = MotionTrace(System.currentTimeMillis(), SystemClock.elapsedRealtimeNanos())
            wake = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PlanD:PowerComparison").also { it.acquire((phaseSeconds + 60) * 1000) }
            motion = AndroidMotionSource(this).open { sample ->
                trace.add(sample)
                synchronized(readingsLock) {
                    if (received == 0L) firstNanos = sample.elapsedNanos
                    else maxGapNanos = maxOf(maxGapNanos, sample.elapsedNanos - lastNanos)
                    received++; lastNanos = sample.elapsedNanos
                    maxDeliveryDelayNanos = maxOf(maxDeliveryDelayNanos, SystemClock.elapsedRealtimeNanos() - sample.elapsedNanos)
                }
            }
            checkNotNull(motion) { "Accelerometer registration failed" }
            info = motion!!.sensor
        }
        startSnapshot = snapshot()
        write(buildJsonObject { put("kind", "phase-start"); put("phase", phase); put("mode", mode); put("battery", startSnapshot!!); put("requestedPeriodUs", info?.requestedPeriodUs); put("sensorWakeUp", info?.wakeUp) })
        notify("電力比較 ${phase + 1}/${sequence.size}：画面を消したままお待ちください")
        schedule(phaseSeconds * 1000)
    }
    private fun endPhase() {
        val end = snapshot()
        write(buildJsonObject {
            put("kind", "phase-end"); put("phase", phase); put("mode", sequence[phase]); put("start", startSnapshot!!); put("end", end)
            put("alarmLatenessMs", alarmLatenessMs)
            put("screenOnEvents", screenOns); put("positionRequests", positionRequests); put("positionFixes", positionFixes); put("disasterAtEnd", disaster)
            put("disasterModes", JsonArray(disasterModes.map(::JsonPrimitive)))
            put("dropRegistered", drop?.state?.value?.active ?: false); put("wakeLockHeldAtEnd", wake?.isHeld ?: false)
            synchronized(readingsLock) {
                put("samples", received); put("firstSampleNanos", firstNanos); put("lastSampleNanos", lastNanos)
                put("maxGapNanos", maxGapNanos); put("maxDeliveryDelayNanos", maxDeliveryDelayNanos)
            }
        })
    }
    private fun snapshot() = buildJsonObject {
        val status = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        put("elapsedMs", SystemClock.elapsedRealtime()); put("wallMs", System.currentTimeMillis())
        put("chargeUah", battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER))
        put("currentUa", battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW))
        put("averageCurrentUa", battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE))
        put("energyNwh", battery.getLongProperty(BatteryManager.BATTERY_PROPERTY_ENERGY_COUNTER))
        put("level", status?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)); put("temperatureTenthsC", status?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1))
        put("levelScale", status?.getIntExtra(BatteryManager.EXTRA_SCALE, -1))
        put("voltageMv", status?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)); put("plugged", status?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1))
        put("screenInteractive", power.isInteractive)
        put("uptimeMs", SystemClock.uptimeMillis())
    }
    private fun captureBattery(source: String) {
        if (finished) return
        val reading = snapshot()
        val level = reading["level"]?.jsonPrimitive?.intOrNull
        bufferedSamples.add(buildJsonObject {
            put("kind", "battery-sample"); put("phase", phase); put("source", source); put("battery", reading)
        })
        if (bufferedSamples.size >= 60 || (levelPreflight && level != lastRecordedLevel)) flushSamples()
        lastRecordedLevel = level
    }
    private fun flushSamples() {
        if (bufferedSamples.isEmpty()) return
        val destination = log ?: return
        FileOutputStream(destination, true).use { stream ->
            bufferedSamples.forEach { stream.write((it.toString() + "\n").toByteArray()) }
            stream.fd.sync()
        }
        bufferedSamples.clear()
    }
    private fun fail(error: Throwable) {
        android.util.Log.e("PlanD-power", "Power comparison failed", error)
        runCatching { write(buildJsonObject { put("kind", "error"); put("error", error.javaClass.simpleName); put("message", error.message) }) }
        finish("error")
    }
    private fun clearPhase() {
        position?.stop(); position = null; phaseJob?.cancel(); phaseJob = null
        drop?.stop(1); drop = null
        try { motion?.close?.invoke() } finally { motion = null; if (wake?.isHeld == true) wake?.release(); wake = null }
    }
    private fun finish(reason: String) {
        if (finished) return
        finished = true; armed = false
        try {
            sampleJob?.cancel(); sampleJob = null
            boundaryJob?.cancel(); boundaryJob = null
            alarm.cancel(tickIntent())
            clearPhase()
            flushSamples()
            write(buildJsonObject { put("kind", "finished"); put("reason", reason); put("battery", snapshot()) })
        } finally {
            stopForeground(STOP_FOREGROUND_REMOVE)
            getSystemService(NotificationManager::class.java).notify(99,
                NotificationCompat.Builder(this, CHANNEL).setSmallIcon(android.R.drawable.ic_menu_info_details)
                    .setContentTitle("PlanD 電力試験終了")
                    .setContentText(if (reason == "completed") "記録が揃いました。USBを接続して解析してください" else "試験を終了しました（$reason）。USBを接続して記録を確認してください")
                    .setAutoCancel(true).build())
            stopSelf()
        }
    }
    private fun write(value: JsonObject) {
        val destination = log ?: return
        FileOutputStream(destination, true).use { it.write((value.toString() + "\n").toByteArray()); it.fd.sync() }
    }
    private fun notification(message: String): Notification {
        val stop = PendingIntent.getService(this, 981, Intent(this, PowerComparisonService::class.java).setAction(STOP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL).setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentTitle("PlanD 電力比較試験").setContentText(message).setOngoing(true).setOnlyAlertOnce(true)
            .addAction(0, "測定を中止", stop).build()
    }
    private fun notify(message: String) = getSystemService(NotificationManager::class.java).notify(98, notification(message))
    override fun onDestroy() {
        try { alarm.cancel(tickIntent()); clearPhase() }
        finally {
            scope.cancel()
            if (registeredReceiver) unregisterReceiver(receiver)
            super.onDestroy()
        }
    }
    companion object {
        private const val CHANNEL = "power-comparison"
        private const val MAPS = "com.google.android.apps.maps"
        const val ARM = "io.github.nevaseme.plandmk1.POWER_ARM"
        const val TICK = "io.github.nevaseme.plandmk1.POWER_TICK"
        const val STOP = "io.github.nevaseme.plandmk1.POWER_STOP"
    }
}
