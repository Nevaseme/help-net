package io.github.nevaseme.plandmk1.ui

import android.app.ActivityManager
import android.content.Context
import android.graphics.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.nevaseme.plandmk1.MapPoint
import io.github.nevaseme.plandmk1.R
import io.github.nevaseme.plandmk1.maps.OfflineMapRepository
import io.github.nevaseme.plandmk1.buildings.BuildingMapModel
import io.github.nevaseme.plandmk1.buildings.BuildingRiskOverlay
import io.github.nevaseme.plandmk1.buildings.BuildingMapCoverage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.maplibre.android.annotations.IconFactory
import org.maplibre.android.annotations.Icon
import org.maplibre.android.annotations.Marker
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.style.expressions.Expression.*
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.PropertyFactory.*
import kotlin.math.*

class SituationCamera {
    internal var map: MapLibreMap? = null
    var cancelLocationCenter:()->Unit = {}
    fun zoom(delta: Double) { map?.let { it.animateCamera(CameraUpdateFactory.zoomTo((it.cameraPosition.zoom + delta).coerceIn(2.0, 20.0))) } }
    fun move(point: MapPoint, zoom: Double = 16.8) { map?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(point.latitude, point.longitude), zoom)) }
    fun showKyoto() {
        val bounds=LatLngBounds.Builder().include(LatLng(BuildingMapCoverage.NORTH,BuildingMapCoverage.EAST)).include(LatLng(BuildingMapCoverage.SOUTH,BuildingMapCoverage.WEST)).build()
        map?.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds,48))
    }
}
data class MapReport(val id:String,val point:MapPoint,val urgency:io.github.nevaseme.plandmk1.core.Urgency)

@Suppress("DEPRECATION")
@Composable
fun SituationMap(
    people: List<MapPerson>, homes: List<MapHome>, center: MapPoint, showSafe: Boolean,
    now: Long, preview: Boolean, camera: SituationCamera,
    onPerson: (MapPerson) -> Unit, onHome: (MapHome) -> Unit,
    onOverlapping: (List<MapPerson>) -> Unit, onError: () -> Unit,
    modifier: Modifier = Modifier,
    tsunamiResult:io.github.nevaseme.plandmk1.tsunami.TsunamiComputedResult? = null,
    assessmentIntensity: Double = BuildingMapModel.DEFAULT_INTENSITY,
    onOverview: (Boolean) -> Unit = {},
    onCoverage: (Boolean) -> Unit = {},
    onReportPoint: ((MapPoint) -> Unit)? = null,
    reports:List<MapReport> = emptyList(),onReport: (String)->Unit = {}, initialZoom:Double=16.8,
    neighborhoods:List<MapNeighborhood> = emptyList(),onNeighborhood:(String)->Unit={},
    currentLocation:io.github.nevaseme.plandmk1.location.PositionFix?=null,
) {
    val context = LocalContext.current
    val supports = remember { (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).deviceConfigurationInfo.reqGlEsVersion >= 0x30000 }
    if (!supports) {
        Box(modifier, contentAlignment = Alignment.Center) { Text("地図を表示できません。メニューの住民一覧を利用できます。", Modifier.padding(24.dp)) }
        return
    }
    val owner = LocalLifecycleOwner.current
    val mapVisible = LocalMapVisible.current
    val latestMapVisible by rememberUpdatedState(mapVisible)
    val view = remember { PlanMapView(context).also { it.tag="situation-map";it.onCreate(null); it.setMaximumFps(60) } }
    var controller by remember { mutableStateOf<MapLibreMap?>(null) }
    var ready by remember { mutableStateOf(false) }
    var zoom by rememberSaveable { mutableDoubleStateOf(initialZoom) }
    var latitude by rememberSaveable { mutableDoubleStateOf(center.latitude) }
    var longitude by rememberSaveable { mutableDoubleStateOf(center.longitude) }
    val latestPeople by rememberUpdatedState(people)
    val latestHomes by rememberUpdatedState(homes)
    val latestSelect by rememberUpdatedState(onPerson)
    val latestHome by rememberUpdatedState(onHome)
    val latestOverlap by rememberUpdatedState(onOverlapping)
    val latestError by rememberUpdatedState(onError)
    val latestSafe by rememberUpdatedState(showSafe)
    val latestNow by rememberUpdatedState(now)
    val latestIntensity by rememberUpdatedState(assessmentIntensity)
    val latestOverview by rememberUpdatedState(onOverview)
    val latestCoverage by rememberUpdatedState(onCoverage)
    val latestReportPoint by rememberUpdatedState(onReportPoint)
    val latestReport by rememberUpdatedState(onReport)
    val latestTsunami by rememberUpdatedState(tsunamiResult)
    val tsunamiEnglish by rememberUpdatedState(LocalUiLanguage.current=="en")
    val neighborhoodMarkers=remember {mutableMapOf<Long,String>()}
    val neighborhoodRendered=remember {mutableListOf<Marker>()}
    val latestNeighborhood by rememberUpdatedState(onNeighborhood)
    val neighborhoodEnglish=LocalUiLanguage.current=="en"
    val reportMarkers=remember {mutableMapOf<Long,String>()}
    val reportRendered=remember {mutableListOf<Marker>()}
    var riskModel by remember { mutableStateOf<BuildingMapModel?>(null) }
    val latestRiskModel by rememberUpdatedState(riskModel)
    val markers = remember { mutableMapOf<Long, MapCluster>() }
    val renderedMarkers = remember { mutableListOf<Marker>() }
    val iconCache = remember { mutableMapOf<String, Icon>() }

    LaunchedEffect(Unit) {
        if (!preview) riskModel = withContext(Dispatchers.IO) {
            context.assets.open("kyoto-building-assessment/model.json").bufferedReader().use { BuildingMapModel.parse(it.readText()) }
        }
    }
    LaunchedEffect(ready, riskModel, assessmentIntensity) {
        val model=riskModel ?: return@LaunchedEffect
        if(ready) controller?.style?.let {BuildingRiskOverlay.install(it,model,assessmentIntensity)}
    }

    LaunchedEffect(ready,tsunamiResult) {
        if(ready)controller?.style?.let {style ->
            io.github.nevaseme.plandmk1.tsunami.TsunamiOverlay.update(style,tsunamiResult)
        }
    }
    LaunchedEffect(ready,currentLocation){if(ready)controller?.style?.let {drawCurrentMapLocation(it,currentLocation)}}
    DisposableEffect(view, owner) {
        val observer = LifecycleEventObserver { _, event -> if (!view.isDestroyed) when (event) {
            Lifecycle.Event.ON_START -> if(latestMapVisible) view.onStart()
            Lifecycle.Event.ON_RESUME -> if(latestMapVisible) view.onResume()
            Lifecycle.Event.ON_PAUSE -> view.onPause()
            Lifecycle.Event.ON_STOP -> view.onStop()
            Lifecycle.Event.ON_DESTROY -> view.release()
            else -> Unit
        } }
        owner.lifecycle.addObserver(observer)
        view.addOnDidFailLoadingMapListener { latestError() }
        view.getMapAsync { map ->
            controller = map; camera.map = map
            map.addOnCameraMoveStartedListener {reason->if(reason==MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE)camera.cancelLocationCenter()}
            map.cameraPosition = CameraPosition.Builder().target(LatLng(latitude, longitude)).zoom(zoom).build()
            map.uiSettings.apply { isRotateGesturesEnabled = false; isTiltGesturesEnabled = false; isCompassEnabled = false }
            // Tile caching is enabled by default. The redundant renderer-backed
            // setter stalled this UI callback during rapid map recreation.
            // A coarser Kyoto tile contains hundreds of thousands of footprints.
            // Loading it alongside the visible tiles doubles work during a jump.
            map.setPrefetchZoomDelta(0)
            map.addOnCameraIdleListener {
                zoom = map.cameraPosition.zoom
                latestOverview(zoom < 14)
                val bounds = map.projection.visibleRegion.latLngBounds
                latestCoverage(BuildingMapCoverage.intersects(bounds.latitudeSouth,bounds.longitudeWest,bounds.latitudeNorth,bounds.longitudeEast))
                map.cameraPosition.target?.let { latitude = it.latitude; longitude = it.longitude }
            }
            map.setOnMarkerClickListener { marker ->
                // Location-picking mode takes precedence over opening any existing marker.
                latestReportPoint?.let { pick ->
                    pick(MapPoint(marker.position.latitude, marker.position.longitude))
                    return@setOnMarkerClickListener true
                }
                neighborhoodMarkers[marker.id]?.let {latestNeighborhood(it);return@setOnMarkerClickListener true}
                reportMarkers[marker.id]?.let {latestReport(it);return@setOnMarkerClickListener true}
                markers[marker.id]?.let { cluster ->
                    if (cluster.people.size == 1) {
                        val person = latestPeople.find { it.id == cluster.people.single().id } ?: cluster.people.single()
                        val samePoint = latestPeople.filter { it.point == person.point && it.hasPosition(latestNow) && (latestSafe || it.state !in listOf(MapSafety.SAFE, MapSafety.RESCUED)) }
                        if (samePoint.size > 1) latestOverlap(samePoint) else latestSelect(person)
                    }
                    else if (cluster.people.map { it.point }.distinct().size == 1) latestOverlap(cluster.people)
                    else {
                        val bounds = LatLngBounds.Builder().includes(cluster.people.map { LatLng(it.point!!.latitude, it.point.longitude) }).build()
                        val density = context.resources.displayMetrics.density
                        val fitted = map.getCameraForLatLngBounds(bounds, intArrayOf((52 * density).toInt(), (72 * density).toInt(), (52 * density).toInt(), (88 * density).toInt()))
                        if (fitted != null) {
                            val maximum = minOf(16.6, map.cameraPosition.zoom + 1.8)
                            map.animateCamera(CameraUpdateFactory.newCameraPosition(CameraPosition.Builder(fitted).zoom(minOf(fitted.zoom, maximum)).build()))
                        }
                    }
                }
                true
            }
            map.addOnMapClickListener { point ->
                latestReportPoint?.let {it(MapPoint(point.latitude,point.longitude));return@addOnMapClickListener true}
                if(latestTsunami!=null) {
                    val cell=map.queryRenderedFeatures(map.projection.toScreenLocation(point),"tsunami-depth-fill").firstOrNull()
                    if(cell!=null) {
                        android.widget.Toast.makeText(context,tsunamiText("depth",tsunamiEnglish)+" ${String.format(java.util.Locale.US,"%.2f",cell.getNumberProperty("max_depth_m").toDouble())}m",android.widget.Toast.LENGTH_SHORT).show()
                        return@addOnMapClickListener true
                    }
                }
                val model=latestRiskModel
                val wide=map.cameraPosition.zoom<14
                val risk=if(model!=null && !wide) map.queryRenderedFeatures(map.projection.toScreenLocation(point),BuildingRiskOverlay.BUILDINGS,BuildingRiskOverlay.OVERVIEW,"toranomon-risk-buildings","toranomon-risk-overview").firstOrNull() else null
                if(model!=null && wide) {
                    camera.move(MapPoint(point.latitude,point.longitude),16.8)
                    true
                } else if(risk!=null && model!=null) {
                    fun number(key:String)=risk.getNumberProperty(key).toDouble()
                    val rate=model.percent(risk.getStringProperty("p"),latestIntensity)
                    val strength=rate?.let {if(it<10)0 else if(it<40)1 else 2}
                    val structure=when(risk.getStringProperty("structure")) {"601"->"木造・土蔵造";"602"->"SRC造";"603"->"RC造";"604"->"鉄骨造";"605"->"軽量鉄骨造";"606"->"組積造";"610"->"非木造";else->"不明"}
                    val assessed=MapHome(risk.getStringProperty("bid"),"PLATEAU建物",MapPoint(point.latitude,point.longitude),
                        built=number("year").toInt().takeIf {it>0},material=structure,
                        floors=number("floors").toInt().takeIf {it>0},rating=strength,
                        fullDamagePercent=rate,assessmentIntensity=latestIntensity,
                        assessmentAssumed=risk.getStringProperty("s")=="assumed")
                    val rings=when(val g=risk.geometry()) {
                        is org.maplibre.geojson.Polygon -> listOf(g.coordinates())
                        is org.maplibre.geojson.MultiPolygon -> g.coordinates()
                        else -> emptyList()
                    }.map { polygon -> polygon.map { ring -> ring.map { MapPoint(it.latitude(),it.longitude()) } } }
                    val registered=latestHomes.firstOrNull { h -> rings.any {insideFootprint(h.point,it)} }
                    latestHome(if(registered==null)assessed else assessed.copy(id=registered.id,address=registered.address,point=registered.point))
                    true
                } else {
                val features = map.queryRenderedFeatures(map.projection.toScreenLocation(point), "building")
                if (features.isEmpty()) false else {
                    val polygons = features.flatMap { feature -> when (val geometry = feature.geometry()) {
                        is org.maplibre.geojson.Polygon -> listOf(geometry.coordinates())
                        is org.maplibre.geojson.MultiPolygon -> geometry.coordinates()
                        else -> emptyList()
                    } }.map { rings -> rings.map { ring -> ring.map { MapPoint(it.latitude(), it.longitude()) } } }
                    val registered = latestHomes.firstOrNull { home -> polygons.any { insideFootprint(home.point, it) } }
                    if (registered != null) latestHome(registered)
                    else latestHome(MapHome("selected", "選択した建物", MapPoint(point.latitude, point.longitude)))
                    true
                }
                }
            }
            map.setStyle(OfflineMapRepository.STYLE) { style ->
                if (preview) {
                    val buildings = style.getLayer("building") as? FillLayer
                    // Preview classes only. Real strength assessments will supply their own attributes.
                    val rating = raw("""["%",["floor",["/",["to-number",["coalesce",["id"],30],30],10]],4]""")
                    val color = match(rating, literal("#D4D7DC"),
                        stop(0, "#FBE8C5"), stop(1, "#F2AE5D"), stop(2, "#C5672E"))
                    val layer = FillLayer("design-ratings", buildings?.sourceId ?: "openmaptiles")
                        .withSourceLayer(buildings?.sourceLayer ?: "building")
                        .withProperties(fillColor(color), fillOpacity(.58f), fillOutlineColor("#B5A696"))
                    layer.minZoom = buildings?.minZoom ?: 12f
                    buildings?.filter?.let { layer.setFilter(it) }
                    val before = style.layers.firstOrNull { it is org.maplibre.android.style.layers.SymbolLayer }?.id
                    if (before != null) style.addLayerBelow(layer, before) else style.addLayer(layer)
                    val hatch = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
                    for (y in 0..7) for (x in 0..7) hatch.setPixel(x, y, if ((x + y) % 8 < 2) 0xFF949AA4.toInt() else 0xFFDADDE1.toInt())
                    style.addImage("design-hatch", hatch)
                    val unknown = FillLayer("design-rating-unknown", buildings?.sourceId ?: "openmaptiles").withSourceLayer(buildings?.sourceLayer ?: "building").withProperties(fillPattern("design-hatch"), fillOpacity(.6f)).withFilter(eq(rating, literal(3)))
                    unknown.minZoom = layer.minZoom
                    buildings?.filter?.let { unknown.setFilter(all(it, eq(rating, literal(3)))) }
                    if (before != null) style.addLayerBelow(unknown, before) else style.addLayer(unknown)

                }
                ready = true
            }
        }
        onDispose {
            owner.lifecycle.removeObserver(observer)
            camera.map = null; controller = null
            view.release()
            renderedMarkers.clear(); markers.clear(); iconCache.clear();reportMarkers.clear();reportRendered.clear()
        }
    }
    SideEffect {
        if (mapVisible && owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            view.visibility = android.view.View.VISIBLE
            view.onStart()
            if(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) view.onResume()
        } else {
            view.onPause()
            view.onStop()
            view.visibility = android.view.View.INVISIBLE
        }
    }
    AndroidView(factory = { view }, modifier = modifier)
    LaunchedEffect(ready,reports) {
        val map=controller?:return@LaunchedEffect
        if(!ready)return@LaunchedEffect
        reportRendered.forEach {map.removeMarker(it)};reportRendered.clear();reportMarkers.clear()
        reports.forEach {report->
            val size=(42*context.resources.displayMetrics.density).roundToInt()
            val bitmap=Bitmap.createBitmap(size,size,Bitmap.Config.ARGB_8888)
            ContextCompat.getDrawable(context,reportIcon(report.urgency))?.let {it.setBounds(0,0,size,size);it.draw(Canvas(bitmap))}
            val icon=IconFactory.getInstance(context).fromBitmap(bitmap)
            val marker=map.addMarker(MarkerOptions().position(LatLng(report.point.latitude,report.point.longitude)).icon(icon))
            reportRendered.add(marker);reportMarkers[marker.id]=report.id
        }
    }
    LaunchedEffect(ready,neighborhoods,neighborhoodEnglish) {
        val map=controller?:return@LaunchedEffect
        if(!ready)return@LaunchedEffect
        neighborhoodRendered.forEach {map.removeMarker(it)};neighborhoodRendered.clear();neighborhoodMarkers.clear()
        neighborhoods.forEach {n->n.point?.let {point->
            val d=context.resources.displayMetrics.density
            val bitmap=Bitmap.createBitmap((148*d).roundToInt(),(100*d).roundToInt(),Bitmap.Config.ARGB_8888)
            val canvas=Canvas(bitmap);canvas.scale(d,d);val paint=Paint(Paint.ANTI_ALIAS_FLAG)
            paint.color=Color.WHITE;canvas.drawRoundRect(1f,1f,147f,99f,10f,10f,paint)
            paint.color=0xFF183447.toInt();paint.textSize=12f;paint.typeface=Typeface.create("sans-serif",Typeface.BOLD)
            canvas.drawText(if(neighborhoodEnglish)n.row.nameEn else n.row.nameJa,8f,21f,paint)
            paint.textSize=11f;paint.typeface=Typeface.create("sans-serif",Typeface.NORMAL)
            canvas.drawText("SOS ${n.row.sos} · ${if(neighborhoodEnglish)"No response" else "応答なし"} ${n.row.unreported}",8f,44f,paint)
            canvas.drawText("${if(neighborhoodEnglish)"Safe" else "無事"} ${n.row.safe} · ${if(neighborhoodEnglish)"Rescued" else "救助済み"} ${n.row.rescued}",8f,65f,paint)
            canvas.drawText("${if(neighborhoodEnglish)"High urgency" else "高緊急度"} ${n.activeHigh}",8f,86f,paint)
            val marker=map.addMarker(MarkerOptions().position(LatLng(point.latitude,point.longitude)).icon(IconFactory.getInstance(context).fromBitmap(bitmap)))
            neighborhoodRendered.add(marker);neighborhoodMarkers[marker.id]=n.row.id
        }}
    }
    LaunchedEffect(ready, people, showSafe, zoom, now) {
        val map = controller ?: return@LaunchedEffect
        if (!ready) return@LaunchedEffect
        val clusters = mapClusters(people, zoom, showSafe, now)
        val previous = renderedMarkers.toList()
        val reuse = reuseClusterMarkers(previous.map { markers.getValue(it.id) }, clusters)
        val next = mutableListOf<Marker>()
        val usedIcons = mutableSetOf<String>()
        clusters.forEachIndexed { index, cluster ->
            val bubble = zoom < INDIVIDUAL_ZOOM
            val key = if (bubble) cluster.counts.toString() else personMarkerKey(cluster.people.single(), now)
            usedIcons += key
            val icon = iconCache.getOrPut(key) { IconFactory.getInstance(context).fromBitmap(mapMarkerBitmap(context, cluster, bubble, now)) }
            val p = cluster.point
            val position = LatLng(p.latitude, p.longitude)
            val label = if (bubble) cluster.counts.joinToString("、") { "${it.first.label}${it.second}人" } else "${cluster.people.single().publicName}、${cluster.people.single().state.label}"
            val marker = reuse[index]?.let(previous::get)?.apply {
                if (this.icon != icon) this.icon = icon
                if (this.position != position) this.position = position
                if (title != label) title = label
            } ?: map.addMarker(MarkerOptions().position(position).title(label).icon(icon))
            next += marker
            markers[marker.id] = cluster
        }
        val retained = next.map { it.id }.toSet()
        previous.filter { it.id !in retained }.forEach { map.removeMarker(it); markers.remove(it.id) }
        renderedMarkers.clear(); renderedMarkers.addAll(next)
        if (iconCache.size > 128) iconCache.keys.retainAll(usedIcons)
    }
}

private fun mapMarkerBitmap(context: Context, cluster: MapCluster, bubble: Boolean, now: Long): Bitmap {
    val d = context.resources.displayMetrics.density
    val width = if (bubble) CLUSTER_WIDTH else 60
    val age = cluster.people.singleOrNull()?.let { markerAgeLabel(it, now) }.orEmpty()
    val height = if (bubble) clusterHeight(cluster) else if (age.isNotEmpty()) 80 else 62
    val bitmap = Bitmap.createBitmap((width * d).roundToInt(), (height * d).roundToInt(), Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap); canvas.scale(d, d)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    if (bubble) {
        paint.color = Color.WHITE; canvas.drawRoundRect(1f, 1f, 81f, height - 1f, 17f, 17f, paint)
        cluster.counts.forEachIndexed { i, (state, count) ->
            drawState(canvas, paint, state, 21f, 22f + i * 30, 13f)
            paint.color = 0xFF142B42.toInt(); paint.textSize = 17f; paint.textAlign = Paint.Align.LEFT; paint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
            canvas.drawText("${count}人", 40f, 28f + i * 30, paint)
        }
    } else {
        val p = cluster.people.single()
        drawState(canvas, paint, p.state, 30f, 35f, 21f)
        fun sensor(resource: Int, left: Int) {
            paint.color = Color.WHITE; canvas.drawCircle(left + 11f, 13f, 13f, paint)
            ContextCompat.getDrawable(context, resource)?.let { drawable -> drawable.setBounds(left, 2, left + 22, 24); drawable.draw(canvas) }
        }
        if (p.impactAt != null) sensor(R.drawable.impact, 36)
        if (p.enclosedAt != null) sensor(R.drawable.enclosed, 2)
        if (age.isNotEmpty()) {
            paint.color = Color.WHITE; canvas.drawRoundRect(8f, 61f, 52f, 79f, 5f, 5f, paint)
            paint.color = 0xFF697586.toInt(); paint.textSize = 10f; paint.textAlign = Paint.Align.CENTER
            canvas.drawText(age, 30f, 74f, paint)
        }
    }
    return bitmap
}

internal fun drawState(canvas: Canvas, paint: Paint, state: MapSafety, x: Float, y: Float, radius: Float) {
    paint.style = Paint.Style.FILL; paint.color = Color.WHITE; canvas.drawCircle(x, y, radius + 3, paint)
    paint.color = state.argb; canvas.drawCircle(x, y, radius, paint)
    if (state == MapSafety.UNANSWERED) { paint.color = Color.WHITE; canvas.drawCircle(x, y, radius - 2, paint); paint.color = state.argb }
    else paint.color = Color.WHITE
    paint.textAlign = Paint.Align.CENTER; paint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
    when (state) {
        MapSafety.SOS -> { paint.textSize = radius * .76f; canvas.drawText("SOS", x, y + radius * .27f, paint) }
        MapSafety.UNANSWERED -> { paint.textSize = radius * 1.48f; canvas.drawText("?", x, y + radius * .52f, paint) }
        MapSafety.SAFE -> {
            paint.style = Paint.Style.STROKE; paint.strokeWidth = radius * .22f; paint.strokeCap = Paint.Cap.ROUND; paint.strokeJoin = Paint.Join.ROUND
            canvas.drawPath(Path().apply { moveTo(x - radius * .52f, y); lineTo(x - radius * .1f, y + radius * .38f); lineTo(x + radius * .56f, y - radius * .4f) }, paint)
            paint.style = Paint.Style.FILL
        }
        MapSafety.RESCUED -> {
            // Match the adopted demo's user-check silhouette, distinct from the safe checkmark.
            canvas.drawCircle(x - radius * .18f, y - radius * .35f, radius * .29f, paint)
            canvas.drawPath(Path().apply {
                moveTo(x - radius * .73f, y + radius * .48f)
                cubicTo(x - radius * .65f, y - radius * .17f, x + radius * .24f, y - radius * .17f, x + radius * .3f, y + radius * .48f)
                close()
            }, paint)
            // The background cutout separates the check from the person's shoulder.
            paint.style = Paint.Style.STROKE; paint.strokeCap = Paint.Cap.ROUND; paint.strokeJoin = Paint.Join.ROUND
            val check = Path().apply {
                moveTo(x + radius * .13f, y + radius * .17f)
                lineTo(x + radius * .36f, y + radius * .39f)
                lineTo(x + radius * .77f, y - radius * .07f)
            }
            paint.strokeWidth = radius * .37f; paint.color = state.argb; canvas.drawPath(check, paint)
            paint.strokeWidth = radius * .18f; paint.color = Color.WHITE; canvas.drawPath(check, paint)
            paint.style = Paint.Style.FILL
        }
    }
}
