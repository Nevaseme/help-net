package io.github.nevaseme.plandmk1.buildings

/** PlanD categories of conditional full-damage rate, not collapse or a safety diagnosis.
 * Thresholds approved 2026-10-26; these are not Cabinet Office categories.
 */
enum class BuildingRiskLevel {
    LOW, MEDIUM, HIGH, UNKNOWN;

    companion object {
        /** Input is a fraction (0..1), not percent. Unknown must not become zero. */
        fun fromFullDamageRate(rate: Double?): BuildingRiskLevel {
            if (rate == null) return UNKNOWN
            require(rate.isFinite() && rate in 0.0..1.0) { "Full-damage rate must be within 0..1" }
            return when {
                rate < 0.10 -> LOW
                rate < 0.40 -> MEDIUM
                else -> HIGH
            }
        }
    }
}
