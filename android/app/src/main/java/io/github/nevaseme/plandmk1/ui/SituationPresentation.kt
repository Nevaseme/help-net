package io.github.nevaseme.plandmk1.ui

import io.github.nevaseme.plandmk1.MapPoint
import io.github.nevaseme.plandmk1.core.*
import kotlin.math.*
import kotlinx.serialization.json.*
import io.github.nevaseme.plandmk1.buildings.AssessedBuilding

const val DAY = 86_400_000L
const val INDIVIDUAL_ZOOM = 14.0
const val CLUSTER_WIDTH = 82
fun clusterHeight(cluster: MapCluster) = cluster.counts.size * 30 + 14

enum class MapSafety(val label: String, val argb: Int) {
    SOS("SOS", 0xFFE01F2C.toInt()), UNANSWERED("応答なし", 0xFFEE9C08.toInt()),
    SAFE("無事", 0xFF00836C.toInt()), RESCUED("救助済み", 0xFF00836C.toInt())
}

data class MapHome(
    val id: String, val address: String, val point: MapPoint,
    val built: Int? = null, val material: String = "不明", val floors: Int? = null,
    val rating: Int? = null,
    val fullDamagePercent: Double? = null, val assessmentIntensity: Double? = null,
    val assessmentAssumed: Boolean = false,
    val attributesUnavailable:Boolean=false,
    val plateauId: String? = null,
)

fun MapHome.withBuildingAttributes(building:AssessedBuilding):MapHome {
    fun positive(key:String)=building.attributes[key]?.jsonPrimitive?.intOrNull?.takeIf {it>0}
    val material=when(building.attributes["structureCode"]?.jsonPrimitive?.contentOrNull){
        "601"->"木造・土蔵造";"602"->"SRC造";"603"->"RC造";"604"->"鉄骨造";"605"->"軽量鉄骨造";"606"->"組積造";"610"->"非木造";else->"不明"
    }
    return copy(built=positive("year"),floors=positive("floors"),material=material)
}

data class MapPerson(
    val id: String, val name: String, val state: MapSafety,
    val point: MapPoint? = null, val acquiredAt: Long? = null, val accuracy: Int? = null,
    val home: MapHome? = null, val bedroom: String = "未登録", val memo: String = "",
    val impactAt: Long? = null, val enclosedAt: Long? = null,
    val identityVisible: Boolean = true, val registeredPlaceVisible: Boolean = true, val demo:Boolean=false,val fictional:Boolean=false,
    val rescuedAt: Long? = null,
) {
    val publicName get() = when (state) {
        MapSafety.SAFE -> "無事の人"
        MapSafety.RESCUED -> "救助済みの人"
        else -> if(!identityVisible) "応答なし" else name.ifBlank { "登録住民" }
    }
    fun hasPosition(@Suppress("UNUSED_PARAMETER") now: Long) = point != null && acquiredAt != null
}

/** Display projection only; the stored profile and incident's earliest formation time are retained. */
fun projectPersonPrivacy(person:MapPerson,incidentStartedAt:Long?,now:Long):MapPerson {
    if(person.state in setOf(MapSafety.SAFE,MapSafety.RESCUED))return person.copy(name="",identityVisible=false,registeredPlaceVisible=false,home=null,bedroom="",memo="",impactAt=null,enclosedAt=null)
    val anonymous=person.state==MapSafety.UNANSWERED && (incidentStartedAt==null || now-incidentStartedAt<180_000)
    val homeVisible=when(person.state) {
        MapSafety.UNANSWERED -> !anonymous
        MapSafety.SOS,MapSafety.SAFE,MapSafety.RESCUED -> !person.hasPosition(now)
    }
    val observations=person.state==MapSafety.SOS||person.state==MapSafety.UNANSWERED
    return person.copy(name=if(anonymous)""else person.name,identityVisible=!anonymous,registeredPlaceVisible=homeVisible,
        home=person.home.takeIf {homeVisible},bedroom=if(homeVisible)person.bedroom else "",memo=if(anonymous)""else person.memo,
        impactAt=person.impactAt.takeIf {observations},enclosedAt=person.enclosedAt.takeIf {observations})
}

/** Aggregate values only: never retain resident IDs or private home links in map totals. */
data class BuildingResidentCounts(val total:Int=0,val safe:Int=0,val unanswered:Int=0,val sos:Int=0,val rescued:Int=0)
fun buildingResidentCounts(people:List<Resident>,places:List<PlaceEvent>,care:List<CareEvent>,roster:MemberRoster?=null):Map<String,BuildingResidentCounts> {
    val buildings=placeBuildings(places).associateBy {it.buildingId}
    return people.flatMap {person->
        val building=validHome(places,person.originId)?.buildingId?.let(buildings::get)?:return@flatMap emptyList()
        listOfNotNull(building.buildingId,building.locationReference?.plateauId).distinct().map {it to residentMapState(person,care,roster)}
    }.groupBy({it.first},{it.second}).mapValues {(_,states)->BuildingResidentCounts(states.size,states.count {it==MapSafety.SAFE},states.count {it==MapSafety.UNANSWERED},states.count {it==MapSafety.SOS},states.count {it==MapSafety.RESCUED})}
}
fun disclosedBuildingResidents(home:MapHome,people:List<MapPerson>)=people.filter {p->
    p.identityVisible&&p.registeredPlaceVisible&&p.home?.let {it.id==home.id||it.plateauId==home.id||(home.plateauId!=null&&it.plateauId==home.plateauId)}==true
}
fun buildingResidentDestination(person:MapPerson,now:Long):MapPoint? = person.point.takeIf {person.identityVisible&&person.hasPosition(now)}

/** Re-check live disclosure when an already-open building sheet outlives a safety update. */
fun visibleHomeDetail(homeId:String?,personId:String?,people:List<MapPerson>,homes:List<MapHome>,extraHome:MapHome?):MapHome? =
    if(personId!=null)people.find {it.id==personId&&it.registeredPlaceVisible}?.home?.takeIf {it.id==homeId}
    else homes.find {it.id==homeId}?:extraHome?.takeIf {it.id==homeId}

fun situationList(people: List<MapPerson>, state: MapSafety?, unknownOnly: Boolean, areaIds: Set<String>?, now: Long) =
    people.filter { (state == null || it.state == state) && (!unknownOnly || !it.hasPosition(now)) &&
        (areaIds == null || it.id in areaIds) }

enum class MainDestination { HOME, ANSWER, MAP }
fun mainDestination(active: Boolean, answered: Boolean) = when {
    !active -> MainDestination.HOME
    !answered -> MainDestination.ANSWER
    else -> MainDestination.MAP
}

data class MapCluster(val people: List<MapPerson>) {
    val point = MapPoint(people.map { it.point!!.latitude }.average(), people.map { it.point!!.longitude }.average())
    val counts = MapSafety.entries.mapNotNull { state -> people.count { it.state == state }.takeIf { it > 0 }?.let { state to it } }
}

fun markerAgeLabel(person: MapPerson, now: Long): String {
    if(person.demo)return ""
    val minutes = person.acquiredAt?.let { ((now - it) / 60_000).coerceAtLeast(0) } ?: return ""
    return when { minutes <= 5 -> ""; minutes < 60 -> "${minutes}分前"; else -> "${minutes / 60}時間前" }
}

fun personMarkerKey(person: MapPerson, now: Long) =
    "${person.state}:${person.impactAt != null}:${person.enclosedAt != null}:${markerAgeLabel(person, now)}"

/** Keep exact groups first, then reuse a surviving member's marker when groups merge or split. */
fun reuseClusterMarkers(previous: List<MapCluster>, next: List<MapCluster>): List<Int?> {
    val oldMembers = previous.map { it.people.map(MapPerson::id).toSet() }
    val newMembers = next.map { it.people.map(MapPerson::id).toSet() }
    val available = previous.indices.toMutableSet()
    val result = MutableList<Int?>(next.size) { null }
    newMembers.forEachIndexed { i, members ->
        available.firstOrNull { oldMembers[it] == members }?.let { result[i] = it; available.remove(it) }
    }
    newMembers.forEachIndexed { i, members ->
        if (result[i] == null) {
            available.maxByOrNull { oldMembers[it].intersect(members).size }
                ?.takeIf { oldMembers[it].any(members::contains) }
                ?.let { result[i] = it; available.remove(it) }
        }
    }
    return result
}

fun insideFootprint(point: MapPoint, rings: List<List<MapPoint>>): Boolean {
    fun inside(ring: List<MapPoint>): Boolean {
        if (ring.size < 3) return false
        var contained = false
        var previous = ring.last()
        for (current in ring) {
            if ((current.latitude > point.latitude) != (previous.latitude > point.latitude) &&
                point.longitude < (previous.longitude - current.longitude) * (point.latitude - current.latitude) / (previous.latitude - current.latitude) + current.longitude) contained = !contained
            previous = current
        }
        return contained
    }
    return rings.firstOrNull()?.let(::inside) == true && rings.drop(1).none(::inside)
}

/** Distances are evaluated in projected map pixels at the current zoom. */
fun mapClusters(people: List<MapPerson>, zoom: Double, showSafe: Boolean, now: Long): List<MapCluster> {
    val visible = people.filter { it.hasPosition(now) && (showSafe || it.state !in listOf(MapSafety.SAFE, MapSafety.RESCUED)) }
    if (zoom >= INDIVIDUAL_ZOOM) return visible.map { MapCluster(listOf(it)) }
    val scale = 512 * 2.0.pow(zoom)
    fun projected(point: MapPoint): Pair<Double, Double> {
        val latitude = point.latitude.coerceIn(-85.0, 85.0) * PI / 180
        return (point.longitude + 180) / 360 * scale to (1 - ln(tan(latitude) + 1 / cos(latitude)) / PI) / 2 * scale
    }
    val xy = visible.map { projected(it.point!!) }
    val roots = IntArray(visible.size) { it }
    fun root(i: Int): Int { var r = i; while (roots[r] != r) r = roots[r]; return r }
    for (i in visible.indices) for (j in 0 until i) {
        if (hypot(xy[i].first - xy[j].first, xy[i].second - xy[j].second) < 70) roots[root(i)] = root(j)
    }
    val clusters = visible.indices.groupBy { root(it) }.values.map { MapCluster(it.map(visible::get)) }.toMutableList()
    // Merging changes both the anchor and number of rows, so check again until the bubbles fit.
    while (true) {
        var merged = false
        outer@ for (i in clusters.indices) for (j in 0 until i) {
            val a = projected(clusters[i].point)
            val b = projected(clusters[j].point)
            if (abs(a.first - b.first) < CLUSTER_WIDTH + 8 &&
                abs(a.second - b.second) < maxOf(clusterHeight(clusters[i]), clusterHeight(clusters[j])) + 8) {
                clusters[j] = MapCluster(clusters[j].people + clusters[i].people)
                clusters.removeAt(i)
                merged = true
                break@outer
            }
        }
        if (!merged) return clusters
    }
}

fun residentMapState(person: Resident, care: List<CareEvent>,roster:MemberRoster?=null): MapSafety {
    val current = currentCareEvents(care.filter { it.subjectId == person.originId },roster)
    val rescued = current.any { it.action == CareAction.RESCUED && it.createdAt >= person.updatedAt }
    return when {
        rescued -> MapSafety.RESCUED
        person.safety == Safety.SOS -> MapSafety.SOS
        person.safety == Safety.SAFE -> MapSafety.SAFE
        else -> MapSafety.UNANSWERED
    }
}

fun publicRecordAuthor(authorId: String, people: List<Resident>, care: List<CareEvent>,roster:MemberRoster?=null): String =
    people.find { it.originId == authorId }?.let { person ->
        when (residentMapState(person, care,roster)) {
            MapSafety.SOS, MapSafety.UNANSWERED -> person.displayName()
            MapSafety.SAFE, MapSafety.RESCUED -> "メンバー"
        }
    } ?: "メンバー"

// Format only recognized floor numbers; room and floor descriptions remain user text.
fun residenceFloorLabel(floor: String, language: String = "ja"): String {
    val value = java.text.Normalizer.normalize(floor.trim(), java.text.Normalizer.Form.NFKC)
    val number = Regex("^(-?\\d+)(?:階)?$").matchEntire(value)?.groupValues?.get(1)?.toLongOrNull()
        ?: Regex("^(?:B|地下)(\\d+)(?:階)?$", RegexOption.IGNORE_CASE).matchEntire(value)?.groupValues?.get(1)?.toLongOrNull()?.let { -it }
        ?: return floor.trim()
    if (number !in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) return floor.trim()
    return if (language == "en") "Floor $number" else if (number < 0) "地下${-number}階" else "${number}階"
}

// The old schema stores home coordinates, not measured phone positions.
fun residentMapPerson(person: Resident, places: List<PlaceEvent>, care: List<CareEvent>, position: PhonePosition? = null, memo: String = "", language: String = "ja",roster:MemberRoster?=null): MapPerson {
    val building = validHome(places, person.originId)?.buildingId?.let { id -> placeBuildings(places).find { it.buildingId == id } }
    val home = building?.let { MapHome(it.buildingId!!, it.displayLabel(), MapPoint(it.latitude!!, it.longitude!!),plateauId=it.locationReference?.plateauId) }
        ?: MapHome("home:${person.originId}", "登録住居", MapPoint(person.latitude, person.longitude))
    val measured = position?.takeIf { it.groupId == person.groupId && it.originId == person.originId }
    return MapPerson(person.originId, person.displayName(), residentMapState(person, care,roster),
        point = measured?.let { MapPoint(it.latitude, it.longitude) }, acquiredAt = measured?.acquiredAt,
        accuracy = measured?.accuracyMeters?.let { ceil(it).toInt() }, memo = memo, home = home,
        rescuedAt = currentCareEvents(care.filter {it.subjectId==person.originId},roster).filter {it.action==CareAction.RESCUED&&it.createdAt>=person.updatedAt}.maxOfOrNull {it.createdAt},
        bedroom = listOf(residenceFloorLabel(person.floor, language), person.room).filter { it.isNotBlank() }.joinToString("・").ifBlank { if (language == "en") "Not registered" else "未登録" })
}
