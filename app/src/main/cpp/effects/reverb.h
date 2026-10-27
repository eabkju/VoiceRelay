#ifndef VOICE_RELAY_REVERB_H
#define VOICE_RELAY_REVERB_H

#include <vector>
#include <algorithm>
#include "dsp_utils.h"

class ReverbEffect {
private:
    struct CombFilter {
        std::vector<float> buffer;
        int bufSize = 0;
        int idx = 0;
        float feedback = 0.75f;
        float filterStore = 0.0f;
        float damp = 0.2f;

        void init(int size, float fb, float d) {
            bufSize = size;
            buffer.assign(bufSize, 0.0f);
            idx = 0;
            feedback = fb;
            damp = d;
            filterStore = 0.0f;
        }

        inline float process(float in) {
            float out = buffer[idx];
            filterStore = (out * (1.0f - damp)) + (filterStore * damp);
            buffer[idx] = in + (filterStore * feedback);
            idx = (idx + 1) % bufSize;
            return out;
        }

        void reset() {
            std::fill(buffer.begin(), buffer.end(), 0.0f);
            idx = 0;
            filterStore = 0.0f;
        }
    };

    struct AllPassFilter {
        std::vector<float> buffer;
        int bufSize = 0;
        int idx = 0;
        float feedback = 0.5f;

        void init(int size, float fb) {
            bufSize = size;
            buffer.assign(bufSize, 0.0f);
            idx = 0;
            feedback = fb;
        }

        inline float process(float in) {
            float bufOut = buffer[idx];
            float out = -in + bufOut;
            buffer[idx] = in + (bufOut * feedback);
            idx = (idx + 1) % bufSize;
            return out;
        }

        void reset() {
            std::fill(buffer.begin(), buffer.end(), 0.0f);
            idx = 0;
        }
    };

public:
    ReverbEffect() {
        init(48000);
    }

    void init(int sampleRate) {
        sampleRate_ = sampleRate;
        float scale = static_cast<float>(sampleRate) / 44100.0f;

        // Tuned prime delay lengths
        combs_[0].init(static_cast<int>(1116 * scale), 0.8f, 0.2f);
        combs_[1].init(static_cast<int>(1188 * scale), 0.8f, 0.2f);
        combs_[2].init(static_cast<int>(1277 * scale), 0.8f, 0.2f);
        combs_[3].init(static_cast<int>(1356 * scale), 0.8f, 0.2f);

        allpasses_[0].init(static_cast<int>(556 * scale), 0.5f);
        allpasses_[1].init(static_cast<int>(441 * scale), 0.5f);
    }

    void setRoomSize(float size) {
        roomSize_ = dsp::clamp(size, 0.1f, 0.95f);
        for (auto& c : combs_) {
            c.feedback = roomSize_;
        }
    }

    void setMix(float mix) {
        mix_ = dsp::clamp(mix, 0.0f, 1.0f);
    }

    void reset() {
        for (auto& c : combs_) c.reset();
        for (auto& a : allpasses_) a.reset();
    }

    void process(float* buffer, int numSamples) {
        for (int i = 0; i < numSamples; ++i) {
            float inSample = buffer[i];

            // Parallel combs
            float combSum = 0.0f;
            for (auto& c : combs_) {
                combSum += c.process(inSample);
            }
            combSum *= 0.25f;

            // Series allpasses
            float ap = allpasses_[0].process(combSum);
            float wet = allpasses_[1].process(ap);

            buffer[i] = dsp::softLimit((1.0f - 0.4f * mix_) * inSample + wet * (0.6f * mix_));
        }
    }

private:
    int sampleRate_ = 48000;
    CombFilter combs_[4];
    AllPassFilter allpasses_[2];
    float roomSize_ = 0.78f;
    float mix_ = 0.4f;
};

#endif // VOICE_RELAY_REVERB_H
