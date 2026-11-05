package io.github.nevaseme.plandmk1.tsunami

/** The published bundle contains exactly one case for each integer metre from 0 through 35. */
object TsunamiPrecomputedHeight {
    fun isSupported(height: Double): Boolean = height.isFinite() && height in 0.0..35.0 && height % 1.0 == 0.0
    fun parse(text: String): Double? = text.trim().toDoubleOrNull()?.takeIf(::isSupported)
}
