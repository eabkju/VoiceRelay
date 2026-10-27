package com.voicerelay.app

import org.junit.Assert.*
import org.junit.Test
import java.util.PriorityQueue

/**
 * Pure JVM Unit test verifying the adaptive jitter buffer logic,
 * packet reordering, loss detection, and Packet Loss Concealment (PLC).
 */
class JitterBufferTest {

    data class MockPacket(
        val seq: Long,
        val timestampMs: Long,
        val samples: ShortArray
    )

    class SimulatedJitterBuffer(private val targetLatencyMs: Int = 60) {
        private val queue = PriorityQueue<MockPacket>(compareBy { it.seq })
        private var nextExpectedSeq = 0L
        private var hasStarted = false
        var lostPacketsCount = 0
            private set
        var receivedPacketsCount = 0
            private set
        private var lastGoodFrame: ShortArray? = null

        fun pushPacket(packet: MockPacket) {
            receivedPacketsCount++
            if (!hasStarted && queue.isEmpty()) {
                nextExpectedSeq = packet.seq
            }
            queue.offer(packet)
        }

        fun readFrame(frameSize: Int): ShortArray {
            val targetFrames = targetLatencyMs / 10
            if (!hasStarted) {
                if (queue.size < targetFrames) {
                    return ShortArray(frameSize) // Pre-buffering silence
                }
                hasStarted = true
            }

            // Check if expected seq is at top of queue
            val peek = queue.peek()
            if (peek != null && peek.seq == nextExpectedSeq) {
                val packet = queue.poll()!!
                nextExpectedSeq++
                lastGoodFrame = packet.samples
                return packet.samples
            }

            // Packet loss occurred -> Apply PLC
            lostPacketsCount++
            nextExpectedSeq++

            val plcOutput = ShortArray(frameSize)
            lastGoodFrame?.let { prev ->
                for (i in 0 until frameSize) {
                    plcOutput[i] = (prev[i % prev.size] * 0.7f).toInt().toShort()
                }
            }
            return plcOutput
        }
    }

    @Test
    fun testInOrderPacketPlayback() {
        val buffer = SimulatedJitterBuffer(targetLatencyMs = 30) // 3 frames pre-buffer
        val frameSize = 480

        for (i in 0 until 5) {
            val pcm = ShortArray(frameSize) { (it + i * 100).toShort() }
            buffer.pushPacket(MockPacket(seq = i.toLong(), timestampMs = i * 10L, samples = pcm))
        }

        assertEquals(5, buffer.receivedPacketsCount)

        // Read frames
        val f0 = buffer.readFrame(frameSize)
        val f1 = buffer.readFrame(frameSize)
        val f2 = buffer.readFrame(frameSize)

        assertEquals(0, buffer.lostPacketsCount)
        assertEquals(0.toShort(), f0[0])
        assertEquals(100.toShort(), f1[0])
        assertEquals(200.toShort(), f2[0])
    }

    @Test
    fun testOutOfOrderPacketReordering() {
        val buffer = SimulatedJitterBuffer(targetLatencyMs = 30)
        val frameSize = 480

        // Push seq 0, seq 2, seq 1 (out of order)
        val p0 = MockPacket(0L, 0L, ShortArray(frameSize) { 10 })
        val p2 = MockPacket(2L, 20L, ShortArray(frameSize) { 30 })
        val p1 = MockPacket(1L, 10L, ShortArray(frameSize) { 20 })

        buffer.pushPacket(p0)
        buffer.pushPacket(p2)
        buffer.pushPacket(p1)

        val out0 = buffer.readFrame(frameSize)
        val out1 = buffer.readFrame(frameSize)
        val out2 = buffer.readFrame(frameSize)

        // Verifies correct reordered playback
        assertEquals(10.toShort(), out0[0])
        assertEquals(20.toShort(), out1[0])
        assertEquals(30.toShort(), out2[0])
        assertEquals(0, buffer.lostPacketsCount)
    }

    @Test
    fun testPacketLossConcealment() {
        val buffer = SimulatedJitterBuffer(targetLatencyMs = 20)
        val frameSize = 480

        // Push seq 0, skip seq 1, push seq 2
        val p0 = MockPacket(0L, 0L, ShortArray(frameSize) { 1000 })
        val p2 = MockPacket(2L, 20L, ShortArray(frameSize) { 2000 })

        buffer.pushPacket(p0)
        buffer.pushPacket(p2)

        val out0 = buffer.readFrame(frameSize)
        val out1 = buffer.readFrame(frameSize) // Expected seq 1 -> missing! PLC kicks in
        val out2 = buffer.readFrame(frameSize) // Expected seq 2 -> present!

        assertEquals(1000.toShort(), out0[0])
        assertEquals(1, buffer.lostPacketsCount)
        // PLC output: attenuated previous frame
        assertEquals(700.toShort(), out1[0])
        assertEquals(2000.toShort(), out2[0])
    }
}
