#ifndef VOICE_RELAY_JITTER_BUFFER_H
#define VOICE_RELAY_JITTER_BUFFER_H

#include <cstdint>
#include <vector>
#include <mutex>
#include "packet_format.h"

struct JitterSlot {
    bool occupied = false;
    uint32_t seq = 0;
    uint64_t timestampMs = 0;
    uint16_t sampleRate = 48000;
    uint16_t numSamples = 0;
    int16_t samples[MAX_SAMPLES_PER_FRAME];
};

class AdaptiveJitterBuffer {
public:
    explicit AdaptiveJitterBuffer(int capacity = 32);
    ~AdaptiveJitterBuffer() = default;

    void init(int sampleRate, int targetLatencyMs = 60);
    void reset();

    // Insert packet into jitter buffer
    bool pushPacket(const AudioPacketHeader& header, const int16_t* pcmPayload);

    // Read audio frame for output playback. Returns number of samples read (e.g. 480).
    int readFrame(int16_t* outputBuffer, int maxSamples);

    // Latency & stats
    int getTargetLatencyMs() const { return targetLatencyMs_; }
    void setTargetLatencyMs(int ms);
    float getEstimatedJitterMs() const { return estimatedJitterMs_; }
    uint32_t getLostPacketsCount() const { return lostPackets_; }
    uint32_t getReceivedPacketsCount() const { return receivedPackets_; }

private:
    void applyPLC(int16_t* outputBuffer, int numSamples);

    int sampleRate_ = 48000;
    int targetLatencyMs_ = 60;
    float estimatedJitterMs_ = 10.0f;

    std::vector<JitterSlot> slots_;
    int capacity_ = 32;

    uint32_t nextExpectedSeq_ = 0;
    bool hasStartedPlayback_ = false;
    uint32_t queuedPackets_ = 0;

    // Statistics
    uint32_t receivedPackets_ = 0;
    uint32_t lostPackets_ = 0;

    // PLC state
    int16_t plcLastSamples_[MAX_SAMPLES_PER_FRAME];
    int plcLastNumSamples_ = 0;
    float plcAttenuation_ = 1.0f;

    std::mutex mutex_;
};

#endif // VOICE_RELAY_JITTER_BUFFER_H
