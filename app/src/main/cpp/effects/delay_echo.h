#ifndef VOICE_RELAY_DELAY_ECHO_H
#define VOICE_RELAY_DELAY_ECHO_H

#include <vector>
#include <algorithm>
#include "dsp_utils.h"

class DelayEchoEffect {
public:
    DelayEchoEffect() {
        init(48000);
    }

    void init(int sampleRate) {
        sampleRate_ = sampleRate;
        maxDelaySamples_ = sampleRate; // 1.0 second max
        buffer_.assign(maxDelaySamples_, 0.0f);
        writeIndex_ = 0;
        setDelayTimeMs(250.0f); // 250ms default echo
    }

    void setDelayTimeMs(float ms) {
        delaySamples_ = static_cast<int>((ms / 1000.0f) * static_cast<float>(sampleRate_));
        if (delaySamples_ < 1) delaySamples_ = 1;
        if (delaySamples_ >= maxDelaySamples_) delaySamples_ = maxDelaySamples_ - 1;
    }

    void setFeedback(float fb) {
        feedback_ = dsp::clamp(fb, 0.0f, 0.85f);
    }

    void setMix(float mix) {
        mix_ = dsp::clamp(mix, 0.0f, 1.0f);
    }

    void reset() {
        std::fill(buffer_.begin(), buffer_.end(), 0.0f);
        writeIndex_ = 0;
    }

    void process(float* buffer, int numSamples) {
        const int bufSize = static_cast<int>(buffer_.size());

        for (int i = 0; i < numSamples; ++i) {
            float inSample = buffer[i];

            int readIndex = (writeIndex_ - delaySamples_ + bufSize) % bufSize;
            float delayed = buffer_[readIndex];

            buffer_[writeIndex_] = inSample + delayed * feedback_;
            writeIndex_ = (writeIndex_ + 1) % bufSize;

            buffer[i] = dsp::softLimit((1.0f - 0.5f * mix_) * inSample + delayed * mix_);
        }
    }

private:
    int sampleRate_ = 48000;
    int maxDelaySamples_ = 48000;
    int delaySamples_ = 12000;
    std::vector<float> buffer_;
    int writeIndex_ = 0;
    float feedback_ = 0.45f;
    float mix_ = 0.5f;
};

#endif // VOICE_RELAY_DELAY_ECHO_H
