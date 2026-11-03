package io.github.nevaseme.plandmk1.buildings

import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression.raw
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.PropertyFactory.*
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.VectorSource

/** Native MVT decoding/caching. Never parses city-wide geometry on the UI thread. */
object BuildingRiskOverlay {
    const val SOURCE="kyoto-risk"
    const val BUILDINGS="kyoto-risk-buildings"
    const val OVERVIEW="kyoto-risk-overview"
    private fun color(rate:String)=raw("""["case",["<",$rate,0],"#D4D7DC",["<",$rate,10],"#FBE8C5",["<",$rate,40],"#F2AE5D","#C5672E"]""")
    fun install(style:Style,model:BuildingMapModel,intensity:Double) {
        for ((SOURCE,asset) in listOf("kyoto-risk" to "kyoto-risk-tiles", "toranomon-risk" to "toranomon-risk-tiles")) {
        val BUILDINGS="$SOURCE-buildings"; val OVERVIEW="$SOURCE-overview"
        if(style.getSource(SOURCE)==null)style.addSource(VectorSource(SOURCE,"asset://$asset/tiles.json"))
        val before=style.layers.firstOrNull {it is SymbolLayer}?.id
        fun add(layer:FillLayer) { if(before!=null)style.addLayerBelow(layer,before) else style.addLayer(layer) }
        if(style.getLayer(BUILDINGS)==null) {
            add(FillLayer(OVERVIEW,SOURCE).withSourceLayer("footprints").apply {minZoom=8f;maxZoom=14f})
            add(FillLayer(BUILDINGS,SOURCE).withSourceLayer("buildings").apply {minZoom=14f})
        }
        (style.getLayer(BUILDINGS) as FillLayer).setProperties(fillColor(color(model.buildingExpression(intensity))),fillOpacity(.72f),fillOutlineColor("#B5A696"))
        (style.getLayer(OVERVIEW) as FillLayer).setProperties(fillColor(color(model.buildingExpression(intensity))),fillOpacity(.72f))
        }
    }
}
