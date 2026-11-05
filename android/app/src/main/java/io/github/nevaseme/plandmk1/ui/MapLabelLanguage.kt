package io.github.nevaseme.plandmk1.ui

import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.PropertyFactory.textField
import org.maplibre.android.style.layers.SymbolLayer

/** Change geographic labels only; route numbers and participant text are untouched. */
class MapLabelLanguage {
    private val original=mutableMapOf<String,Expression>()
    fun apply(style:Style,english:Boolean) {
        for(layer in style.layers.filterIsInstance<SymbolLayer>()) {
            val field=layer.textField.expression?:continue
            if(!field.toString().contains("name"))continue
            original.putIfAbsent(layer.id,field)
            layer.setProperties(textField(if(english)Expression.raw("""["coalesce",["get","name:en"],["get","name_en"],["get","name:latin"],""]""")else original.getValue(layer.id)))
        }
    }
}
