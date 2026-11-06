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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import io.github.nevaseme.plandmk1.cloud.GroupMunicipality
import org.maplibre.android.MapLibre

class MainActivity : ComponentActivity() {
    private val model by lazy { ViewModelProvider(this)[PlanViewModel::class.java] }
    private lateinit var positionAccess: PositionAccess
    val permissionRequestActive: kotlinx.coroutines.flow.StateFlow<Boolean> get() = positionAccess.requesting
    private val networkCallback=object:android.net.ConnectivityManager.NetworkCallback(){
        override fun onCapabilitiesChanged(network:android.net.Network, capabilities:android.net.NetworkCapabilities){
            if(capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED))runOnUiThread {
                if(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)){
                    val app=application as PlanDApplication
                    app.cloud.state.value.venue?.let {GroupMunicipality.forDemo(it.positionMode,it.closed)?.code}?.let(app.offlineMaps::prepareCity)
                }
            }
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MapLibre.getInstance(this)
        // The map provider allows at most four concurrent requests for offline downloads.
        org.maplibre.android.module.http.HttpRequestUtil.setOkHttpClient(okhttp3.OkHttpClient.Builder()
            .dispatcher(okhttp3.Dispatcher().apply {maxRequests=4;maxRequestsPerHost=4})
            .addInterceptor { chain->chain.proceed(chain.request().newBuilder().header("User-Agent","Tasukeai-Android/${BuildConfig.VERSION_NAME} (io.github.nevaseme.plandmk1)").build()) }.build())
        val app = application as PlanDApplication
        positionAccess = PositionAccess(this, app)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                app.cloud.state.map {state->state.venue?.let {GroupMunicipality.forDemo(it.positionMode,it.closed)?.code}}
                    .distinctUntilChanged().collect {code->if(code!=null)app.offlineMaps.prepareCity(code)}
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                app.installation.group.collectLatest { group ->
                    combine(app.store.observe(group),app.cloud.state) { residents,cloud->
                        residents.any {it.originId==app.installation.localId} || (cloud.groupId==group && app.cloud.hasSession(group))
                    }.collect { registered ->
                        positionAccess.registered(group, registered)
                    }
                }
            }
        }
        enableEdgeToEdge()
        setContent { PlanDMk1Theme { PlanApp(model) } }
        handleOpen(intent)
    }
    override fun onStart() {
        super.onStart(); positionAccess.newVisit()
        getSystemService(android.net.ConnectivityManager::class.java).registerDefaultNetworkCallback(networkCallback)
    }
    fun requestPositionAccess() = positionAccess.requestAgain()
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); handleOpen(intent) }
    private fun handleOpen(intent: Intent) {
        if (intent.getBooleanExtra(SharingService.OPEN_SAFETY, false)) {
            model.openSafety.value = true; intent.removeExtra(SharingService.OPEN_SAFETY)
        }
    }
    override fun onStop() {
        getSystemService(android.net.ConnectivityManager::class.java).unregisterNetworkCallback(networkCallback)
        model.maps.pause(); super.onStop()
    }
}
