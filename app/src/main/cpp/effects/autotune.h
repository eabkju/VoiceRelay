#ifndef VOICE_RELAY_AUTOTUNE_H
#define VOICE_RELAY_AUTOTUNE_H

#include "yin_pitch_detector.h"
#include "pitch_shifter.h"
#include <vector>

enum class MusicalScale {
    CHROMATIC = 0,
    MAJOR = 1,
    MINOR = 2,
    PENTATONIC = 3
};

class AutoTuneEffect {
public:
    AutoTuneEffect();
    ~AutoTuneEffect() = default;

    void init(int sampleRate);
    void setRootKey(int rootNote); // 0=C, 1=C#, 2=D, ..., 11=B
    void setScale(MusicalScale scale);
    void setRetuneSpeed(float speed); // 0.0f (subtle) to 1.0f (robotic hard tune)
    void reset();

    void process(float* buffer, int numSamples);

private:
    float snapToScale(float inputHz) const;
    bool isNoteInScale(int midiNote) const;

    int sampleRate_ = 48000;
    int rootKey_ = 0; // C
    MusicalScale scale_ = MusicalScale::CHROMATIC;
    float retuneSpeed_ = 0.8f; // fast default for noticeable effect

    YinPitchDetector pitchDetector_;
    PitchShifter pitchShifter_;

    std::vector<float> analysisBuffer_;
    int analysisIndex_ = 0;
    float currentPitchShift_ = 0.0f;
};

#endif // VOICE_RELAY_AUTOTUNE_H
