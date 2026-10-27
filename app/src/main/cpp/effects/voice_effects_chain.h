#ifndef VOICE_RELAY_VOICE_EFFECTS_CHAIN_H
#define VOICE_RELAY_VOICE_EFFECTS_CHAIN_H

#include "pitch_shifter.h"
#include "autotune.h"
#include "flanger.h"
#include "robot.h"
#include "delay_echo.h"
#include "reverb.h"
#include <vector>
#include <atomic>

enum class EffectPreset : int {
    NONE = 0,
    CHIPMUNK = 1,
    CHILD = 2,
    TALKING_TOM = 3,
    MONSTER = 4,
    FLANGER = 5,
    AUTOTUNE = 6,
    ROBOT = 7,
    ECHO = 8,
    REVERB = 9
};

class VoiceEffectsChain {
public:
    VoiceEffectsChain();
    ~VoiceEffectsChain() = default;

    void init(int sampleRate);
    void setPreset(EffectPreset preset);
    void setIntensity(float intensity); // 0.0 to 1.0
    void setMasterGain(float gain);     // 0.0 to 2.0 (1.0 = normal)
    void setAutoTuneKey(int rootKey);   // 0=C, 1=C#, ..., 11=B
    void setAutoTuneScale(int scale);   // 0=Chromatic, 1=Major, 2=Minor, 3=Pentatonic
    void reset();

    // Process mono buffer in-place
    void process(float* buffer, int numSamples);

    // Live level meters
    float getPeakLevel() const { return peakLevel_.load(std::memory_order_relaxed); }
    float getRmsLevel() const { return rmsLevel_.load(std::memory_order_relaxed); }

private:
    void applyPresetSettings(EffectPreset preset, float intensity);

    int sampleRate_ = 48000;
    EffectPreset currentPreset_ = EffectPreset::NONE;
    EffectPreset pendingPreset_ = EffectPreset::NONE;
    float intensity_ = 0.8f;
    float masterGain_ = 1.0f;

    // Sub-effect modules
    PitchShifter pitchShifter_;
    AutoTuneEffect autoTune_;
    FlangerEffect flanger_;
    RobotEffect robot_;
    DelayEchoEffect echo_;
    ReverbEffect reverb_;

    // Crossfading when switching effects
    std::vector<float> crossfadeBuffer_;
    int crossfadeRemaining_ = 0;
    int crossfadeTotal_ = 480; // 10ms at 48kHz

    std::atomic<float> peakLevel_{0.0f};
    std::atomic<float> rmsLevel_{0.0f};
};

#endif // VOICE_RELAY_VOICE_EFFECTS_CHAIN_H
