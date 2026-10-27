#include "yin_pitch_detector.h"
#include <cmath>
#include <algorithm>

YinPitchDetector::YinPitchDetector(int sampleRate, int bufferSize, float threshold) {
    init(sampleRate, bufferSize, threshold);
}

void YinPitchDetector::init(int sampleRate, int bufferSize, float threshold) {
    sampleRate_ = sampleRate;
    bufferSize_ = bufferSize;
    halfBufferSize_ = bufferSize / 2;
    threshold_ = threshold;
    yinBuffer_.assign(halfBufferSize_, 0.0f);
}

void YinPitchDetector::difference(const float* buffer) {
    for (int tau = 0; tau < halfBufferSize_; ++tau) {
        float sum = 0.0f;
        for (int i = 0; i < halfBufferSize_; ++i) {
            float delta = buffer[i] - buffer[i + tau];
            sum += delta * delta;
        }
        yinBuffer_[tau] = sum;
    }
}

void YinPitchDetector::cumulativeMeanNormalizedDifference() {
    yinBuffer_[0] = 1.0f;
    float runningSum = 0.0f;

    for (int tau = 1; tau < halfBufferSize_; ++tau) {
        runningSum += yinBuffer_[tau];
        if (runningSum > 1e-9f) {
            yinBuffer_[tau] = (yinBuffer_[tau] * static_cast<float>(tau)) / runningSum;
        } else {
            yinBuffer_[tau] = 1.0f;
        }
    }
}

int YinPitchDetector::absoluteThreshold() {
    int tau;
    for (tau = 2; tau < halfBufferSize_; ++tau) {
        if (yinBuffer_[tau] < threshold_) {
            while (tau + 1 < halfBufferSize_ && yinBuffer_[tau + 1] < yinBuffer_[tau]) {
                tau++;
            }
            probability_ = 1.0f - yinBuffer_[tau];
            return tau;
        }
    }

    // If no pitch below threshold was found, find absolute minimum
    int minTau = 2;
    float minVal = yinBuffer_[2];
    for (tau = 3; tau < halfBufferSize_; ++tau) {
        if (yinBuffer_[tau] < minVal) {
            minVal = yinBuffer_[tau];
            minTau = tau;
        }
    }

    if (minVal < 0.45f) {
        probability_ = 1.0f - minVal;
        return minTau;
    }

    probability_ = 0.0f;
    return -1;
}

float YinPitchDetector::parabolicInterpolation(int tauEstimate) {
    if (tauEstimate <= 0 || tauEstimate >= halfBufferSize_ - 1) {
        return static_cast<float>(tauEstimate);
    }

    float s0 = yinBuffer_[tauEstimate - 1];
    float s1 = yinBuffer_[tauEstimate];
    float s2 = yinBuffer_[tauEstimate + 1];

    float denom = 2.0f * (2.0f * s1 - s2 - s0);
    if (std::abs(denom) < 1e-9f) {
        return static_cast<float>(tauEstimate);
    }

    float delta = (s2 - s0) / denom;
    return static_cast<float>(tauEstimate) + delta;
}

float YinPitchDetector::detectPitch(const float* buffer, int numSamples) {
    if (numSamples < bufferSize_) {
        return -1.0f;
    }

    // Check RMS energy to avoid analyzing background silence
    float energy = 0.0f;
    for (int i = 0; i < bufferSize_; ++i) {
        energy += buffer[i] * buffer[i];
    }
    float rms = std::sqrt(energy / static_cast<float>(bufferSize_));
    if (rms < 0.015f) {
        probability_ = 0.0f;
        return -1.0f;
    }

    difference(buffer);
    cumulativeMeanNormalizedDifference();

    int tauEstimate = absoluteThreshold();
    if (tauEstimate <= 0) {
        return -1.0f;
    }

    float refinedTau = parabolicInterpolation(tauEstimate);
    if (refinedTau <= 0.0f) {
        return -1.0f;
    }

    float pitchHz = static_cast<float>(sampleRate_) / refinedTau;
    if (pitchHz < 50.0f || pitchHz > 1200.0f) {
        return -1.0f;
    }

    return pitchHz;
}
