package io.github.nevaseme.plandmk1.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import io.github.nevaseme.plandmk1.MapPoint
import io.github.nevaseme.plandmk1.location.AndroidPositionSource
import io.github.nevaseme.plandmk1.location.PositionFix
import kotlinx.coroutines.*
import org.maplibre.android.maps.Style
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.PropertyFactory.*
import org.maplibre.geojson.*
import kotlin.math.*

class CurrentMapLocation {
    var fix by mutableStateOf<PositionFix?>(null)
    var request by mutableIntStateOf(0)
    var locate:()->Unit={}
}

/** Visible-map preview only: never persists or shares a location. */
@Composable fun rememberCurrentMapLocation(enabled:Boolean,camera:SituationCamera):CurrentMapLocation {
    val context=LocalContext.current
    val owner=LocalLifecycleOwner.current
    val english by rememberUpdatedState(LocalUiLanguage.current=="en")
    val state=remember {CurrentMapLocation()}
    val scope=rememberCoroutineScope()
    var centering by remember {mutableStateOf<Job?>(null)}
    fun allowed()=listOf(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION).any {ContextCompat.checkSelfPermission(context,it)==PackageManager.PERMISSION_GRANTED}
    fun feedback(denied:Boolean=false){android.widget.Toast.makeText(context,if(denied) {if(english)"Allow location access to show your location" else "現在地を表示するには位置情報を許可してください"}else {if(english)"Unable to find your location. Turn on location services and try again." else "現在地を取得できません。位置情報をオンにして再試行してください"},android.widget.Toast.LENGTH_LONG).show()}
    suspend fun acquire():PositionFix?=try {withTimeoutOrNull(15000){AndroidPositionSource(context).acquire()}}catch(e:CancellationException){throw e}catch(_:Exception){null}
    fun locate(){centering?.cancel();centering=scope.launch {val fix=acquire();if(fix!=null){state.fix=fix;camera.move(MapPoint(fix.latitude,fix.longitude))}else feedback()}}
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {if(allowed()){state.request++;locate()}else feedback(true)}
    SideEffect {state.locate={if(allowed())locate()else permission.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION,Manifest.permission.ACCESS_FINE_LOCATION))};camera.cancelLocationCenter={centering?.cancel()}}
    LaunchedEffect(enabled,state.request){
        if(enabled)owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED){
            try {while(isActive){if(allowed()){acquire()?.let {state.fix=it}}else state.fix=null
                if(state.fix?.let {System.currentTimeMillis()-it.acquiredAt>120000}==true)state.fix=null
                delay(15000)}}finally{state.fix=null;centering?.cancel()}
        }else {state.fix=null;centering?.cancel()}
    }
    DisposableEffect(Unit){onDispose{centering?.cancel();camera.cancelLocationCenter={}}}
    return state
}

/** Meter-based accuracy area plus a constant-size blue dot; no camera changes. */
fun drawCurrentMapLocation(style:Style,fix:PositionFix?){
    val source="my-location-point";val area="my-location-area"
    if(style.getSource(source)==null){
        style.addSource(GeoJsonSource(source,FeatureCollection.fromFeatures(emptyList())))
        style.addSource(GeoJsonSource(area,FeatureCollection.fromFeatures(emptyList())))
        style.addLayer(FillLayer("my-location-accuracy",area).withProperties(fillColor("#2675df"),fillOpacity(.12f)))
        style.addLayer(CircleLayer("my-location-dot",source).withProperties(circleRadius(7f),circleColor("#2675df"),circleStrokeColor("#ffffff"),circleStrokeWidth(3f)))
    }
    val dot=if(fix==null)emptyList()else listOf(Feature.fromGeometry(Point.fromLngLat(fix.longitude,fix.latitude)))
    val ring=if(fix==null)emptyList()else (0..64).map {i->
        val angle=i*2*PI/64;val d=fix.accuracy/6371000;val lat=Math.toRadians(fix.latitude);val lon=Math.toRadians(fix.longitude)
        val y=asin(sin(lat)*cos(d)+cos(lat)*sin(d)*cos(angle));val x=lon+atan2(sin(angle)*sin(d)*cos(lat),cos(d)-sin(lat)*sin(y))
        Point.fromLngLat(Math.toDegrees(x),Math.toDegrees(y))
    }
    style.getSourceAs<GeoJsonSource>(source)?.setGeoJson(FeatureCollection.fromFeatures(dot))
    style.getSourceAs<GeoJsonSource>(area)?.setGeoJson(FeatureCollection.fromFeatures(if(ring.isEmpty())emptyList()else listOf(Feature.fromGeometry(Polygon.fromLngLats(listOf(ring))))))
}
