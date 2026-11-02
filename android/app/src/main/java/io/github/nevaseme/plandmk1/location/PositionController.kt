package io.github.nevaseme.plandmk1.location

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class PositionController(private val context: Context) {
    val preferences = PositionPreferences(context)
    private val app get() = context.applicationContext as io.github.nevaseme.plandmk1.PlanDApplication
    private val mutable = MutableStateFlow(PositionState())
    val state = mutable.asStateFlow()
    private var generation = 0L
    private var recording: PositionRecording? = null
    private var finish: (() -> Unit)? = null
    var foreground = false

    fun start() {
        preferences.enable(app.installation.group.value)
        if (mutable.value.active) { refresh(); return }
        if (!foreground) { error("アプリを開いて位置記録を開始してください"); return }
        val token = prepareResume() ?: return
        try { ContextCompat.startForegroundService(context, Intent(context, PositionService::class.java).putExtra("token", token)) }
        catch (e: Exception) { stop(); error("開始できません。位置情報の権限を確認してください") }
    }
    internal fun prepareResume(): Long? {
        if (!PositionRecoveryPolicy.canResume(preferences.group == app.installation.group.value, foreground,
                hasLocationPermission(), hasBackgroundPermission(), Build.VERSION.SDK_INT)) {
            error("位置記録の再開には、アプリを開いて位置情報の権限を許可してください")
            return null
        }
        if (mutable.value.active) return generation
        mutable.value = PositionState(true, "位置を取得しています")
        return ++generation
    }
    fun hasLocationPermission() = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        .any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
    fun hasBackgroundPermission() = Build.VERSION.SDK_INT < 29 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** Called only from OS boot/package-replacement broadcasts, which permit an FGS start. */
    fun restoreAfterBoot() {
        val token = prepareResume() ?: return
        try { ContextCompat.startForegroundService(context, Intent(context, PositionService::class.java).putExtra("token", token)) }
        catch (e: Exception) { stop(); error("位置記録を再開するにはアプリを開いてください") }
    }
    fun stop() {
        // Stops this session. Persistent configuration is retained for the next visible visit/boot.
        generation++
        val end = finish; finish = null
        recording?.stop(); recording = null
        mutable.value = PositionState()
        end?.invoke()
        (context.applicationContext as? io.github.nevaseme.plandmk1.PlanDApplication)?.let {app->app.recordsChanged()}
    }
    fun refresh() { recording?.refresh() }
    fun error(text: String) { mutable.value = mutable.value.copy(message = text) }
    internal fun current(token: Long) = token == generation && mutable.value.active
    internal fun attach(token: Long, session: PositionRecording, end: () -> Unit): Boolean {
        if (!current(token)) return false
        recording = session; finish = end; return true
    }
    internal fun publish(token: Long, value: PositionState) { if(current(token)) mutable.value = value }
    internal fun detached(token: Long) {
        if(token == generation) { recording = null; finish = null; mutable.value = PositionState() }
    }
}
