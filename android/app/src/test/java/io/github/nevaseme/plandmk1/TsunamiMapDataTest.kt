package io.github.nevaseme.plandmk1

import io.github.nevaseme.plandmk1.tsunami.*
import org.junit.Assert.*
import org.junit.Test
import kotlinx.serialization.json.*

class TsunamiMapDataTest {
    @Test fun depthClassesUseClosedLowerBoundsAndNeverColorZero() {
        assertNull(TsunamiDepth.color(0.0))
        assertNull(TsunamiDepth.color(.009))
        assertEquals("#ffffb2", TsunamiDepth.color(.01))
        assertEquals("#ffffb2", TsunamiDepth.color(.499999))
        assertEquals("#fed976", TsunamiDepth.color(.5))
        assertEquals("#feb24c", TsunamiDepth.color(1.0))
        assertEquals("#fd8d3c", TsunamiDepth.color(2.0))
        assertEquals("#e31a1c", TsunamiDepth.color(3.0))
    }
    @Test fun bundledCasesRetainCalibratedResultsAndSourceMetadata() {
        val data=TsunamiMapData.parse(java.io.File("src/main/assets/tsunami-atami.json").readText())
        assertEquals(10.0,data.defaultCase.spacing,0.0)
        assertTrue(data.cases.all { it.target>0 && it.threshold==.01 && it.duration==900.0 && it.featureCount>0 })
        assertNull(data.case("not-computed"))
        assertTrue(data.cases.all { it.features.contains("max_depth_m") && it.limitations.isNotEmpty() })
    }
    @Test fun selectsTsunamiHeightNotGridSpacingAndNeverSubstitutesMissingHeight() {
        val ten=TsunamiCase(fixture(10.0,9.999,-.001))
        val twenty=TsunamiCase(fixture(20.0,20.02,.02))
        val data=TsunamiMapData(listOf(ten,twenty),ten.id)
        assertEquals(10.0,ten.spacing,0.0)
        assertEquals(10.0,twenty.spacing,0.0)
        assertSame(ten,data.caseForHeight(10.0))
        assertSame(twenty,data.caseForHeight(20.0))
        assertNull(data.caseForHeight(15.0))
        assertNull(TsunamiMapData(listOf(ten),ten.id).caseForHeight(20.0))
        val decimal=TsunamiCase(fixture(7.25,7.249,-.001))
        val arbitrary=TsunamiMapData(listOf(decimal),decimal.id)
        assertSame(decimal,arbitrary.caseForHeight(7.25))
        assertNull(arbitrary.caseForHeight(7.2))
    }
    @Test fun rejectsUncalibratedAndContradictoryHeightResults() {
        assertThrows(IllegalArgumentException::class.java) {TsunamiCase(fixture(10.0,9.0,-1.0))}
        assertThrows(IllegalArgumentException::class.java) {TsunamiCase(fixture(10.0,9.999,0.02))}
    }
    // Fictional unit fixture only. This does not generate or publish a simulation result.
    private fun fixture(target:Double,achieved:Double,residual:Double)=Json.parseToJsonElement("""{
        "id":"test-height-$target","title":"synthetic unit fixture","target_height_m":$target,
        "achieved_height_m":$achieved,"residual_m":$residual,"grid_spacing_m":10,"threshold_m":0.01,
        "duration_s":900,"inundated_area_m2":25,"max_ground_depth_m":0.5,
        "coverage":{"type":"Polygon","coordinates":[[[139,35],[139.01,35],[139,35.01],[139,35]]]},
        "reference":{"type":"Point","coordinates":[139,35]},
        "features":{"type":"FeatureCollection","features":[{"type":"Feature","properties":{"max_depth_m":0.5},"geometry":{"type":"Polygon","coordinates":[[[139,35],[139.01,35],[139,35.01],[139,35]]]}}]},
        "conditions":{},"limitations":["synthetic unit fixture"]
    }""").jsonObject
    @Test fun refusesUnknownSchemaInsteadOfShowingAnUnverifiedShape() {
        assertThrows(IllegalArgumentException::class.java) { TsunamiMapData.parse("""{"schema":"other"}""") }
    }
}
