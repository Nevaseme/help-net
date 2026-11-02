package io.github.nevaseme.plandmk1.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import io.github.nevaseme.plandmk1.MapPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

/** Resolve once before mounting the map. Location updates never drag an open picker. */
@Composable fun HomeLocationPicker(
    fallback: MapPoint,
    onPick: (MapPoint)->Unit,
    onCancel: ()->Unit,
    locate: suspend ()->MapPoint,
) {
    val context=LocalContext.current
    fun allowed()=listOf(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION)
        .any {ContextCompat.checkSelfPermission(context,it)==PackageManager.PERMISSION_GRANTED}
    var permissionReady by remember {mutableStateOf(allowed())}
    var permissionChecked by remember {mutableStateOf(permissionReady)}
    var center by remember {mutableStateOf<MapPoint?>(null)}
    var unavailable by remember {mutableStateOf(false)}
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        permissionReady=allowed();permissionChecked=true
    }
    LaunchedEffect(Unit) {
        if(!permissionChecked)permission.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION,Manifest.permission.ACCESS_FINE_LOCATION))
    }
    LaunchedEffect(permissionChecked,permissionReady) {
        if(!permissionChecked)return@LaunchedEffect
        val current=if(permissionReady)try {withTimeoutOrNull(15_000){locate()}}catch(e:CancellationException){throw e}catch(_:Exception){null}else null
        unavailable=current==null
        center=current?:fallback
    }
    Box(Modifier.fillMaxSize()) {
        center?.let {point->BuildingLocationPicker(point,{p,_->onPick(p)},onCancel,initialMessage=if(unavailable)"現在地を取得できません。住所検索か地図で位置を選んでください" else "")}
            ?: Column(Modifier.align(Alignment.Center),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator();Text("現在地を確認しています…")
            }
    }
}
