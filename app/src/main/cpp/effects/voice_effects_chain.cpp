#include "voice_effects_chain.h"
#include <cmath>
#include <algorithm>

VoiceEffectsChain::VoiceEffectsChain() {
    init(48000);
}

void VoiceEffectsChain::init(int sampleRate) {
    sampleRate_ = sampleRate;
    crossfadeTotal_ = static_cast<int>(0.010f * static_cast<float>(sampleRate)); // 10 ms
    crossfadeBuffer_.assign(crossfadeTotal_, 0.0f);
    crossfadeRemaining_ = 0;

    pitchShifter_.init(sampleRate, 1024);
    autoTune_.init(sampleRate);
    flanger_.init(sampleRate);
    robot_.init(sampleRate);
    echo_.init(sampleRate);
    reverb_.init(sampleRate);

    applyPresetSettings(currentPreset_, intensity_);
}

void VoiceEffectsChain::setPreset(EffectPreset preset) {
    if (preset == currentPreset_) return;
    pendingPreset_ = preset;
    // Trigger smooth crossfade to eliminate clicks
    crossfadeRemaining_ = crossfadeTotal_;
}

void VoiceEffectsChain::setIntensity(float intensity) {
    intensity_ = dsp::clamp(intensity, 0.0f, 1.0f);
    applyPresetSettings(currentPreset_, intensity_);
}

void VoiceEffectsChain::setMasterGain(float gain) {
    masterGain_ = dsp::clamp(gain, 0.0f, 2.5f);
}

void VoiceEffectsChain::setAutoTuneKey(int rootKey) {
    autoTune_.setRootKey(rootKey);
}

void VoiceEffectsChain::setAutoTuneScale(int scale) {
    autoTune_.setScale(static_cast<MusicalScale>(scale));
}

void VoiceEffectsChain::applyPresetSettings(EffectPreset preset, float intensity) {
    switch (preset) {
        case EffectPreset::NONE:
            pitchShifter_.setPitchShiftSemitones(0.0f);
            pitchShifter_.setFormantShift(0.0f);
            break;
        case EffectPreset::CHIPMUNK:
            // Chipmunk: +7 semitones with strong formant up
            pitchShifter_.setPitchShiftSemitones(7.0f * intensity);
            pitchShifter_.setFormantShift(0.7f * intensity);
            break;
        case EffectPreset::CHILD:
            // Child: +4 semitones with light formant up
            pitchShifter_.setPitchShiftSemitones(4.0f * intensity);
            pitchShifter_.setFormantShift(0.4f * intensity);
            break;
        case EffectPreset::TALKING_TOM:
            // Talking Tom: +9 semitones with cartoon vocal formant
            pitchShifter_.setPitchShiftSemitones(9.0f * intensity);
            pitchShifter_.setFormantShift(0.5f * intensity);
            break;
        case EffectPreset::MONSTER:
            // Deep voice / Monster: -6 semitones with formant down
            pitchShifter_.setPitchShiftSemitones(-6.0f * intensity);
            pitchShifter_.setFormantShift(-0.6f * intensity);
            break;
        case EffectPreset::FLANGER:
            flanger_.setDepth(0.85f * intensity);
            flanger_.setFeedback(0.65f * intensity);
            flanger_.setRateHz(0.4f + 2.5f * intensity);
            break;
        case EffectPreset::AUTOTUNE:
            autoTune_.setRetuneSpeed(0.2f + 0.8f * intensity);
            break;
        case EffectPreset::ROBOT:
            robot_.setMix(0.85f * intensity);
            robot_.setCarrierFreq(45.0f + 60.0f * (1.0f - intensity));
            break;
        case EffectPreset::ECHO:
            echo_.setDelayTimeMs(150.0f + 250.0f * intensity);
            echo_.setFeedback(0.3f + 0.45f * intensity);
            echo_.setMix(0.3f + 0.4f * intensity);
            break;
        case EffectPreset::REVERB:
            reverb_.setRoomSize(0.6f + 0.35f * intensity);
            reverb_.setMix(0.25f + 0.5f * intensity);
            break;
    }
}

void VoiceEffectsChain::reset() {
    pitchShifter_.reset();
    autoTune_.reset();
    flanger_.reset();
    robot_.reset();
    echo_.reset();
    reverb_.reset();
    crossfadeRemaining_ = 0;
}

void VoiceEffectsChain::process(float* buffer, int numSamples) {
    if (numSamples <= 0) return;

    // Check if preset transition is requested with crossfade
    if (crossfadeRemaining_ > 0 && pendingPreset_ != currentPreset_) {
        // Copy dry buffer for pending preset crossfade
        int xfadeSamples = std::min(numSamples, crossfadeRemaining_);
        for (int i = 0; i < xfadeSamples; ++i) {
            crossfadeBuffer_[i] = buffer[i];
        }

        // Process current effect first
        applyPresetSettings(currentPreset_, intensity_);
        switch (currentPreset_) {
            case EffectPreset::CHIPMUNK:
            case EffectPreset::CHILD:
            case EffectPreset::TALKING_TOM:
            case EffectPreset::MONSTER:
                pitchShifter_.process(buffer, numSamples);
                break;
            case EffectPreset::FLANGER:
                flanger_.process(buffer, numSamples);
                break;
            case EffectPreset::AUTOTUNE:
                autoTune_.process(buffer, numSamples);
                break;
            case EffectPreset::ROBOT:
                robot_.process(buffer, numSamples);
                break;
            case EffectPreset::ECHO:
                echo_.process(buffer, numSamples);
                break;
            case EffectPreset::REVERB:
                reverb_.process(buffer, numSamples);
                break;
            default:
                break;
        }

        // Switch active preset to pending
        currentPreset_ = pendingPreset_;
        applyPresetSettings(currentPreset_, intensity_);

        // Process copied buffer with new preset
        switch (currentPreset_) {
            case EffectPreset::CHIPMUNK:
            case EffectPreset::CHILD:
            case EffectPreset::TALKING_TOM:
            case EffectPreset::MONSTER:
                pitchShifter_.process(crossfadeBuffer_.data(), xfadeSamples);
                break;
            case EffectPreset::FLANGER:
                flanger_.process(crossfadeBuffer_.data(), xfadeSamples);
                break;
            case EffectPreset::AUTOTUNE:
                autoTune_.process(crossfadeBuffer_.data(), xfadeSamples);
                break;
            case EffectPreset::ROBOT:
                robot_.process(crossfadeBuffer_.data(), xfadeSamples);
                break;
            case EffectPreset::ECHO:
                echo_.process(crossfadeBuffer_.data(), xfadeSamples);
                break;
            case EffectPreset::REVERB:
                reverb_.process(crossfadeBuffer_.data(), xfadeSamples);
                break;
            default:
                break;
        }

        // Crossfade interpolation to prevent any audible glitch
        for (int i = 0; i < xfadeSamples; ++i) {
            float fadeProgress = static_cast<float>(crossfadeTotal_ - crossfadeRemaining_ + i) /
                                 static_cast<float>(crossfadeTotal_);
            fadeProgress = dsp::clamp(fadeProgress, 0.0f, 1.0f);
            buffer[i] = (1.0f - fadeProgress) * buffer[i] + fadeProgress * crossfadeBuffer_[i];
        }

        crossfadeRemaining_ -= xfadeSamples;
        if (crossfadeRemaining_ <= 0) {
            crossfadeRemaining_ = 0;
        }
    } else {
        // Normal processing of active effect
        switch (currentPreset_) {
            case EffectPreset::NONE:
                break;
            case EffectPreset::CHIPMUNK:
            case EffectPreset::CHILD:
            case EffectPreset::TALKING_TOM:
            case EffectPreset::MONSTER:
                pitchShifter_.process(buffer, numSamples);
                break;
            case EffectPreset::FLANGER:
                flanger_.process(buffer, numSamples);
                break;
            case EffectPreset::AUTOTUNE:
                autoTune_.process(buffer, numSamples);
                break;
            case EffectPreset::ROBOT:
                robot_.process(buffer, numSamples);
                break;
            case EffectPreset::ECHO:
                echo_.process(buffer, numSamples);
                break;
            case EffectPreset::REVERB:
                reverb_.process(buffer, numSamples);
                break;
        }
    }

    // Apply master gain, compute live RMS/Peak level meters, and apply soft limiter
    float peak = 0.0f;
    float sumSquares = 0.0f;

    for (int i = 0; i < numSamples; ++i) {
        float sample = buffer[i] * masterGain_;
        sample = dsp::softLimit(sample);
        buffer[i] = sample;

        float absVal = std::abs(sample);
        if (absVal > peak) peak = absVal;
        sumSquares += sample * sample;
    }

    float rms = std::sqrt(sumSquares / static_cast<float>(numSamples));
    peakLevel_.store(peak, std::memory_order_relaxed);
    rmsLevel_.store(rms, std::memory_order_relaxed);
}
