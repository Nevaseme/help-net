package io.github.nevaseme.plandmk1.tsunami

import kotlinx.serialization.json.*

/** Display-only adapter for the same reviewed precomputed cases used by the browser. */
data class TsunamiComputedResult(
    val height: Double, val achieved: Double, val features: String, val coverage: String,
    val reference: String, val bounds: List<List<Double>>, val area: Double, val maximum: Double,
    val description: String, val displayThreshold: Double,
) {
    companion object {
        fun parse(json: JsonObject, requestedHeight: Double): TsunamiComputedResult {
            fun number(key: String) = json.getValue(key).jsonPrimitive.double.also { require(it.isFinite()) }
            val height = number("heightM")
            require(height == requestedHeight && TsunamiPrecomputedHeight.isSupported(height))
            val achieved = number("achievedHeightM")
            val metadata = json.getValue("metadata").jsonObject
            require(metadata.getValue("precomputed").jsonPrimitive.boolean)
            val displayThreshold = metadata.getValue("displayDepthThresholdM").jsonPrimitive.double
            require(displayThreshold.isFinite() && displayThreshold > 0)
            val calibration = metadata.getValue("calibration").jsonObject
            val tolerance = calibration.getValue("tolerance_m").jsonPrimitive.double
            require(tolerance.isFinite() && tolerance > 0)
            require(calibration.getValue("target_height_m").jsonPrimitive.double == height)
            require(kotlin.math.abs(achieved - height) <= tolerance + 1e-8)
            require(metadata.getValue("diagnostics").jsonObject.getValue("completed").jsonPrimitive.boolean)
            val audit = metadata.getValue("boundaryAudit").jsonObject
            require(!audit.getValue("inundation_reaches_closed_cut_boundary").jsonPrimitive.boolean)
            require(!audit.getValue("supercritical_open_boundary").jsonPrimitive.boolean)
            val geojson = json.getValue("geojson").jsonObject
            require(geojson.getValue("type").jsonPrimitive.content == "FeatureCollection")
            val colored = JsonObject(geojson + ("features" to JsonArray(geojson.getValue("features").jsonArray.map { raw ->
                val feature = raw.jsonObject
                val properties = feature.getValue("properties").jsonObject
                val color = requireNotNull(TsunamiDepth.color(properties.getValue("max_depth_m").jsonPrimitive.double))
                JsonObject(feature + ("properties" to JsonObject(properties + ("depth_color" to JsonPrimitive(color)))))
            })))
            val coverage = json.getValue("coverage").jsonObject
            require(coverage.getValue("type").jsonPrimitive.content == "FeatureCollection")
            val reference = json.getValue("referencePoint").jsonArray
            require(reference.size == 2 && reference.all { it.jsonPrimitive.double.isFinite() })
            val bounds = json.getValue("bounds").jsonArray.map { row -> row.jsonArray.map { it.jsonPrimitive.double.also { n -> require(n.isFinite()) } } }
            require(bounds.size == 2 && bounds.all { it.size == 2 })
            return TsunamiComputedResult(height, achieved, colored.toString(), coverage.toString(),
                buildJsonObject { put("type", "Point"); put("coordinates", reference) }.toString(), bounds,
                metadata.getValue("inundatedAreaM2").jsonPrimitive.double,
                metadata.getValue("maximumDepthM").jsonPrimitive.double,
                metadata.getValue("description").jsonPrimitive.content, displayThreshold)
        }
    }
}
