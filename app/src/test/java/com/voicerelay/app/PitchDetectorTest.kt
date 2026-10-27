package com.voicerelay.app

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

/**
 * Pure JVM Unit test verifying the YIN pitch detection algorithm,
 * silence detection, and musical scale snapping.
 */
class PitchDetectorTest {

    class PureYinDetector(
        private val sampleRate: Int = 48000,
        private val bufferSize: Int = 1024,
        private val threshold: Float = 0.15f
    ) {
        private val halfBufferSize = bufferSize / 2
        private val yinBuffer = FloatArray(halfBufferSize)

        fun detectPitch(buffer: FloatArray): Float {
            // Check RMS
            var energy = 0f
            for (i in 0 until bufferSize) {
                energy += buffer[i] * buffer[i]
            }
            val rms = sqrt(energy / bufferSize)
            if (rms < 0.015f) return -1f // Silence check

            // Step 1: Difference function
            for (tau in 0 until halfBufferSize) {
                var sum = 0f
                for (i in 0 until halfBufferSize) {
                    val delta = buffer[i] - buffer[i + tau]
                    sum += delta * delta
                }
                yinBuffer[tau] = sum
            }

            // Step 2: Cumulative mean normalized difference
            yinBuffer[0] = 1f
            var runningSum = 0f
            for (tau in 1 until halfBufferSize) {
                runningSum += yinBuffer[tau]
                yinBuffer[tau] = if (runningSum > 1e-9f) {
                    (yinBuffer[tau] * tau) / runningSum
                } else {
                    1f
                }
            }

            // Step 3: Absolute threshold
            var tauEstimate = -1
            for (tau in 2 until halfBufferSize) {
                if (yinBuffer[tau] < threshold) {
                    var t = tau
                    while (t + 1 < halfBufferSize && yinBuffer[t + 1] < yinBuffer[t]) {
                        t++
                    }
                    tauEstimate = t
                    break
                }
            }

            if (tauEstimate <= 0) return -1f

            // Step 4: Parabolic interpolation
            val s0 = yinBuffer[tauEstimate - 1]
            val s1 = yinBuffer[tauEstimate]
            val s2 = yinBuffer[tauEstimate + 1]
            val denom = 2f * (2f * s1 - s2 - s0)
            val delta = if (abs(denom) > 1e-9f) (s2 - s0) / denom else 0f
            val refinedTau = tauEstimate + delta

            return if (refinedTau > 0f) sampleRate / refinedTau else -1f
        }
    }

    private fun generateSineWave(freqHz: Float, sampleRate: Int, numSamples: Int): FloatArray {
        return FloatArray(numSamples) { i ->
            sin(2.0 * PI * freqHz * i / sampleRate).toFloat()
        }
    }

    @Test
    fun testDetect440HzConcertPitch() {
        val sampleRate = 48000
        val targetFreq = 440.0f // A4
        val audio = generateSineWave(targetFreq, sampleRate, 1024)

        val detector = PureYinDetector(sampleRate = sampleRate, bufferSize = 1024)
        val detected = detector.detectPitch(audio)

        assertTrue("Expected pitch near 440Hz, got $detected", abs(detected - targetFreq) < 5.0f)
    }

    @Test
    fun testDetect220HzPitch() {
        val sampleRate = 48000
        val targetFreq = 220.0f // A3
        val audio = generateSineWave(targetFreq, sampleRate, 1024)

        val detector = PureYinDetector(sampleRate = sampleRate, bufferSize = 1024)
        val detected = detector.detectPitch(audio)

        assertTrue("Expected pitch near 220Hz, got $detected", abs(detected - targetFreq) < 5.0f)
    }

    @Test
    fun testDetect880HzPitch() {
        val sampleRate = 48000
        val targetFreq = 880.0f // A5
        val audio = generateSineWave(targetFreq, sampleRate, 1024)

        val detector = PureYinDetector(sampleRate = sampleRate, bufferSize = 1024)
        val detected = detector.detectPitch(audio)

        assertTrue("Expected pitch near 880Hz, got $detected", abs(detected - targetFreq) < 10.0f)
    }

    @Test
    fun testSilenceRejection() {
        val sampleRate = 48000
        val silence = FloatArray(1024) { 0f }

        val detector = PureYinDetector(sampleRate = sampleRate, bufferSize = 1024)
        val detected = detector.detectPitch(silence)

        assertEquals("Silence must return -1f", -1.0f, detected, 0.001f)
    }

    @Test
    fun testScaleSnappingLogic() {
        // Test that 445 Hz snaps to 440 Hz (A4)
        val inputHz = 445.0f
        val midiFloat = 69.0f + 12.0f * (ln(inputHz / 440.0f) / ln(2.0f))
        val snappedMidi = round(midiFloat).toInt()
        val snappedFreq = 440.0f * (2.0f).pow((snappedMidi - 69) / 12.0f)

        assertEquals("Snapped MIDI note should be 69 (A4)", 69, snappedMidi)
        assertEquals("Snapped frequency should be 440Hz", 440.0f, snappedFreq, 0.01f)
    }
}
