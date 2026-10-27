#include "jitter_buffer.h"
#include <cstring>
#include <algorithm>
#include <cmath>

AdaptiveJitterBuffer::AdaptiveJitterBuffer(int capacity) {
    capacity_ = capacity;
    slots_.resize(capacity_);
    std::memset(plcLastSamples_, 0, sizeof(plcLastSamples_));
    reset();
}

void AdaptiveJitterBuffer::init(int sampleRate, int targetLatencyMs) {
    sampleRate_ = sampleRate;
    setTargetLatencyMs(targetLatencyMs);
    reset();
}

void AdaptiveJitterBuffer::setTargetLatencyMs(int ms) {
    targetLatencyMs_ = std::clamp(ms, 30, 150);
}

void AdaptiveJitterBuffer::reset() {
    std::lock_guard<std::mutex> lock(mutex_);
    for (auto& slot : slots_) {
        slot.occupied = false;
    }
    nextExpectedSeq_ = 0;
    hasStartedPlayback_ = false;
    queuedPackets_ = 0;
    receivedPackets_ = 0;
    lostPackets_ = 0;
    plcLastNumSamples_ = 0;
    plcAttenuation_ = 1.0f;
    std::memset(plcLastSamples_, 0, sizeof(plcLastSamples_));
}

bool AdaptiveJitterBuffer::pushPacket(const AudioPacketHeader& header, const int16_t* pcmPayload) {
    std::lock_guard<std::mutex> lock(mutex_);

    if (header.numSamples > MAX_SAMPLES_PER_FRAME) {
        return false;
    }

    receivedPackets_++;

    if (!hasStartedPlayback_ && queuedPackets_ == 0) {
        nextExpectedSeq_ = header.seq;
    }

    // Sequence wrap-around aware check: is packet too old?
    int32_t diff = static_cast<int32_t>(header.seq - nextExpectedSeq_);
    if (diff < -15) {
        // Late packet arrived after PLC or already played
        return false;
    }

    // Find slot in circular buffer
    int slotIdx = header.seq % capacity_;
    JitterSlot& slot = slots_[slotIdx];

    slot.occupied = true;
    slot.seq = header.seq;
    slot.timestampMs = header.timestampMs;
    slot.sampleRate = header.sampleRate;
    slot.numSamples = header.numSamples;
    std::memcpy(slot.samples, pcmPayload, header.numSamples * sizeof(int16_t));

    queuedPackets_++;
    return true;
}

int AdaptiveJitterBuffer::readFrame(int16_t* outputBuffer, int maxSamples) {
    std::lock_guard<std::mutex> lock(mutex_);

    // Pre-buffering before playback starts (target latency in frames, e.g. 6 frames = 60ms)
    int targetFrames = std::max(2, targetLatencyMs_ / 10);
    if (!hasStartedPlayback_) {
        if (queuedPackets_ < static_cast<uint32_t>(targetFrames)) {
            // Buffer is filling up; output silence
            std::memset(outputBuffer, 0, maxSamples * sizeof(int16_t));
            return maxSamples;
        }
        hasStartedPlayback_ = true;
    }

    int slotIdx = nextExpectedSeq_ % capacity_;
    JitterSlot& slot = slots_[slotIdx];

    if (slot.occupied && slot.seq == nextExpectedSeq_) {
        // Expected packet is available!
        int count = std::min(static_cast<int>(slot.numSamples), maxSamples);
        std::memcpy(outputBuffer, slot.samples, count * sizeof(int16_t));

        // Save for PLC
        std::memcpy(plcLastSamples_, slot.samples, count * sizeof(int16_t));
        plcLastNumSamples_ = count;
        plcAttenuation_ = 1.0f;

        slot.occupied = false;
        if (queuedPackets_ > 0) queuedPackets_--;
        nextExpectedSeq_++;
        return count;
    }

    // Packet loss detected! Apply Packet Loss Concealment (PLC)
    lostPackets_++;
    nextExpectedSeq_++;

    applyPLC(outputBuffer, maxSamples);
    return maxSamples;
}

void AdaptiveJitterBuffer::applyPLC(int16_t* outputBuffer, int numSamples) {
    if (plcLastNumSamples_ > 0 && plcAttenuation_ > 0.05f) {
        // Repeat previous pitch cycle with smooth attenuation (-3 dB)
        for (int i = 0; i < numSamples; ++i) {
            int srcIdx = i % plcLastNumSamples_;
            float sample = static_cast<float>(plcLastSamples_[srcIdx]) * plcAttenuation_;
            outputBuffer[i] = static_cast<int16_t>(std::clamp(sample, -32768.0f, 32767.0f));
        }
        plcAttenuation_ *= 0.65f; // Exponential decay per concealed packet
    } else {
        // Silence fallback
        std::memset(outputBuffer, 0, numSamples * sizeof(int16_t));
    }
}
