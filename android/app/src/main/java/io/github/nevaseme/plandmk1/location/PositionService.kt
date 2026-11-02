package io.github.nevaseme.plandmk1.location

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import io.github.nevaseme.plandmk1.MainActivity
import io.github.nevaseme.plandmk1.PlanDApplication
import io.github.nevaseme.plandmk1.core.managedIncidentOptions
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class PositionService : Service() {
    private val app get() = application as PlanDApplication
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var recording: PositionRecording? = null
    private var token = -1L
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP) { app.position.stop(); stopSelf(); return START_NOT_STICKY }
        val requested = if (intent == null) app.position.prepareResume() ?: run { stopSelf(startId); return START_NOT_STICKY }
            else intent.getLongExtra("token", -1)
        if (recording != null && app.position.current(requested) && requested == token) return START_STICKY
        val notifications = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) notifications.createNotificationChannel(NotificationChannel(CHANNEL, "位置記録", NotificationManager.IMPORTANCE_LOW))
        try {
            if (Build.VERSION.SDK_INT >= 34) startForeground(NOTIFICATION, notification("位置を取得しています"), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION or ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            else if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIFICATION, notification("位置を取得しています"), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
            else startForeground(NOTIFICATION, notification("位置を取得しています"))
        } catch (e: Exception) {
            if(app.position.current(requested)) { app.position.stop(); app.position.error("位置情報の権限と端末の位置情報設定を確認してください") }
            stopSelf(startId); return START_NOT_STICKY
        }
        if (!app.position.current(requested)) { stopSelf(startId); return START_NOT_STICKY }
        recording?.stop(); app.motion.stop(token); app.impactMonitoring.stop(token); app.drops.stop(token); scope.coroutineContext.cancelChildren(); token = requested
        val group = app.installation.group.value
        val source = app.positionSourceFactory()
        val session = PositionRecording(scope, source, { fix ->
            check(app.position.current(requested) && app.installation.group.value == group)
            app.store.recordOwnPosition(group, app.installation.localId, fix).also {saved->
                if(saved) {app.recordsChanged()}
            }
        })
        recording = session
        if (!app.position.attach(token, session) { app.motion.stop(requested); app.impactMonitoring.stop(requested); app.drops.stop(requested); session.stop(); stopForeground(STOP_FOREGROUND_REMOVE); stopSelf() }) {
            stopSelf(startId); return START_NOT_STICKY
        }
        app.drops.start(requested, group, io.github.nevaseme.plandmk1.drops.DropSource { callback -> app.dropSourceFactory().open(callback) })
        scope.launch(Dispatchers.IO) {
            while (isActive) {
                runCatching { app.dropEvents.prune() }.onFailure { android.util.Log.w("PlanD-drops", "Retention cleanup failed", it) }
                delay(24L * 60 * 60 * 1_000)
            }
        }
        if (io.github.nevaseme.plandmk1.BuildConfig.DEBUG && app.motionCalibrationEnabled) app.motion.start(requested, group, object : io.github.nevaseme.plandmk1.motion.MotionSource {
            override fun open(listener: (io.github.nevaseme.plandmk1.motion.AccelerationSample) -> Unit) = app.motionSourceFactory().open(listener)
        })
        else {
            fun startObservation() = app.impactMonitoring.start(requested, group, app.impactWakePolicy.protect(app.motionSourceFactory()))
            startObservation()
            scope.launch {
                while (isActive) {
                    delay(1_000)
                    if (app.impactMonitoring.snapshot().sensor?.wakeUp == false && app.impactWakePolicy.needsSettings()) {
                        if (app.impactMonitoring.state.value.status != io.github.nevaseme.plandmk1.impact.ImpactMonitoringStatus.SETTINGS_REQUIRED) {
                            app.impactMonitoring.stop(requested)
                            startObservation()
                        }
                    } else if (app.impactMonitoring.state.value.status == io.github.nevaseme.plandmk1.impact.ImpactMonitoringStatus.SETTINGS_REQUIRED && !app.impactWakePolicy.needsSettings()) startObservation()
                    app.impactMonitoring.checkHealth()
                }
            }
        }
        session.start(false)
        scope.launch { session.state.collect {
            app.position.publish(requested, it)
            if (it.active) notifications.notify(NOTIFICATION, notification(it.message))
        } }
        scope.launch { app.installation.group.filter { it != group }.first(); app.position.stop() }
        scope.launch {
            var previous: Set<String>? = null
            combine(app.store.observeIncidents(group),app.quakes.observeRoster(group),app.quakes.observe(group)) {events,roster,quakes->managedIncidentOptions(events,roster,quakes).filter {!it.closed}.map {it.start.eventId}.toSet()}
                .distinctUntilChanged().collect { active ->
                    session.disaster(active.isNotEmpty())
                    if(previous != null && active.any { it !in previous!! }) session.refresh()
                    previous = active
                }
        }
        return START_STICKY
    }
    private fun notification(text: String): Notification {
        val open = PendingIntent.getActivity(this, 51, Intent(this, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 52, Intent(this, PositionService::class.java).setAction(STOP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL).setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("PlanD 位置記録中").setContentText(text).setContentIntent(open)
            .setOngoing(true).setOnlyAlertOnce(true).addAction(0, "位置記録を停止", stop).build()
    }
    override fun onDestroy() {
        app.motion.stop(token); app.impactMonitoring.stop(token); app.drops.stop(token); recording?.stop(); scope.cancel(); app.position.detached(token)
        super.onDestroy()
    }
    companion object { const val STOP = "io.github.nevaseme.plandmk1.STOP_POSITION"; private const val CHANNEL = "position"; private const val NOTIFICATION = 51 }
}
