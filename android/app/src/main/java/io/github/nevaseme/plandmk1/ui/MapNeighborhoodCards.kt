package io.github.nevaseme.plandmk1.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import kotlin.math.roundToInt

/** Fixed-anchor neighborhood cards render above the native people/report annotation layer. */
@Composable
internal fun MapNeighborhoodCards(map:MapLibreMap,reports:List<MapReport>,neighborhoods:List<MapNeighborhood>,english:Boolean,onNeighborhood:(String)->Unit,onReport:(MapReport)->Unit,modifier:Modifier=Modifier) {
    var revision by remember(map) { mutableIntStateOf(0) }
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    DisposableEffect(map) {
        val move=MapLibreMap.OnCameraMoveListener { revision++ }
        val idle=MapLibreMap.OnCameraIdleListener { revision++ }
        map.addOnCameraMoveListener(move);map.addOnCameraIdleListener(idle)
        onDispose {map.removeOnCameraMoveListener(move);map.removeOnCameraIdleListener(idle)}
    }
    val density=LocalDensity.current.density
    val reportTargets=remember(map,reports,revision,viewport) {
        reports.mapNotNull {report->
            val p=map.projection.toScreenLocation(LatLng(report.point.latitude,report.point.longitude))
            if(p.x<0||p.y<0||p.x>viewport.width||p.y>viewport.height)null else report to p
        }
    }
    val cards=remember(map,neighborhoods,revision,viewport,density) {
        neighborhoods.mapNotNull { neighborhood -> neighborhood.point?.let { point ->
            val p=map.projection.toScreenLocation(LatLng(point.latitude,point.longitude))
            if(p.x+74*density<0||p.y<0||p.x-74*density>viewport.width||p.y-100*density>viewport.height)null else neighborhood to p
        } }
    }
    Box(modifier.onSizeChanged {viewport=it}) {
        // Native report artwork shares the people layer; preserve accessible touch targets.
        reportTargets.forEach {(report,point)->key(report.id) {
            Box(Modifier.offset {IntOffset((point.x-24*density).roundToInt(),(point.y-24*density).roundToInt())}
                .size(48.dp).semantics {contentDescription=if(english)"Incident report" else "通報"}
                .clickable(role=Role.Button) {onReport(report)})
        } }
        // Same 148 x 100 dp artwork and bottom anchor as the former native marker.
        // Only redraw artwork when counts change; camera movement only updates coordinates.
        cards.forEach {(neighborhood,point)->key(neighborhood.row.id) {
            val bitmap=remember(neighborhood.row,neighborhood.activeHigh,english,density) {
                neighborhoodCardBitmap(neighborhood,english,density).asImageBitmap()
            }
            Image(bitmap,if(english)neighborhood.row.nameEn else neighborhood.row.nameJa,
                Modifier.offset {IntOffset((point.x-74*density).roundToInt(),(point.y-100*density).roundToInt())}
                    .size(148.dp,100.dp).clickable(role=Role.Button) {onNeighborhood(neighborhood.row.id)})
        } }
    }
}

private fun neighborhoodCardBitmap(n:MapNeighborhood,english:Boolean,density:Float):Bitmap {
    val bitmap=Bitmap.createBitmap((148*density).roundToInt(),(100*density).roundToInt(),Bitmap.Config.ARGB_8888)
    val canvas=Canvas(bitmap);canvas.scale(density,density);val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    paint.color=Color.WHITE;canvas.drawRoundRect(1f,1f,147f,99f,10f,10f,paint)
    paint.color=0xFF183447.toInt();paint.textSize=12f;paint.typeface=Typeface.create("sans-serif",Typeface.BOLD)
    canvas.drawText(if(english)n.row.nameEn else n.row.nameJa,8f,21f,paint)
    paint.textSize=11f;paint.typeface=Typeface.create("sans-serif",Typeface.NORMAL)
    canvas.drawText("SOS ${n.row.sos} · ${if(english)"No response" else "応答なし"} ${n.row.unreported}",8f,44f,paint)
    canvas.drawText("${if(english)"Safe" else "無事"} ${n.row.safe} · ${if(english)"Rescued" else "救助済み"} ${n.row.rescued}",8f,65f,paint)
    canvas.drawText("${if(english)"High urgency" else "高緊急度"} ${n.activeHigh}",8f,86f,paint)
    return bitmap
}
