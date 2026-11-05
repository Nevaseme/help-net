package io.github.nevaseme.plandmk1.buildings

/** Separate envelopes; the area between Kyoto and Tokyo is not covered. */
object BuildingMapCoverage {
    const val WEST = 135.6529811299958
    const val SOUTH = 34.88252512272187
    const val EAST = 135.84025298229068
    const val NORTH = 35.09395768527534

    fun intersects(south: Double, west: Double, north: Double, east: Double): Boolean =
        listOf(listOf(WEST,SOUTH,EAST,NORTH),listOf(139.73779171125489,35.656350185935715,139.76240247196802,35.67624752866467)).any { b->
            north>=b[1] && south<=b[3] && (if(west<=east) east>=b[0]&&west<=b[2] else east>=b[0]||west<=b[2])
        }
}
