package io.github.nevaseme.plandmk1

import io.github.nevaseme.plandmk1.buildings.BuildingMapCoverage
import org.junit.Assert.*
import org.junit.Test

class BuildingMapCoverageTest {
    @Test fun distinguishesKyotoTokyoAndWideJapanView() {
        assertTrue(BuildingMapCoverage.intersects(35.00,135.74,35.02,135.77))
        assertFalse(BuildingMapCoverage.intersects(35.71,139.75,35.73,139.77))
        assertTrue(BuildingMapCoverage.intersects(30.0,130.0,40.0,145.0))
    }
    @Test fun datelineViewportDoesNotIncludeKyotoUnlessItReachesIt() {
        assertFalse(BuildingMapCoverage.intersects(30.0,170.0,40.0,-170.0))
        assertTrue(BuildingMapCoverage.intersects(30.0,130.0,40.0,-170.0))
    }
}
