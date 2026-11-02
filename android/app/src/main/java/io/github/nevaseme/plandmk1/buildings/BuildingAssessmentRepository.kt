package io.github.nevaseme.plandmk1.buildings

import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.GZIPInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.round

@Serializable
data class BuildingGeometry(val type: String, val coordinates: List<List<List<List<Double>>>>)

@Serializable
data class AssessedBuilding(
    val id: String,
    val attributes: JsonObject,
    val raw: JsonObject = JsonObject(emptyMap()),
    val bbox: List<Double>? = null,
    val geometry: BuildingGeometry? = null,
    val geometrySource: String? = null,
    val profile: String?,
    val status: String,
    val reasons: List<String>,
)

data class BuildingDamageRates(val full: Double, val halfOnly: Double, val fullHalf: Double)
data class BuildingAssessment(
    val building: AssessedBuilding,
    val instrumentalIntensity: Double,
    val rates: BuildingDamageRates?,
    val modelId: String,
    val limitations: List<String>,
) {
    // Keep building.status/reasons: a high-rise assumed zero is not a measured result.
    val riskLevel: BuildingRiskLevel
        get() = BuildingRiskLevel.fromFullDamageRate(rates?.full)
}

/** Read-only public reference data. Never writes or joins residents' records by inferred IDs.
 * All file work runs on IO; at most two meshes are cached. No network is used.
 */
class BuildingAssessmentRepository(private val openAsset: (String) -> InputStream) {
    private val json=Json { ignoreUnknownKeys=true }
    private val lock=Mutex()
    private var index: JsonObject?=null
    private var model: JsonObject?=null
    private val cache=linkedMapOf<String,List<AssessedBuilding>>()
    private val prefix="kyoto-building-assessment/"

    private fun read(name: String)=openAsset(prefix+name).use { it.readBytes() }
    private fun verified(bytes: ByteArray, expected: String): ByteArray {
        val actual=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(""){"%02x".format(it)}
        require(actual==expected) { "Building assessment package checksum mismatch" }
        return bytes
    }
    private fun indexLocked(): JsonObject = index ?: json.parseToJsonElement(read("index.json").decodeToString()).jsonObject.also {
        require(it.getValue("schemaVersion").jsonPrimitive.int==1) { "Unsupported building dataset schema" }
        index=it
    }
    private fun modelLocked(): JsonObject = model ?: run {
        val idx=indexLocked()
        json.parseToJsonElement(verified(read("model.json"),idx.getValue("modelSha256").jsonPrimitive.content).decodeToString()).jsonObject.also {
            require(it.getValue("modelId")==idx.getValue("modelId"))
            model=it
        }
    }
    private fun chunkLocked(mesh: String): List<AssessedBuilding> {
        cache.remove(mesh)?.let { cache[mesh]=it; return it }
        val item=indexLocked().getValue("chunks").jsonArray.map { it.jsonObject }
            .firstOrNull { it.getValue("mesh").jsonPrimitive.content==mesh } ?: return emptyList()
        val bytes=verified(read(item.getValue("file").jsonPrimitive.content),item.getValue("sha256").jsonPrimitive.content)
        val text=GZIPInputStream(bytes.inputStream()).bufferedReader().use { it.readText() }
        val chunk=json.parseToJsonElement(text).jsonObject
        require(chunk.getValue("mesh").jsonPrimitive.content==mesh)
        val rows=json.decodeFromJsonElement<List<AssessedBuilding>>(chunk.getValue("buildings"))
        cache[mesh]=rows
        if(cache.size>2) cache.remove(cache.keys.first())
        return rows
    }

    suspend fun metadata(): JsonObject = withContext(Dispatchers.IO) { lock.withLock { indexLocked() } }
    suspend fun mesh(code: String): List<AssessedBuilding> = withContext(Dispatchers.IO) { lock.withLock { chunkLocked(code) } }

    /** Returns every containing footprint (including overlapping models); no nearest-building guess.
     * Empty means no matching published footprint, not zero damage. Unknown rates remain null.
     */
    suspend fun atPoint(latitude: Double, longitude: Double, instrumentalIntensity: Double): List<BuildingAssessment> = withContext(Dispatchers.IO) {
        require(latitude.isFinite() && latitude in -90.0..90.0 && longitude.isFinite() && longitude in -180.0..180.0)
        require(instrumentalIntensity.isFinite() && instrumentalIntensity in 5.0..7.0) { "計測震度は5.0〜7.0の範囲で指定してください" }
        lock.withLock {
            val idx=indexLocked()
            val mdl=modelLocked()
            val limitations=mdl.getValue("limitations").jsonArray.map { it.jsonPrimitive.content }
            val curves=mdl.getValue("curves").jsonObject
            val candidates=idx.getValue("chunks").jsonArray.map { it.jsonObject }.filter {
                val b=it["bbox"]
                b!=null && b!=JsonNull && contains(b.jsonArray.map { v->v.jsonPrimitive.double },longitude,latitude)
            }
            candidates.flatMap { item ->
                chunkLocked(item.getValue("mesh").jsonPrimitive.content).filter { building ->
                    building.bbox?.let { contains(it,longitude,latitude) }==true &&
                        building.geometry?.coordinates?.any { polygon ->
                            polygon.isNotEmpty() && inRing(polygon.first(),longitude,latitude) &&
                                polygon.drop(1).none { inRing(it,longitude,latitude) }
                        }==true
                }.map { building ->
                    val rates=building.profile?.let { profile ->
                        val curve=curves.getValue(profile).jsonObject
                        val rawPosition=(instrumentalIntensity-5)*10
                        // Decimal grid points (e.g. 6.3) must use the exact table value.
                        val nearest=round(rawPosition)
                        val position=if(abs(rawPosition-nearest)<1e-10) nearest else rawPosition
                        val lo=floor(position).toInt().coerceAtMost(19)
                        val fraction=position-lo
                        fun interpolate(key: String): Double {
                            val values=curve.getValue(key).jsonArray
                            require(values.size==21)
                            return (values[lo].jsonPrimitive.double*(1-fraction)+values[lo+1].jsonPrimitive.double*fraction)/100
                        }
                        val full=interpolate("full")
                        val total=interpolate("fullHalf")
                        require(full in 0.0..1.0 && total in full..1.0)
                        BuildingDamageRates(full,total-full,total)
                    }
                    BuildingAssessment(building,instrumentalIntensity,rates,mdl.getValue("modelId").jsonPrimitive.content,limitations)
                }
            }
        }
    }

    private fun contains(b: List<Double>,x: Double,y: Double)=b.size==4 && x>=b[0] && x<=b[2] && y>=b[1] && y<=b[3]
    private fun inRing(ring: List<List<Double>>,x: Double,y: Double): Boolean {
        if(ring.size<4) return false
        var inside=false
        for(i in 0 until ring.lastIndex) {
            val (ax,ay)=ring[i]; val (bx,by)=ring[i+1]
            val cross=(x-ax)*(by-ay)-(y-ay)*(bx-ax)
            if(abs(cross)<=1e-14 && x>=minOf(ax,bx) && x<=maxOf(ax,bx) && y>=minOf(ay,by) && y<=maxOf(ay,by)) return true
            if((ay>y)!=(by>y) && x<(bx-ax)*(y-ay)/(by-ay)+ax) inside=!inside
        }
        return inside
    }
}
