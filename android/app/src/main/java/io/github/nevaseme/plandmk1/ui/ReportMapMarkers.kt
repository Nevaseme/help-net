package io.github.nevaseme.plandmk1.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import kotlin.math.roundToInt

/** Report icons stay centered on their recorded geographic coordinates. */
@Composable
internal fun ReportMapMarkers(map:MapLibreMap,reports:List<MapReport>,modifier:Modifier=Modifier,onReport:(MapReport)->Unit) {
    var revision by remember(map) { mutableIntStateOf(0) }
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    DisposableEffect(map) {
        val move=MapLibreMap.OnCameraMoveListener { revision++ }
        val idle=MapLibreMap.OnCameraIdleListener { revision++ }
        map.addOnCameraMoveListener(move);map.addOnCameraIdleListener(idle)
        onDispose {map.removeOnCameraMoveListener(move);map.removeOnCameraIdleListener(idle)}
    }
    val density=LocalDensity.current.density
    val projected=remember(map,reports,revision,viewport) {
        reports.mapNotNull {report->
            val p=map.projection.toScreenLocation(LatLng(report.point.latitude,report.point.longitude))
            if(p.x<0||p.y<0||p.x>viewport.width||p.y>viewport.height)null else report to p
        }
    }
    Box(modifier.onSizeChanged {viewport=it}) {
        projected.forEach {(report,point)->key(report.id) {
            Image(painterResource(reportIcon(report.urgency)),menuText("通報"),
                Modifier.offset {IntOffset((point.x-24*density).roundToInt(),(point.y-24*density).roundToInt())}
                    .size(48.dp).clickable(role=Role.Button) {onReport(report)}.padding(3.dp))
        } }
    }
}
