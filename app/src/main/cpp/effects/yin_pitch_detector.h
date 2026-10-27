#ifndef VOICE_RELAY_YIN_PITCH_DETECTOR_H
#define VOICE_RELAY_YIN_PITCH_DETECTOR_H

#include <vector>
#include <cstddef>

class YinPitchDetector {
public:
    explicit YinPitchDetector(int sampleRate = 48000, int bufferSize = 1024, float threshold = 0.15f);
    ~YinPitchDetector() = default;

    void init(int sampleRate, int bufferSize, float threshold = 0.15f);

    // Feeds audio and detects fundamental frequency in Hz. Returns -1.0f if unvoiced/silent.
    float detectPitch(const float* buffer, int numSamples);

    float getProbability() const { return probability_; }

private:
    void difference(const float* buffer);
    void cumulativeMeanNormalizedDifference();
    int absoluteThreshold();
    float parabolicInterpolation(int tauEstimate);

    int sampleRate_ = 48000;
    int bufferSize_ = 1024;
    int halfBufferSize_ = 512;
    float threshold_ = 0.15f;
    float probability_ = 0.0f;

    std::vector<float> yinBuffer_;
};

#endif // VOICE_RELAY_YIN_PITCH_DETECTOR_H
