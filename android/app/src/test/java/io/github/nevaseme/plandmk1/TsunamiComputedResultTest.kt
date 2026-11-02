package io.github.nevaseme.plandmk1

import io.github.nevaseme.plandmk1.tsunami.TsunamiComputedResult
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class TsunamiComputedResultTest {
    private fun fixture(height: Double = 7.0, achieved: Double = 6.999, cut: Boolean = false, precomputed: Boolean = true) = Json.parseToJsonElement("""{
        "heightM":$height,"achievedHeightM":$achieved,
        "geojson":{"type":"FeatureCollection","features":[{"type":"Feature","properties":{"max_depth_m":0.5},"geometry":{"type":"Polygon","coordinates":[[[139,35],[139.01,35],[139,35.01],[139,35]]]}}]},
        "coverage":{"type":"FeatureCollection","features":[]},"referencePoint":[139,35],"bounds":[[139,35],[139.01,35.01]],
        "metadata":{"precomputed":$precomputed,"calibration":{"target_height_m":$height,"tolerance_m":0.002},"diagnostics":{"completed":true},"boundaryAudit":{"inundation_reaches_closed_cut_boundary":$cut,"supercritical_open_boundary":false},"inundatedAreaM2":25,"maximumDepthM":0.5,"displayDepthThresholdM":0.1,"description":"synthetic unit fixture"}
    }""").jsonObject

    @Test fun displaysExactPrecomputedRequestWithoutScalingDepths() {
        val r = TsunamiComputedResult.parse(fixture(), 7.0)
        assertEquals(7.0, r.height, 0.0)
        val properties = Json.parseToJsonElement(r.features).jsonObject["features"]!!.jsonArray[0].jsonObject["properties"]!!.jsonObject
        assertEquals(.5, properties["max_depth_m"]!!.jsonPrimitive.double, 0.0)
        assertEquals("#fed976", properties["depth_color"]!!.jsonPrimitive.content)
    }
    @Test fun rejectsStaleHeightFailedCalibrationAndBoundaryAffectedResult() {
        assertThrows(IllegalArgumentException::class.java) { TsunamiComputedResult.parse(fixture(), 3.0) }
        assertThrows(IllegalArgumentException::class.java) { TsunamiComputedResult.parse(fixture(achieved=6.0), 7.0) }
        assertThrows(IllegalArgumentException::class.java) { TsunamiComputedResult.parse(fixture(cut=true), 7.0) }
    }
    @Test fun rejectsUncomputedHeightsAndLiveResultsEvenWhenCalibrationMatches() {
        for(height in listOf(.5, -1.0, 36.0)) {
            assertThrows(IllegalArgumentException::class.java) { TsunamiComputedResult.parse(fixture(height=height, achieved=height), height) }
        }
        assertThrows(IllegalArgumentException::class.java) { TsunamiComputedResult.parse(fixture(precomputed=false), 7.0) }
    }
}
