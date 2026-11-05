package io.github.nevaseme.plandmk1.tsunami

import kotlinx.serialization.json.*

object TsunamiDepth {
    val colors=listOf("#ffffb2","#fed976","#feb24c","#fd8d3c","#e31a1c")
    val labels=listOf("0.01–0.5m","0.5–1m","1–2m","2–3m","3m以上")
    fun color(depth:Double):String? = when {
        !depth.isFinite() || depth<.01 -> null
        depth<.5 -> colors[0]
        depth<1 -> colors[1]
        depth<2 -> colors[2]
        depth<3 -> colors[3]
        else -> colors[4]
    }
}

class TsunamiCase(private val json:JsonObject) {
    val id=json.getValue("id").jsonPrimitive.content
    val title=json.getValue("title").jsonPrimitive.content
    val target=number("target_height_m")
    val achieved=number("achieved_height_m")
    val spacing=number("grid_spacing_m")
    val threshold=number("threshold_m")
    val duration=number("duration_s")
    val area=number("inundated_area_m2")
    val maximum=number("max_ground_depth_m")
    val coverage=json.getValue("coverage").toString()
    val reference=json.getValue("reference").toString()
    val features=buildJsonObject {
        put("type","FeatureCollection")
        putJsonArray("features") {
            json.getValue("features").jsonObject.getValue("features").jsonArray.forEach { source ->
                val feature=source.jsonObject
                val properties=feature.getValue("properties").jsonObject
                val depth=properties.getValue("max_depth_m").jsonPrimitive.double
                add(JsonObject(feature+ ("properties" to JsonObject(properties+("depth_color" to JsonPrimitive(requireNotNull(TsunamiDepth.color(depth))))))))
            }
        }
    }.toString()
    val featureCount=json.getValue("features").jsonObject.getValue("features").jsonArray.size
    val conditions=json.getValue("conditions").jsonObject
    val limitations=json.getValue("limitations").jsonArray.map { it.jsonPrimitive.content }
    private fun number(key:String)=json.getValue(key).jsonPrimitive.double.also {require(it.isFinite())}
    init {
        require(target>0 && threshold==.01 && spacing>0)
        require(kotlin.math.abs(achieved-target)<=.05)
        require(kotlin.math.abs(achieved-target-number("residual_m"))<=1e-6)
        require(json.getValue("coverage").jsonObject.getValue("type").jsonPrimitive.content=="Polygon")
        require(json.getValue("reference").jsonObject.getValue("type").jsonPrimitive.content=="Point")
        json.getValue("features").jsonObject.getValue("features").jsonArray.forEach {
            val depth=it.jsonObject.getValue("properties").jsonObject.getValue("max_depth_m").jsonPrimitive.double
            require(depth.isFinite() && depth>=threshold)
        }
    }
}

class TsunamiMapData(val cases:List<TsunamiCase>,defaultId:String) {
    val defaultCase=cases.single {it.id==defaultId}
    fun case(id:String)=cases.find { it.id==id }
    /** Exact published height only; neither nearest-height substitution nor field scaling. */
    fun caseForHeight(height:Double)=cases.singleOrNull {it.target==height}
    companion object {
        fun parse(text:String):TsunamiMapData {
            val json=Json.parseToJsonElement(text).jsonObject
            require(json.getValue("schema").jsonPrimitive.content=="pland-tsunami-map-1")
            return TsunamiMapData(json.getValue("cases").jsonArray.map {TsunamiCase(it.jsonObject)},json.getValue("default_case_id").jsonPrimitive.content)
        }
    }
}
