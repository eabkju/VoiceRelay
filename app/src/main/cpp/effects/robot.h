#ifndef VOICE_RELAY_ROBOT_H
#define VOICE_RELAY_ROBOT_H

#include <cmath>
#include "dsp_utils.h"

class RobotEffect {
public:
    RobotEffect() {
        init(48000);
    }

    void init(int sampleRate) {
        sampleRate_ = sampleRate;
        carrierPhase_ = 0.0f;
        carrierFreq_ = 50.0f; // 50 Hz Dalek / Cylon style ring mod
        mix_ = 0.9f;
    }

    void setCarrierFreq(float freqHz) {
        carrierFreq_ = dsp::clamp(freqHz, 20.0f, 500.0f);
    }

    void setMix(float mix) {
        mix_ = dsp::clamp(mix, 0.0f, 1.0f);
    }

    void reset() {
        carrierPhase_ = 0.0f;
    }

    void process(float* buffer, int numSamples) {
        const float phaseInc = (dsp::TWO_PI * carrierFreq_) / static_cast<float>(sampleRate_);

        for (int i = 0; i < numSamples; ++i) {
            float inSample = buffer[i];
            float carrier = std::sin(carrierPhase_);
            carrierPhase_ += phaseInc;
            if (carrierPhase_ >= dsp::TWO_PI) {
                carrierPhase_ -= dsp::TWO_PI;
            }

            // Ring modulation + dry mix
            float modulated = inSample * carrier * 1.4f;
            buffer[i] = dsp::softLimit((1.0f - mix_) * inSample + mix_ * modulated);
        }
    }

private:
    int sampleRate_ = 48000;
    float carrierPhase_ = 0.0f;
    float carrierFreq_ = 50.0f;
    float mix_ = 0.9f;
};

#endif // VOICE_RELAY_ROBOT_H
