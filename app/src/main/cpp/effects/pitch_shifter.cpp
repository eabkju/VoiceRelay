#include "pitch_shifter.h"
#include <cmath>
#include <cstring>

PitchShifter::PitchShifter() {
    init(48000, 1024);
}

void PitchShifter::init(int sampleRate, int maxGrainSize) {
    sampleRate_ = sampleRate;
    grainSize_ = maxGrainSize;
    ringSize_ = grainSize_ * 4;
    ringBuffer_.assign(ringSize_, 0.0f);
    window_.resize(grainSize_);

    // Precalculate Hanning window for dual-head crossfade
    for (int i = 0; i < grainSize_; ++i) {
        window_[i] = 0.5f * (1.0f - std::cos(dsp::TWO_PI * static_cast<float>(i) / static_cast<float>(grainSize_)));
    }

    reset();
}

void PitchShifter::setPitchShiftSemitones(float semitones) {
    pitchRatio_ = dsp::semitonesToRatio(semitones);
}

void PitchShifter::setFormantShift(float formantShift) {
    formant_ = dsp::clamp(formantShift, -1.0f, 1.0f);
}

void PitchShifter::reset() {
    std::fill(ringBuffer_.begin(), ringBuffer_.end(), 0.0f);
    writeIndex_ = 0;
    readPos1_ = 0.0f;
    readPos2_ = static_cast<float>(grainSize_ / 2);
    fState1_ = 0.0f;
    fState2_ = 0.0f;
}

void PitchShifter::process(float* buffer, int numSamples) {
    if (std::abs(pitchRatio_ - 1.0f) < 0.001f && std::abs(formant_) < 0.01f) {
        return; // Passthrough
    }

    const float halfGrain = static_cast<float>(grainSize_ / 2);

    for (int i = 0; i < numSamples; ++i) {
        float inSample = buffer[i];

        // Write input sample to circular delay buffer
        ringBuffer_[writeIndex_] = inSample;

        // Position of heads relative to grain window
        int winIdx1 = static_cast<int>(readPos1_) % grainSize_;
        int winIdx2 = static_cast<int>(readPos2_) % grainSize_;

        float w1 = window_[winIdx1];
        float w2 = window_[winIdx2];

        // Sample read from circular buffer for head 1
        int rIdx1 = (writeIndex_ - grainSize_ + winIdx1 + ringSize_) % ringSize_;
        int rIdx1Next = (rIdx1 + 1) % ringSize_;
        float frac1 = readPos1_ - std::floor(readPos1_);
        float s1 = dsp::lerp(ringBuffer_[rIdx1], ringBuffer_[rIdx1Next], frac1);

        // Sample read from circular buffer for head 2
        int rIdx2 = (writeIndex_ - grainSize_ + winIdx2 + ringSize_) % ringSize_;
        int rIdx2Next = (rIdx2 + 1) % ringSize_;
        float frac2 = readPos2_ - std::floor(readPos2_);
        float s2 = dsp::lerp(ringBuffer_[rIdx2], ringBuffer_[rIdx2Next], frac2);

        // Overlap-add
        float outSample = (s1 * w1 + s2 * w2);

        // Advance read heads by pitch ratio
        readPos1_ += pitchRatio_;
        if (readPos1_ >= static_cast<float>(grainSize_)) {
            readPos1_ -= static_cast<float>(grainSize_);
        }

        readPos2_ += pitchRatio_;
        if (readPos2_ >= static_cast<float>(grainSize_)) {
            readPos2_ -= static_cast<float>(grainSize_);
        }

        // Apply simple formant tilt / shaping filter
        if (formant_ > 0.01f) {
            // High boost / formant up
            float high = outSample - fState1_;
            fState1_ += 0.25f * high;
            outSample += formant_ * high * 0.6f;
        } else if (formant_ < -0.01f) {
            // Low boost / formant down
            fState2_ += 0.2f * (outSample - fState2_);
            outSample = dsp::lerp(outSample, fState2_, -formant_ * 0.5f);
        }

        buffer[i] = dsp::softLimit(outSample);

        writeIndex_ = (writeIndex_ + 1) % ringSize_;
    }
}
