package io.github.nevaseme.plandmk1

import io.github.nevaseme.plandmk1.enclosure.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

class EnclosureFeaturesTest {
    @Test fun missingSensorDoesNotBecomeAZeroReading() {
        val missing = EnclosureFeatures.scalar(emptyList())
        assertEquals(0, missing.count)
        assertNull(missing.mean)
        assertNull(missing.minimum)
        assertNull(missing.populationSd)
        val dark = EnclosureFeatures.scalar(listOf(ScalarObservation(0, 0.0)))
        assertEquals(0.0, dark.mean!!, 0.0)
    }

    @Test fun rejectsInvalidAndRepeatedTimestampsAndRecordsObservationGaps() {
        val result = EnclosureFeatures.scalar(listOf(
            ScalarObservation(-1, 1.0), ScalarObservation(0, 2.0),
            ScalarObservation(0, 99.0), ScalarObservation(1, Double.NaN),
            ScalarObservation(2_000_000_000, 4.0), ScalarObservation(1, 8.0)))
        assertEquals(2, result.count)
        assertEquals(4, result.rejected)
        assertEquals(3.0, result.mean!!, 0.0)
        assertEquals(1.0, result.populationSd!!, 0.0)
        assertEquals(2_000_000_000L, result.maxGapNanos)
        assertEquals(2_000_000_000L, result.spanNanos)
    }

    @Test fun orientationChangeIsVisibleEvenWhenAccelerationMagnitudeIsUnchanged() {
        val still = EnclosureFeatures.vector(listOf(VectorObservation(0, 0.0, 0.0, 10.0), VectorObservation(1, 0.0, 0.0, 10.0)))
        val rotated = EnclosureFeatures.vector(listOf(VectorObservation(0, 0.0, 0.0, 10.0), VectorObservation(1, 0.0, 10.0, 0.0)))
        assertEquals(0.0, still.centeredVectorRms!!, 0.0)
        assertEquals(sqrt(50.0), rotated.centeredVectorRms!!, 1e-12)
        assertEquals(10.0, rotated.magnitude.mean!!, 0.0)
        assertEquals(0.0, rotated.magnitude.populationSd!!, 0.0)
    }

    @Test fun vectorRejectsNonfiniteComponentsAndDoesNotInventAReading() {
        val result = EnclosureFeatures.vector(listOf(VectorObservation(0, Double.NaN, 1.0, 2.0)))
        assertEquals(1, result.magnitude.rejected)
        assertNull(result.centeredVectorRms)
    }

    @Test fun audioReportsSilenceAsNoBandEvidenceAndSeparatesDcFromSound() {
        val bands = listOf(FrequencyBand("low", 0.0, 2000.0))
        val silent = EnclosureFeatures.audio(DoubleArray(256), 8000, bands)
        assertEquals(0.0, silent.rms, 0.0)
        assertNull(silent.bandEnergyFractions["low"])
        val dc = EnclosureFeatures.audio(DoubleArray(256) { 0.25 }, 8000, bands)
        assertEquals(0.25, dc.dc, 0.0)
        assertEquals(0.25, dc.rms, 0.0)
        assertEquals(0.0, dc.acRms, 0.0)
        assertNull(dc.bandEnergyFractions["low"])
    }

    @Test fun separatesKnownTonesAndKeepsRelativeBandsIndependentOfGain() {
        val bands = listOf(FrequencyBand("low", 500.0, 1500.0), FrequencyBand("high", 2500.0, 3500.0))
        val samples = DoubleArray(1024) { .5 * sin(2 * PI * 1000 * it / 8192) + .25 * sin(2 * PI * 3000 * it / 8192) }
        val original = EnclosureFeatures.audio(samples, 8192, bands)
        val quieter = EnclosureFeatures.audio(samples.map { it * .1 }.toDoubleArray(), 8192, bands)
        assertEquals(sqrt(.15625), original.acRms, 1e-10)
        assertEquals(.8, original.bandEnergyFractions.getValue("low")!!, 1e-6)
        assertEquals(.2, original.bandEnergyFractions.getValue("high")!!, 1e-6)
        assertEquals(original.bandEnergyFractions.getValue("low")!!, quieter.bandEnergyFractions.getValue("low")!!, 1e-12)
        assertEquals(original.rms * .1, quieter.rms, 1e-12)
    }

    @Test fun invalidPcmAndBandsAreRejectedInsteadOfGeneratingEvidence() {
        assertThrows(IllegalArgumentException::class.java) { EnclosureFeatures.audio(doubleArrayOf(Double.NaN, 0.0, 0.0, 0.0), 8000, emptyList()) }
        assertThrows(IllegalArgumentException::class.java) { EnclosureFeatures.audio(doubleArrayOf(2.0, 0.0, 0.0, 0.0), 8000, emptyList()) }
        assertThrows(IllegalArgumentException::class.java) { EnclosureFeatures.audio(DoubleArray(7), 8000, emptyList()) }
        assertThrows(IllegalArgumentException::class.java) { EnclosureFeatures.audio(DoubleArray(8), 8000, listOf(FrequencyBand("bad", 0.0, 5000.0))) }
        val clipping = EnclosureFeatures.audio(doubleArrayOf(1.0, -1.0, 0.0, 0.0), 8000, emptyList())
        assertEquals(.5, clipping.clippedFraction, 0.0)
    }

    @Test fun knownProbeDelaySurvivesGainOffsetAndPolarityChanges() {
        val probe = doubleArrayOf(.1, -.4, .7, -.2, .3, -.8, .5, -.1)
        val recording = DoubleArray(30) { .2 }
        probe.forEachIndexed { i, value -> recording[i + 13] = .2 - .5 * value }
        val result = EnclosureFeatures.matchProbe(probe, recording, 0, 22)!!
        assertEquals(13, result.lagSamples)
        assertEquals(-1.0, result.correlation, 1e-12)
        assertNull(EnclosureFeatures.matchProbe(probe, DoubleArray(30), 0, 22))
        assertNull(EnclosureFeatures.matchProbe(DoubleArray(8), recording, 0, 22))
    }

    @Test fun correlationSearchCannotReadOutsideRecording() {
        assertThrows(IllegalArgumentException::class.java) { EnclosureFeatures.matchProbe(doubleArrayOf(0.0, 1.0), DoubleArray(4), -1, 2) }
        assertThrows(IllegalArgumentException::class.java) { EnclosureFeatures.matchProbe(doubleArrayOf(0.0, 1.0), DoubleArray(4), 0, 3) }
    }

    @Test fun constantNonBinaryDcDoesNotTurnRoundoffIntoSoundOrAMatch() {
        val dc = EnclosureFeatures.audio(DoubleArray(256) { .2 }, 8000, listOf(FrequencyBand("all", 0.0, 4000.0)))
        assertEquals(0.0, dc.acRms, 0.0)
        assertNull(dc.bandEnergyFractions["all"])
        assertNull(EnclosureFeatures.matchProbe(DoubleArray(8) { .2 }, DoubleArray(30) { .4 }, 0, 22))
        assertNull(EnclosureFeatures.matchProbe(doubleArrayOf(.1, -.2, .3, -.4), DoubleArray(30) { .2 }, 0, 26))
    }

    @Test fun generatedProbeHasTaperedEndsAndCanBeRecoveredAfterKnownDelay() {
        val probe = EnclosureFeatures.chirp(48000, 512, 2000.0, 6000.0, .2)
        assertEquals(0.0, probe.first(), 1e-12)
        assertEquals(0.0, probe.last(), 1e-12)
        assertTrue(probe.all { it.isFinite() && kotlin.math.abs(it) <= .2 })
        val recording = DoubleArray(700)
        probe.copyInto(recording, 73)
        assertEquals(73, EnclosureFeatures.matchProbe(probe, recording, 0, 188)!!.lagSamples)
        assertThrows(IllegalArgumentException::class.java) { EnclosureFeatures.chirp(8000, 128, 1000.0, 5000.0, .2) }
    }

    @Test fun passiveBandsSeparateLowFrequencyTonesWithoutChangingTotalPower() {
        val bands = EnclosureFeatures.passiveAudioBands48kHz
        assertEquals(listOf(0.0, 250.0, 500.0, 1000.0, 2000.0, 4000.0, 6000.0, 12000.0), bands.map { it.lowHz })
        assertEquals(listOf(250.0, 500.0, 1000.0, 2000.0, 4000.0, 6000.0, 12000.0, 24000.0), bands.map { it.highHz })
        for (band in bands) {
            val frequency = (band.lowHz + band.highHz) / 2
            val pcm = DoubleArray(4096) { .25 * kotlin.math.sin(2 * Math.PI * frequency * it / 48000) }
            val features = EnclosureFeatures.audio(pcm, 48000, bands)
            assertTrue(features.bandEnergyFractions.getValue(band.label)!! > .99)
            assertEquals(1.0, features.bandEnergyFractions.values.sumOf { it!! }, 1e-10)
            assertEquals(features.totalSpectralPower, features.bandPowers.values.sum(), 1e-12)
            val quiet = EnclosureFeatures.audio(pcm.map { it / 2 }.toDoubleArray(), 48000, bands)
            assertEquals(features.totalSpectralPower / 4, quiet.totalSpectralPower, 1e-12)
            assertEquals(features.bandPowers.getValue(band.label) / 4, quiet.bandPowers.getValue(band.label), 1e-12)
            assertEquals(features.bandEnergyFractions.getValue(band.label)!!, quiet.bandEnergyFractions.getValue(band.label)!!, 1e-12)
        }
    }
}
