#ifndef VOICE_RELAY_PITCH_SHIFTER_H
#define VOICE_RELAY_PITCH_SHIFTER_H

#include <vector>
#include <cstddef>
#include "dsp_utils.h"

class PitchShifter {
public:
    PitchShifter();
    ~PitchShifter() = default;

    void init(int sampleRate, int maxGrainSize = 2048);
    void setPitchShiftSemitones(float semitones);
    void setFormantShift(float formantShift); // -1.0 to +1.0
    void reset();

    // Process mono buffer in-place. No allocations!
    void process(float* buffer, int numSamples);

private:
    int sampleRate_ = 48000;
    int grainSize_ = 1024;
    float pitchRatio_ = 1.0f;
    float formant_ = 0.0f;

    std::vector<float> ringBuffer_;
    int ringSize_ = 4096;
    int writeIndex_ = 0;

    float readPos1_ = 0.0f;
    float readPos2_ = 0.0f;
    std::vector<float> window_;

    // Formant filter states (biquad shelving / peak filters)
    float fState1_ = 0.0f;
    float fState2_ = 0.0f;
};

#endif // VOICE_RELAY_PITCH_SHIFTER_H
