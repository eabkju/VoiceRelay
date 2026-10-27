#ifndef VOICE_RELAY_FLANGER_H
#define VOICE_RELAY_FLANGER_H

#include <vector>
#include <cmath>
#include "dsp_utils.h"

class FlangerEffect {
public:
    FlangerEffect() {
        init(48000);
    }

    void init(int sampleRate) {
        sampleRate_ = sampleRate;
        maxDelaySamples_ = static_cast<int>(0.015f * static_cast<float>(sampleRate)); // 15 ms buffer
        buffer_.assign(maxDelaySamples_ * 2, 0.0f);
        writeIndex_ = 0;
        lfoPhase_ = 0.0f;
    }

    void setRateHz(float rateHz) {
        rateHz_ = dsp::clamp(rateHz, 0.1f, 10.0f);
    }

    void setDepth(float depth) {
        depth_ = dsp::clamp(depth, 0.0f, 1.0f);
    }

    void setFeedback(float feedback) {
        feedback_ = dsp::clamp(feedback, 0.0f, 0.85f);
    }

    void reset() {
        std::fill(buffer_.begin(), buffer_.end(), 0.0f);
        writeIndex_ = 0;
        lfoPhase_ = 0.0f;
    }

    void process(float* buffer, int numSamples) {
        const float lfoIncrement = (dsp::TWO_PI * rateHz_) / static_cast<float>(sampleRate_);
        const float baseDelay = 0.003f * static_cast<float>(sampleRate_); // 3ms center
        const float maxMod = 0.0025f * static_cast<float>(sampleRate_) * depth_; // +/- 2.5ms swing
        const int bufSize = static_cast<int>(buffer_.size());

        for (int i = 0; i < numSamples; ++i) {
            float inSample = buffer[i];

            // Calculate current modulated delay in samples
            float mod = std::sin(lfoPhase_);
            lfoPhase_ += lfoIncrement;
            if (lfoPhase_ >= dsp::TWO_PI) {
                lfoPhase_ -= dsp::TWO_PI;
            }

            float currentDelay = baseDelay + mod * maxMod;
            if (currentDelay < 1.0f) currentDelay = 1.0f;

            // Interpolated read from delay line
            float readPos = static_cast<float>(writeIndex_) - currentDelay;
            while (readPos < 0.0f) readPos += static_cast<float>(bufSize);

            int iRead0 = static_cast<int>(readPos) % bufSize;
            int iRead1 = (iRead0 + 1) % bufSize;
            float frac = readPos - std::floor(readPos);

            float delayedSample = dsp::lerp(buffer_[iRead0], buffer_[iRead1], frac);

            // Write back to circular buffer with feedback
            buffer_[writeIndex_] = inSample + delayedSample * feedback_;
            writeIndex_ = (writeIndex_ + 1) % bufSize;

            // Mix dry and wet
            buffer[i] = dsp::softLimit(0.7f * inSample + 0.7f * delayedSample);
        }
    }

private:
    int sampleRate_ = 48000;
    int maxDelaySamples_ = 960;
    std::vector<float> buffer_;
    int writeIndex_ = 0;
    float lfoPhase_ = 0.0f;
    float rateHz_ = 0.5f; // 0.5 Hz default
    float depth_ = 0.8f;
    float feedback_ = 0.6f;
};

#endif // VOICE_RELAY_FLANGER_H
