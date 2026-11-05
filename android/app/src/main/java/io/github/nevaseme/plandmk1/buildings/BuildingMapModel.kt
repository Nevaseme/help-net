package io.github.nevaseme.plandmk1.buildings

import kotlinx.serialization.json.*
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.round

/** Percent units, identical interpolation to the assessment repository. */
class BuildingMapModel(private val curves: Map<String,List<Double>>) {
    init { require(curves.values.all { it.size==21 && it.all { v -> v.isFinite() && v in 0.0..100.0 } }) }
    private fun position(intensity:Double):Pair<Int,Double> {
        require(intensity.isFinite() && intensity in 5.0..7.0)
        val raw=(intensity-5)*10
        val nearest=round(raw)
        val pos=if(abs(raw-nearest)<1e-10)nearest else raw
        val lo=floor(pos).toInt().coerceAtMost(19)
        return lo to pos-lo
    }
    fun percent(profile:String,intensity:Double):Double? {
        val (lo,f)=position(intensity)
        return curves[profile]?.let { it[lo]*(1-f)+it[lo+1]*f }
    }
    fun buildingExpression(intensity:Double):String = buildJsonArray {
        add("match");add(buildJsonArray {add("get");add("p")})
        curves.keys.forEach { add(it);add(percent(it,intensity)!!) };add(-1)
    }.toString()
    companion object {
        const val DEFAULT_INTENSITY=6.2
        fun parse(text:String)=BuildingMapModel(Json.parseToJsonElement(text).jsonObject.getValue("curves").jsonObject.mapValues {
            it.value.jsonObject.getValue("full").jsonArray.map { n->n.jsonPrimitive.double }
        })
    }
}
