package io.github.nevaseme.plandmk1.enclosure

import kotlin.math.*

data class ScalarObservation(val elapsedNanos: Long, val value: Double)
data class VectorObservation(val elapsedNanos: Long, val x: Double, val y: Double, val z: Double)
data class ScalarFeatures(val count: Int, val rejected: Int, val minimum: Double?, val maximum: Double?,
    val mean: Double?, val populationSd: Double?, val spanNanos: Long, val maxGapNanos: Long)
data class VectorFeatures(val magnitude: ScalarFeatures, val centeredVectorRms: Double?)
data class FrequencyBand(val label: String, val lowHz: Double, val highHz: Double)
data class AcousticFeatures(val sampleCount: Int, val sampleRateHz: Int, val dc: Double, val rms: Double,
    val acRms: Double, val peak: Double, val clippedFraction: Double, val bandEnergyFractions: Map<String, Double?>,
    val bandPowers: Map<String, Double>, val totalSpectralPower: Double)
data class ProbeMatch(val lagSamples: Int, val correlation: Double)

/** Calibration features only; no confined-space decision or probability. */
object EnclosureFeatures {
    val passiveAudioBands48kHz = listOf(0, 250, 500, 1000, 2000, 4000, 6000, 12000, 24000)
        .zipWithNext { low, high -> FrequencyBand("${low}-${high}Hz", low.toDouble(), high.toDouble()) }
    /** Sample-weighted statistics. On-change sensors are not uniformly sampled in time. */
    fun scalar(samples: List<ScalarObservation>): ScalarFeatures {
        var previous = -1L
        val valid = samples.filter { sample ->
            (sample.elapsedNanos >= 0 && sample.elapsedNanos > previous && sample.value.isFinite()).also {
                if (it) previous = sample.elapsedNanos
            }
        }
        if (valid.isEmpty()) return ScalarFeatures(0, samples.size, null, null, null, null, 0, 0)
        val mean = valid.map { it.value }.average()
        val variance = valid.sumOf { (it.value - mean).pow(2) } / valid.size
        return ScalarFeatures(valid.size, samples.size - valid.size, valid.minOf { it.value }, valid.maxOf { it.value },
            mean, sqrt(variance), valid.last().elapsedNanos - valid.first().elapsedNanos,
            valid.zipWithNext { a, b -> b.elapsedNanos - a.elapsedNanos }.maxOrNull() ?: 0)
    }

    /** Includes gravity if the source is an accelerometer. A rotation is not an impact. */
    fun vector(samples: List<VectorObservation>): VectorFeatures {
        var previous = -1L
        val valid = samples.filter { sample ->
            (sample.elapsedNanos >= 0 && sample.elapsedNanos > previous &&
                hypot(hypot(sample.x, sample.y), sample.z).isFinite()).also {
                if (it) previous = sample.elapsedNanos
            }
        }
        val magnitudes = scalar(valid.map { ScalarObservation(it.elapsedNanos, hypot(hypot(it.x, it.y), it.z)) })
            .copy(rejected = samples.size - valid.size)
        if (valid.isEmpty()) return VectorFeatures(magnitudes, null)
        val x = valid.map { it.x }.average(); val y = valid.map { it.y }.average(); val z = valid.map { it.z }.average()
        val centered = sqrt(valid.sumOf { (it.x - x).pow(2) + (it.y - y).pow(2) + (it.z - z).pow(2) } / valid.size)
        return VectorFeatures(magnitudes, centered)
    }

    /** Normalized PCM [-1,1]. Band powers are Hann-windowed, DC removed, one-sided FFT fractions.
     * Bands use [lowHz, highHz); the Nyquist bin is included when highHz == sampleRate/2.
     * Silence has null fractions. These are dBFS-independent ratios, not sound pressure levels.
     */
    fun audio(pcm: DoubleArray, sampleRateHz: Int, bands: List<FrequencyBand>): AcousticFeatures {
        require(sampleRateHz > 0 && pcm.size in 4..65536 && pcm.size and (pcm.size - 1) == 0)
        require(pcm.all { it.isFinite() && abs(it) <= 1.0 })
        require(bands.map { it.label }.distinct().size == bands.size)
        require(bands.all { it.label.isNotBlank() && it.lowHz.isFinite() && it.highHz.isFinite() &&
            it.lowHz >= 0 && it.lowHz < it.highHz && it.highHz <= sampleRateHz / 2.0 })
        // Center relative to a sample first: a constant decimal level must remain exactly silent.
        val offsetMean = pcm.map { it - pcm[0] }.average()
        val dc = pcm[0] + offsetMean
        val ac = DoubleArray(pcm.size) { (pcm[it] - pcm[0]) - offsetMean }
        val window = DoubleArray(pcm.size) { .5 - .5 * cos(2 * PI * it / (pcm.size - 1)) }
        val re = DoubleArray(pcm.size) { ac[it] * window[it] }
        val im = DoubleArray(pcm.size)
        fft(re, im)
        val power = DoubleArray(pcm.size / 2 + 1) { index ->
            if (index == 0) 0.0 else (re[index] * re[index] + im[index] * im[index]) * if (index == pcm.size / 2) 1 else 2
        }
        val total = power.sum()
        val sums = bands.associate { band ->
            val sum = power.indices.filter { index ->
                val hz = index.toDouble() * sampleRateHz / pcm.size
                hz >= band.lowHz && (hz < band.highHz || hz == band.highHz && index == pcm.size / 2)
            }.sumOf { power[it] }
            band.label to sum
        }
        val fractions = sums.mapValues { (_, sum) -> if (total == 0.0) null else sum / total }
        // Parseval normalization, adjusted by mean-square Hann gain. Same DC-excluded
        // one-sided spectrum as fractions; units are normalized PCM squared, not Pa².
        val normalization = pcm.size * window.sumOf { it * it }
        val powers = sums.mapValues { (_, sum) -> sum / normalization }
        return AcousticFeatures(pcm.size, sampleRateHz, dc, sqrt(pcm.sumOf { it * it } / pcm.size),
            sqrt(ac.sumOf { it * it } / pcm.size), pcm.maxOf { abs(it) },
            pcm.count { abs(it) == 1.0 }.toDouble() / pcm.size, fractions, powers, total / normalization)
    }

    /** Best full-overlap normalized correlation; no minimum confidence or distance inference.
     * A lag includes audio pipeline delay and direct sound. It is not distance to an obstacle.
     */
    fun matchProbe(reference: DoubleArray, recording: DoubleArray, minLag: Int, maxLag: Int): ProbeMatch? {
        require(reference.size >= 2 && recording.size >= reference.size)
        require(minLag >= 0 && maxLag >= minLag && maxLag <= recording.size - reference.size)
        require(reference.all { it.isFinite() && abs(it) <= 1.0 } && recording.all { it.isFinite() && abs(it) <= 1.0 })
        val mean = reference.map { it - reference[0] }.average()
        val centered = reference.map { (it - reference[0]) - mean }
        val energy = centered.sumOf { it * it }
        if (energy == 0.0) return null
        var best: ProbeMatch? = null
        for (lag in minLag..maxLag) {
            var localMean = 0.0
            for (i in reference.indices) localMean += (recording[lag + i] - recording[lag]) / reference.size
            var covariance = 0.0; var localEnergy = 0.0
            for (i in reference.indices) {
                val value = (recording[lag + i] - recording[lag]) - localMean
                covariance += centered[i] * value
                localEnergy += value * value
            }
            if (localEnergy == 0.0) continue
            val correlation = (covariance / sqrt(energy * localEnergy)).coerceIn(-1.0, 1.0)
            if (best == null || abs(correlation) > abs(best.correlation)) best = ProbeMatch(lag, correlation)
        }
        return best
    }

    /** Generates a waveform only. This method never plays audio; parameters are experiment inputs. */
    fun chirp(sampleRateHz: Int, sampleCount: Int, startHz: Double, endHz: Double, amplitude: Double): DoubleArray {
        require(sampleRateHz > 0 && sampleCount in 4..65536)
        require(startHz.isFinite() && endHz.isFinite() && startHz > 0 && endHz > 0 &&
            max(startHz, endHz) < sampleRateHz / 2.0 && amplitude.isFinite() && amplitude > 0 && amplitude <= 1)
        val duration = (sampleCount - 1).toDouble() / sampleRateHz
        val slope = (endHz - startHz) / duration
        return DoubleArray(sampleCount) { index ->
            val t = index.toDouble() / sampleRateHz
            amplitude * (.5 - .5 * cos(2 * PI * index / (sampleCount - 1))) * sin(2 * PI * (startHz * t + .5 * slope * t * t))
        }
    }

    private fun fft(re: DoubleArray, im: DoubleArray) {
        var j = 0
        for (i in 1 until re.size) {
            var bit = re.size shr 1
            while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
            j = j xor bit
            if (i < j) { val value = re[i]; re[i] = re[j]; re[j] = value }
        }
        var length = 2
        while (length <= re.size) {
            val angle = -2 * PI / length
            for (start in re.indices step length) for (offset in 0 until length / 2) {
                val a = start + offset; val b = a + length / 2
                val c = cos(angle * offset); val s = sin(angle * offset)
                val real = re[b] * c - im[b] * s; val imaginary = re[b] * s + im[b] * c
                re[b] = re[a] - real; im[b] = im[a] - imaginary
                re[a] += real; im[a] += imaginary
            }
            length *= 2
        }
    }
}
