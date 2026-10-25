package io.github.nevaseme.plandmk1

import android.os.Bundle
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import io.github.nevaseme.plandmk1.ui.PlanApp
import io.github.nevaseme.plandmk1.ui.theme.PlanDMk1Theme
import io.github.nevaseme.plandmk1.sharing.SharingService
import io.github.nevaseme.plandmk1.location.PositionAccess
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest
import org.maplibre.android.MapLibre

class MainActivity : ComponentActivity() {
    private val model by lazy { ViewModelProvider(this)[PlanViewModel::class.java] }
    private lateinit var positionAccess: PositionAccess
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MapLibre.getInstance(this)
        val app = application as PlanDApplication
        positionAccess = PositionAccess(this, app)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                app.installation.group.collectLatest { group ->
                    app.store.observe(group).collect { residents ->
                        positionAccess.registered(group, residents.any { it.originId == app.installation.localId })
                    }
                }
            }
        }
        enableEdgeToEdge()
        setContent { PlanDMk1Theme { PlanApp(model) } }
        handleOpen(intent)
    }
    override fun onStart() { super.onStart(); positionAccess.newVisit() }
    fun requestPositionAccess() = positionAccess.requestAgain()
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); handleOpen(intent) }
    private fun handleOpen(intent: Intent) {
        if (intent.getBooleanExtra(SharingService.OPEN_SAFETY, false)) {
            model.openSafety.value = true; intent.removeExtra(SharingService.OPEN_SAFETY)
        }
    }
    override fun onStop() { model.maps.pause(); super.onStop() }
}
