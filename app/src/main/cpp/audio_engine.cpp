#include "audio_engine.h"
#include <android/log.h>
#include <chrono>
#include <cstring>
#include <algorithm>

#define TAG "VoiceRelayAudioEngine"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

static uint64_t getCurrentTimeMs() {
    auto now = std::chrono::steady_clock::now();
    return std::chrono::duration_cast<std::chrono::milliseconds>(now.time_since_epoch()).count();
}

AudioEngine::AudioEngine() {
    std::fill(ampRing_, ampRing_ + AMP_RING_SIZE, 0.0f);
    effectsChain_.init(48000);
    jitterBuffer_.init(48000, 60);
}

AudioEngine::~AudioEngine() {
    stop();
}

bool AudioEngine::start(RelayRole role, CodecType codec) {
    if (isRunning_.load()) {
        stop();
    }

    role_ = role;
    codec_ = codec;
    txSeq_ = 0;
    effectsChain_.reset();
    jitterBuffer_.reset();

    // Pre-fill local amplifier buffer with 960 frames (~20ms) of silence so playback has a cushion
    std::fill(ampRing_, ampRing_ + AMP_RING_SIZE, 0.0f);
    if (role_ == RelayRole::LOCAL_AMP) {
        ampWritePos_.store(960);
        ampReadPos_.store(0);
    } else {
        ampWritePos_.store(0);
        ampReadPos_.store(0);
    }

    bool success = true;

    if (role_ == RelayRole::MIC || role_ == RelayRole::LOCAL_AMP) {
        success &= openCaptureStream();
    }

    if (role_ == RelayRole::SPEAKER || role_ == RelayRole::LOCAL_AMP) {
        success &= openPlaybackStream();
    }

    if (!success) {
        LOGE("Failed to open required audio streams for role: %d", static_cast<int>(role_));
        closeStreams();
        return false;
    }

    isRunning_.store(true);
    LOGI("AudioEngine started successfully for role %d", static_cast<int>(role_));
    return true;
}

void AudioEngine::stop() {
    if (!isRunning_.exchange(false)) {
        return;
    }
    closeStreams();
    LOGI("AudioEngine stopped");
}

void AudioEngine::setRole(RelayRole role) {
    if (role_ != role) {
        if (isRunning_.load()) {
            start(role, codec_);
        } else {
            role_ = role;
        }
    }
}

void AudioEngine::setCodec(CodecType codec) {
    codec_ = codec;
}

void AudioEngine::setEffectPreset(EffectPreset preset) {
    effectsChain_.setPreset(preset);
}

void AudioEngine::setEffectIntensity(float intensity) {
    effectsChain_.setIntensity(intensity);
}

void AudioEngine::setMasterGain(float gain) {
    effectsChain_.setMasterGain(gain);
}

void AudioEngine::setAutoTuneKey(int rootKey) {
    effectsChain_.setAutoTuneKey(rootKey);
}

void AudioEngine::setAutoTuneScale(int scale) {
    effectsChain_.setAutoTuneScale(scale);
}

void AudioEngine::setPacketSendCallback(AudioPacketCallback callback) {
    packetSendCallback_ = callback;
}

void AudioEngine::receiveAudioPacket(const uint8_t* data, size_t size) {
    if (size < HEADER_SIZE) return;

    AudioPacketHeader header;
    std::memcpy(&header, data, HEADER_SIZE);

    if (header.magic != PACKET_MAGIC) {
        LOGE("Invalid packet magic: 0x%08X", header.magic);
        return;
    }

    const int16_t* pcmPayload = reinterpret_cast<const int16_t*>(data + HEADER_SIZE);
    jitterBuffer_.pushPacket(header, pcmPayload);
}

float AudioEngine::getEstimatedLatencyMs() const {
    if (role_ == RelayRole::LOCAL_AMP) {
        return 12.0f; // Ultra-low local round-trip hardware latency
    }
    return jitterBuffer_.getTargetLatencyMs() + jitterBuffer_.getEstimatedJitterMs();
}

bool AudioEngine::openCaptureStream() {
    oboe::AudioStreamBuilder builder;
    builder.setDirection(oboe::Direction::Input)
        ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
        ->setSharingMode(oboe::SharingMode::Shared)
        ->setFormat(oboe::AudioFormat::I16)
        ->setChannelCount(oboe::ChannelCount::Mono)
        ->setSampleRate(48000)
        ->setInputPreset(oboe::InputPreset::Generic)
        ->setDataCallback(this)
        ->setErrorCallback(this);

    oboe::Result result = builder.openStream(captureStream_);
    if (result != oboe::Result::OK) {
        LOGE("Failed to open capture stream with LowLatency: %s. Retrying with PerformanceMode::None...", oboe::convertToText(result));
        builder.setPerformanceMode(oboe::PerformanceMode::None);
        result = builder.openStream(captureStream_);
    }

    if (result != oboe::Result::OK) {
        LOGE("Failed to open capture stream: %s", oboe::convertToText(result));
        return false;
    }

    result = captureStream_->requestStart();
    if (result != oboe::Result::OK) {
        LOGE("Failed to start capture stream: %s", oboe::convertToText(result));
        captureStream_->close();
        captureStream_.reset();
        return false;
    }

    LOGI("Capture stream started successfully. Sample rate: %d", captureStream_->getSampleRate());
    return true;
}

bool AudioEngine::openPlaybackStream() {
    oboe::AudioStreamBuilder builder;
    builder.setDirection(oboe::Direction::Output)
        ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
        ->setSharingMode(oboe::SharingMode::Shared)
        ->setFormat(oboe::AudioFormat::I16)
        ->setChannelCount(oboe::ChannelCount::Mono)
        ->setSampleRate(48000)
        ->setUsage(oboe::Usage::Media)
        ->setContentType(oboe::ContentType::Music)
        ->setDataCallback(this)
        ->setErrorCallback(this);

    oboe::Result result = builder.openStream(playbackStream_);
    if (result != oboe::Result::OK) {
        LOGE("Failed to open playback stream with LowLatency: %s. Retrying with PerformanceMode::None...", oboe::convertToText(result));
        builder.setPerformanceMode(oboe::PerformanceMode::None);
        result = builder.openStream(playbackStream_);
    }

    if (result != oboe::Result::OK) {
        LOGE("Failed to open playback stream: %s", oboe::convertToText(result));
        return false;
    }

    result = playbackStream_->requestStart();
    if (result != oboe::Result::OK) {
        LOGE("Failed to start playback stream: %s", oboe::convertToText(result));
        playbackStream_->close();
        playbackStream_.reset();
        return false;
    }

    LOGI("Playback stream started successfully. Sample rate: %d", playbackStream_->getSampleRate());
    return true;
}

void AudioEngine::closeStreams() {
    if (captureStream_) {
        captureStream_->stop();
        captureStream_->close();
        captureStream_.reset();
    }
    if (playbackStream_) {
        playbackStream_->stop();
        playbackStream_->close();
        playbackStream_.reset();
    }
}

oboe::DataCallbackResult AudioEngine::onAudioReady(
    oboe::AudioStream* audioStream,
    void* audioData,
    int32_t numFrames) {

    if (!isRunning_.load(std::memory_order_relaxed)) {
        return oboe::DataCallbackResult::Stop;
    }

    if (numFrames > MAX_BUFFER_FRAMES) {
        numFrames = MAX_BUFFER_FRAMES;
    }

    if (audioStream->getDirection() == oboe::Direction::Input) {
        // --- MIC / CAPTURE CALLBACK ---
        const int16_t* inputSamples = static_cast<const int16_t*>(audioData);

        // Convert 16-bit PCM to float [-1.0f, 1.0f]
        for (int i = 0; i < numFrames; ++i) {
            floatBuffer_[i] = static_cast<float>(inputSamples[i]) / 32768.0f;
        }

        // Apply Voice Effect chain
        effectsChain_.process(floatBuffer_, numFrames);

        if (role_ == RelayRole::LOCAL_AMP) {
            // Write directly into amp ring buffer for local playback
            int wPos = ampWritePos_.load(std::memory_order_relaxed);
            for (int i = 0; i < numFrames; ++i) {
                ampRing_[(wPos + i) % AMP_RING_SIZE] = floatBuffer_[i];
            }
            ampWritePos_.store((wPos + numFrames) % AMP_RING_SIZE, std::memory_order_release);
        } else if (role_ == RelayRole::MIC) {
            // Convert to 16-bit PCM and broadcast over network
            processAndSendCaptureData(floatBuffer_, numFrames);
        }
    } else {
        // --- SPEAKER / PLAYBACK CALLBACK ---
        int16_t* outputSamples = static_cast<int16_t*>(audioData);

        if (role_ == RelayRole::LOCAL_AMP) {
            int rPos = ampReadPos_.load(std::memory_order_relaxed);
            int wPos = ampWritePos_.load(std::memory_order_acquire);
            int available = (wPos - rPos + AMP_RING_SIZE) % AMP_RING_SIZE;

            int framesToRead = std::min(numFrames, available);
            for (int i = 0; i < framesToRead; ++i) {
                float s = ampRing_[(rPos + i) % AMP_RING_SIZE];
                outputSamples[i] = static_cast<int16_t>(std::clamp(s * 32767.0f, -32768.0f, 32767.0f));
            }
            ampReadPos_.store((rPos + framesToRead) % AMP_RING_SIZE, std::memory_order_relaxed);

            // If buffer underran, fill remainder with silence
            if (framesToRead < numFrames) {
                std::memset(outputSamples + framesToRead, 0, (numFrames - framesToRead) * sizeof(int16_t));
            }
        } else {
            // Read from Adaptive Jitter Buffer (with PLC if packet dropped)
            jitterBuffer_.readFrame(outputSamples, numFrames);
        }
    }

    return oboe::DataCallbackResult::Continue;
}

void AudioEngine::processAndSendCaptureData(const float* inputSamples, int32_t numFrames) {
    if (!packetSendCallback_) return;

    // Convert float samples to 16-bit PCM
    for (int i = 0; i < numFrames; ++i) {
        float s = inputSamples[i];
        pcm16Buffer_[i] = static_cast<int16_t>(std::clamp(s * 32767.0f, -32768.0f, 32767.0f));
    }

    uint16_t sendSampleRate = 48000;
    uint16_t sendNumSamples = numFrames;
    const int16_t* payloadData = pcm16Buffer_;

    // Handle optional resampling for bandwidth optimization
    if (codec_ == CodecType::PCM16_24K) {
        // Downsample 2x (48k -> 24k)
        sendSampleRate = 24000;
        sendNumSamples = numFrames / 2;
        for (int i = 0; i < sendNumSamples; ++i) {
            resampleBuffer_[i] = pcm16Buffer_[i * 2];
        }
        payloadData = resampleBuffer_;
    } else if (codec_ == CodecType::PCM16_16K) {
        // Downsample 3x (48k -> 16k)
        sendSampleRate = 16000;
        sendNumSamples = numFrames / 3;
        for (int i = 0; i < sendNumSamples; ++i) {
            resampleBuffer_[i] = pcm16Buffer_[i * 3];
        }
        payloadData = resampleBuffer_;
    }

    AudioPacketHeader header{};
    header.magic = PACKET_MAGIC;
    header.seq = txSeq_++;
    header.timestampMs = getCurrentTimeMs();
    header.sampleRate = sendSampleRate;
    header.numSamples = sendNumSamples;
    header.codec = static_cast<uint8_t>(codec_);
    header.flags = 0;
    header.checksum = 0;

    // Assemble packet
    std::memcpy(packetBuffer_, &header, HEADER_SIZE);
    std::memcpy(packetBuffer_ + HEADER_SIZE, payloadData, sendNumSamples * sizeof(int16_t));
    size_t packetSize = HEADER_SIZE + sendNumSamples * sizeof(int16_t);

    packetSendCallback_(packetBuffer_, packetSize);
}

void AudioEngine::onErrorBeforeClose(oboe::AudioStream* stream, oboe::Result error) {
    LOGE("AudioEngine stream error before close: %s", oboe::convertToText(error));
}

void AudioEngine::onErrorAfterClose(oboe::AudioStream* stream, oboe::Result error) {
    LOGE("AudioEngine stream error after close: %s. Attempting restart...", oboe::convertToText(error));
    if (isRunning_.load()) {
        start(role_, codec_);
    }
}
