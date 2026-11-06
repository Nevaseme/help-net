package io.github.nevaseme.plandmk1.sharing

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.provider.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

enum class AutostartVendor {
    XIAOMI, VIVO;

    companion object {
        fun identify(manufacturer: String, brand: String): AutostartVendor? {
            val names = setOf(manufacturer.lowercase(Locale.ROOT), brand.lowercase(Locale.ROOT))
            return when {
                names.any { it in setOf("xiaomi", "redmi", "poco") } -> XIAOMI
                names.any { it in setOf("vivo", "iqoo") } -> VIVO
                else -> null
            }
        }
    }
}

enum class AutostartDestination { DIRECT, APP_DETAILS, UNAVAILABLE }

/** Acknowledges an explanation, NOT an OS permission. OEM autostart has no common permission API. */
class AutostartSettings(
    private val context: Context,
    val vendor: AutostartVendor? = AutostartVendor.identify(Build.MANUFACTURER, Build.BRAND),
    private val preferences: SharedPreferences = context.getSharedPreferences("autostart-guidance", Context.MODE_PRIVATE),
) {
    private val mutableHandled = MutableStateFlow(preferences.contains("acknowledgement-v1"))
    val handled = mutableHandled.asStateFlow()

    private val components get() = when (vendor) {
        AutostartVendor.XIAOMI -> listOf(ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"))
        AutostartVendor.VIVO -> listOf(
            ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
            ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager"),
        )
        null -> emptyList()
    }

    val available: Boolean get() = components.any { component ->
        try {
            val info = context.packageManager.getActivityInfo(component, 0)
            info.enabled && info.exported && info.applicationInfo?.enabled != false &&
                (info.permission == null || context.checkSelfPermission(info.permission) == android.content.pm.PackageManager.PERMISSION_GRANTED)
        } catch (_: android.content.pm.PackageManager.NameNotFoundException) { false
        } catch (_: SecurityException) { false }
    }

    fun acknowledge(reviewed: Boolean) {
        // No "enabled" flag: visiting settings or dismissing does not prove permission was granted.
        preferences.edit().putString("acknowledgement-v1", if (reviewed) "reviewed" else "later").apply()
        mutableHandled.value = true
    }

    fun open(): AutostartDestination = openWith { context.startActivity(it) }

    internal fun openWith(launch: (Intent) -> Unit): AutostartDestination {
        for (component in components) {
            try {
                launch(Intent().setComponent(component).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return AutostartDestination.DIRECT
            } catch (_: ActivityNotFoundException) { /* OEM/OS variant: try the next supported entry. */
            } catch (_: SecurityException) { /* Some OEM screens are not exported. */ }
        }
        return try {
            launch(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            AutostartDestination.APP_DETAILS
        } catch (_: ActivityNotFoundException) { AutostartDestination.UNAVAILABLE
        } catch (_: SecurityException) { AutostartDestination.UNAVAILABLE }
    }
}
