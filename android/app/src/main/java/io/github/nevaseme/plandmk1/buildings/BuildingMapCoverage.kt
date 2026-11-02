package io.github.nevaseme.plandmk1.buildings

/** Envelope of the packaged Kyoto dataset (kyoto-risk-tiles/tiles.json). */
object BuildingMapCoverage {
    const val WEST = 135.6529811299958
    const val SOUTH = 34.88252512272187
    const val EAST = 135.84025298229068
    const val NORTH = 35.09395768527534

    fun intersects(south: Double, west: Double, north: Double, east: Double): Boolean =
        north >= SOUTH && south <= NORTH &&
            if (west <= east) east >= WEST && west <= EAST else east >= WEST || west <= EAST
}
