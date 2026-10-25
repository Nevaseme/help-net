package io.github.nevaseme.plandmk1.location

import android.Manifest
import android.content.Intent
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.location.LocationManagerCompat
import androidx.lifecycle.Lifecycle
import io.github.nevaseme.plandmk1.PlanDApplication

/** Uses Android's own permission/settings screens; no app-screen layout changes. */
class PositionAccess(private val activity: ComponentActivity, private val app: PlanDApplication) {
    private val retry = PermissionRetry()
    private var busy = false
    private var registered = false
    private var group: String? = null
    private val permissions = activity.activityResultRegistry.register("position-permissions", activity, ActivityResultContracts.RequestMultiplePermissions()) {
        busy = false
        advance()
    }
    private val settings = activity.activityResultRegistry.register("position-settings", activity, ActivityResultContracts.StartActivityForResult()) {
        busy = false
        advance()
        if (app.position.hasLocationPermission()) app.position.refresh()
    }

    fun newVisit() { if (!busy) retry.newVisit() }
    fun registered(group: String, value: Boolean) {
        if (this.group != group) { this.group = group; retry.newVisit() }
        registered = value
        if (value) {
            app.position.preferences.enable(group)
            advance()
        }
    }
    fun requestAgain() { if (!busy) { retry.newVisit(); advance() } }

    private fun advance() {
        if (!registered || busy || !activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return
        val position = app.position
        if (!NotificationManagerCompat.from(activity).areNotificationsEnabled()) {
            val intent = if (Build.VERSION.SDK_INT >= 26) Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, activity.packageName) else details()
            ask("notifications", if (Build.VERSION.SDK_INT >= 33) arrayOf(Manifest.permission.POST_NOTIFICATIONS) else emptyArray(),
                intent, "記録状態と災害通知を受け取るため、通知を許可してください")
            return
        }
        if (!position.hasLocationPermission()) {
            ask("location", arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION),
                details(), "位置記録には位置情報の許可が必要です。許可を再確認してください")
            return
        }
        if (!position.state.value.active) position.start()
        if (!position.hasBackgroundPermission()) {
            ask("background", if (Build.VERSION.SDK_INT == 29) arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION) else emptyArray(),
                details(), "端末再起動後も位置記録を再開するため、位置情報を「常に許可」にしてください")
            return
        }
        if (!LocationManagerCompat.isLocationEnabled(activity.getSystemService(LocationManager::class.java))) {
            ask("location-switch", emptyArray(), Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS), "端末の位置情報をONにしてください")
        }
    }

    private fun ask(key: String, names: Array<String>, intent: Intent, explanation: String) {
        app.position.error(explanation)
        val requested = names.isEmpty() || app.position.preferences.wasRequested(key)
        val canAsk = names.any { ActivityCompat.shouldShowRequestPermissionRationale(activity, it) }
        when (retry.next(key, requested, canAsk)) {
            PermissionRetry.Action.REQUEST -> {
                app.position.preferences.requested(key)
                busy = true
                permissions.launch(names)
            }
            PermissionRetry.Action.SETTINGS -> {
                busy = true
                try { settings.launch(intent) }
                catch (_: android.content.ActivityNotFoundException) { busy = false; app.position.error(explanation) }
            }
            PermissionRetry.Action.WAIT -> Unit
        }
    }

    private fun details() = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${activity.packageName}"))
}
