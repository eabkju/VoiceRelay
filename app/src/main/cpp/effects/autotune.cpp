#include "autotune.h"
#include <cmath>
#include <algorithm>

AutoTuneEffect::AutoTuneEffect() {
    init(48000);
}

void AutoTuneEffect::init(int sampleRate) {
    sampleRate_ = sampleRate;
    pitchDetector_.init(sampleRate, 1024, 0.15f);
    pitchShifter_.init(sampleRate, 1024);
    analysisBuffer_.assign(1024, 0.0f);
    analysisIndex_ = 0;
    currentPitchShift_ = 0.0f;
}

void AutoTuneEffect::setRootKey(int rootNote) {
    rootKey_ = (rootNote % 12 + 12) % 12;
}

void AutoTuneEffect::setScale(MusicalScale scale) {
    scale_ = scale;
}

void AutoTuneEffect::setRetuneSpeed(float speed) {
    retuneSpeed_ = dsp::clamp(speed, 0.05f, 1.0f);
}

void AutoTuneEffect::reset() {
    pitchShifter_.reset();
    std::fill(analysisBuffer_.begin(), analysisBuffer_.end(), 0.0f);
    analysisIndex_ = 0;
    currentPitchShift_ = 0.0f;
}

bool AutoTuneEffect::isNoteInScale(int midiNote) const {
    int noteInOctave = (midiNote % 12 + 12) % 12;
    int interval = (noteInOctave - rootKey_ + 12) % 12;

    switch (scale_) {
        case MusicalScale::CHROMATIC:
            return true;
        case MusicalScale::MAJOR: // 0, 2, 4, 5, 7, 9, 11
            return (interval == 0 || interval == 2 || interval == 4 || interval == 5 ||
                    interval == 7 || interval == 9 || interval == 11);
        case MusicalScale::MINOR: // 0, 2, 3, 5, 7, 8, 10
            return (interval == 0 || interval == 2 || interval == 3 || interval == 5 ||
                    interval == 7 || interval == 8 || interval == 10);
        case MusicalScale::PENTATONIC: // 0, 2, 4, 7, 9
            return (interval == 0 || interval == 2 || interval == 4 || interval == 7 || interval == 9);
    }
    return true;
}

float AutoTuneEffect::snapToScale(float inputHz) const {
    if (inputHz <= 20.0f) return inputHz;

    // Convert frequency to MIDI note number (A4 = 440 Hz = MIDI 69)
    float midiFloat = 69.0f + 12.0f * std::log2(inputHz / 440.0f);
    int roundedNote = static_cast<int>(std::round(midiFloat));

    // Find nearest note that matches the scale
    int bestNote = roundedNote;
    float bestDiff = 999.0f;

    for (int offset = -2; offset <= 2; ++offset) {
        int candidate = roundedNote + offset;
        if (isNoteInScale(candidate)) {
            float diff = std::abs(midiFloat - static_cast<float>(candidate));
            if (diff < bestDiff) {
                bestDiff = diff;
                bestNote = candidate;
            }
        }
    }

    // Convert MIDI back to target frequency
    return 440.0f * std::pow(2.0f, (static_cast<float>(bestNote) - 69.0f) / 12.0f);
}

void AutoTuneEffect::process(float* buffer, int numSamples) {
    // Fill circular analysis buffer for YIN detector
    for (int i = 0; i < numSamples; ++i) {
        analysisBuffer_[analysisIndex_] = buffer[i];
        analysisIndex_ = (analysisIndex_ + 1) % 1024;
    }

    // Run YIN pitch detection on current window
    float detectedPitch = pitchDetector_.detectPitch(analysisBuffer_.data(), 1024);

    if (detectedPitch > 55.0f && detectedPitch < 1000.0f && pitchDetector_.getProbability() > 0.65f) {
        float targetPitch = snapToScale(detectedPitch);
        float semitonesDiff = 12.0f * std::log2(targetPitch / detectedPitch);
        semitonesDiff = dsp::clamp(semitonesDiff, -3.0f, 3.0f); // Limit max correction per frame

        // Smooth or snap based on retune speed
        float alpha = retuneSpeed_ * 0.95f; // Fast = near 1.0 (hard snap), slow = smooth
        currentPitchShift_ = (1.0f - alpha) * currentPitchShift_ + alpha * semitonesDiff;
    } else {
        // Voice unvoiced or silent: decay pitch correction towards 0
        currentPitchShift_ *= 0.85f;
    }

    pitchShifter_.setPitchShiftSemitones(currentPitchShift_);
    pitchShifter_.process(buffer, numSamples);
}
