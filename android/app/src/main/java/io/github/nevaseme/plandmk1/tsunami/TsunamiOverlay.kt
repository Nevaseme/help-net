package io.github.nevaseme.plandmk1.tsunami

import org.maplibre.android.maps.Style
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.style.layers.*
import org.maplibre.android.style.layers.PropertyFactory.*
import org.maplibre.android.style.expressions.Expression.get
import org.maplibre.geojson.*

/** Result layers share the ordinary map, its camera, markers and lifecycle. */
object TsunamiOverlay {
    private val empty = FeatureCollection.fromFeatures(emptyArray<Feature>())
    fun update(style:Style,result:TsunamiComputedResult?) {
        if(style.getSource("tsunami-depth")==null) {
            for(id in listOf("tsunami-depth","tsunami-coverage")) style.addSource(GeoJsonSource(id,empty))
            style.addLayer(FillLayer("tsunami-depth-fill","tsunami-depth").withProperties(fillColor(get("depth_color")),fillOpacity(.72f)))
            style.addLayer(LineLayer("tsunami-coverage-line","tsunami-coverage").withProperties(lineColor("#27485b"),lineWidth(2f)))
        }
        style.getSourceAs<GeoJsonSource>("tsunami-depth")?.setGeoJson(result?.features?.let(FeatureCollection::fromJson) ?: empty)
        style.getSourceAs<GeoJsonSource>("tsunami-coverage")?.setGeoJson(result?.coverage?.let(FeatureCollection::fromJson) ?: empty)
    }
}
