package io.github.nevaseme.plandmk1.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nevaseme.plandmk1.sharing.*

@Composable
fun AutostartGuidanceHost(
    settings: AutostartSettings,
    eligible: Boolean,
    requested: Boolean,
    onRequestHandled: () -> Unit,
    openSettings: () -> AutostartDestination = settings::open,
) {
    if (settings.vendor == null) return
    val handled by settings.handled.collectAsStateWithLifecycle()
    var visible by rememberSaveable { mutableStateOf(false) }
    var destination by rememberSaveable { mutableStateOf<AutostartDestination?>(null) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var resumed by remember { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { resumed = true }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { resumed = false }
    LaunchedEffect(eligible, requested, handled, resumed) {
        if (resumed && (requested || (eligible && !handled))) visible = true
    }
    fun close(reviewed: Boolean) {
        settings.acknowledge(reviewed)
        visible = false
        destination = null
        onRequestHandled()
    }
    if (visible && resumed) AlertDialog(
        onDismissRequest = { close(false) },
        title = { Text(uiText("autostart_title")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(uiText("autostart_explanation"))
                Text(uiText(if (settings.vendor == AutostartVendor.XIAOMI) "autostart_xiaomi_steps" else "autostart_vivo_steps"))
                when (destination) {
                    AutostartDestination.APP_DETAILS -> Text(uiText("autostart_fallback"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    AutostartDestination.UNAVAILABLE -> Text(uiText("autostart_unavailable"), color = MaterialTheme.colorScheme.error)
                    else -> Unit
                }
                Button(onClick = { destination = openSettings() }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(uiText("autostart_open"))
                }
                Text(uiText("autostart_later_hint"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(onClick = { close(true) }, modifier = Modifier.heightIn(min = 48.dp)) { Text(uiText("autostart_reviewed")) } },
        dismissButton = { TextButton(onClick = { close(false) }, modifier = Modifier.heightIn(min = 48.dp)) { Text(uiText("autostart_later")) } },
    )
}
