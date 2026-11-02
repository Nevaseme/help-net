package io.github.nevaseme.plandmk1

import io.github.nevaseme.plandmk1.tsunami.TsunamiPrecomputedHeight
import org.junit.Assert.*
import org.junit.Test

class TsunamiPrecomputedHeightTest {
    @Test fun acceptsAllThirtySixExactHeightsIncludingZeroAndUpperBoundary() {
        for (height in 0..35) {
            assertTrue(TsunamiPrecomputedHeight.isSupported(height.toDouble()))
            assertEquals(height.toDouble(), TsunamiPrecomputedHeight.parse(height.toString())!!, 0.0)
        }
        assertEquals(3.0, TsunamiPrecomputedHeight.parse(" 3.0 ")!!, 0.0)
    }

    @Test fun rejectsUncomputedHeightsInsteadOfRoundingOrClamping() {
        listOf(-1.0, -.1, .1, 7.25, 34.999, 35.001, 36.0, Double.NaN, Double.POSITIVE_INFINITY).forEach {
            assertFalse(TsunamiPrecomputedHeight.isSupported(it))
            assertNull(TsunamiPrecomputedHeight.parse(it.toString()))
        }
        listOf("", " ", "abc", "3m").forEach { assertNull(TsunamiPrecomputedHeight.parse(it)) }
    }
}
