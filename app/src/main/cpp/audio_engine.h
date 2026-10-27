#ifndef VOICE_RELAY_AUDIO_ENGINE_H
#define VOICE_RELAY_AUDIO_ENGINE_H

#include <oboe/Oboe.h>
#include <memory>
#include <functional>
#include <atomic>
#include <vector>
#include "effects/voice_effects_chain.h"
#include "jitter_buffer.h"
#include "packet_format.h"

enum class RelayRole : int {
    MIC = 1,
    SPEAKER = 2,
    LOCAL_AMP = 3
};

using AudioPacketCallback = std::function<void(const uint8_t* data, size_t size)>;

class AudioEngine : public oboe::AudioStreamDataCallback,
                    public oboe::AudioStreamErrorCallback {
public:
    AudioEngine();
    ~AudioEngine() override;

    bool start(RelayRole role, CodecType codec = CodecType::PCM16_48K);
    void stop();
    bool isRunning() const { return isRunning_.load(); }

    void setRole(RelayRole role);
    void setCodec(CodecType codec);
    void setEffectPreset(EffectPreset preset);
    void setEffectIntensity(float intensity);
    void setMasterGain(float gain);
    void setAutoTuneKey(int rootKey);
    void setAutoTuneScale(int scale);

    // Audio Packet transport
    void setPacketSendCallback(AudioPacketCallback callback);
    void receiveAudioPacket(const uint8_t* data, size_t size);

    // Stats
    float getPeakLevel() const { return effectsChain_.getPeakLevel(); }
    float getRmsLevel() const { return effectsChain_.getRmsLevel(); }
    float getEstimatedLatencyMs() const;

    // Oboe Callbacks
    oboe::DataCallbackResult onAudioReady(
        oboe::AudioStream* audioStream,
        void* audioData,
        int32_t numFrames) override;

    void onErrorBeforeClose(oboe::AudioStream* stream, oboe::Result error) override;
    void onErrorAfterClose(oboe::AudioStream* stream, oboe::Result error) override;

private:
    bool openCaptureStream();
    bool openPlaybackStream();
    void closeStreams();

    void processAndSendCaptureData(const float* inputSamples, int32_t numFrames);

    std::shared_ptr<oboe::AudioStream> captureStream_;
    std::shared_ptr<oboe::AudioStream> playbackStream_;

    std::atomic<bool> isRunning_{false};
    RelayRole role_ = RelayRole::MIC;
    CodecType codec_ = CodecType::PCM16_48K;

    VoiceEffectsChain effectsChain_;
    AdaptiveJitterBuffer jitterBuffer_;

    AudioPacketCallback packetSendCallback_ = nullptr;
    uint32_t txSeq_ = 0;

    // Preallocated buffers for audio processing (zero allocations in callback)
    static constexpr int MAX_BUFFER_FRAMES = 960;
    float floatBuffer_[MAX_BUFFER_FRAMES];
    float ampLoopbackBuffer_[MAX_BUFFER_FRAMES];
    int16_t pcm16Buffer_[MAX_BUFFER_FRAMES];
    uint8_t packetBuffer_[MAX_PACKET_SIZE];

    // Local amplifier circular loopback buffer
    static constexpr int AMP_RING_SIZE = 4096;
    float ampRing_[AMP_RING_SIZE];
    std::atomic<int> ampWritePos_{0};
    std::atomic<int> ampReadPos_{0};

    // Resampler scratch
    int16_t resampleBuffer_[MAX_BUFFER_FRAMES];
};

#endif // VOICE_RELAY_AUDIO_ENGINE_H
