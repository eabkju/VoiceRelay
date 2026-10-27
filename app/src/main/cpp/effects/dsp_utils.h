#ifndef VOICE_RELAY_DSP_UTILS_H
#define VOICE_RELAY_DSP_UTILS_H

#include <cmath>
#include <algorithm>
#include <cstdint>

#ifndef M_PI
#define M_PI 3.14159265358979323846
#endif

namespace dsp {

constexpr float PI = 3.14159265358979323846f;
constexpr float TWO_PI = 6.28318530717958647692f;

inline float clamp(float value, float minVal, float maxVal) {
    if (value < minVal) return minVal;
    if (value > maxVal) return maxVal;
    return value;
}

// Cubic soft limiter / saturator to prevent harsh digital clipping
inline float softLimit(float sample) {
    if (sample > 1.25f) return 1.0f;
    if (sample < -1.25f) return -1.0f;
    // Cubic polynomial with smooth knee
    if (sample > 0.6f) {
        float x = (sample - 0.6f) / 0.65f;
        return 0.6f + (x - (x * x * x) / 3.0f) * 0.4f;
    } else if (sample < -0.6f) {
        float x = (-sample - 0.6f) / 0.65f;
        return -(0.6f + (x - (x * x * x) / 3.0f) * 0.4f);
    }
    return sample;
}

// Convert semitones shift to frequency ratio (e.g., +7 -> 1.4983)
inline float semitonesToRatio(float semitones) {
    return std::pow(2.0f, semitones / 12.0f);
}

// Linear interpolation between two samples
inline float lerp(float a, float b, float frac) {
    return a + frac * (b - a);
}

} // namespace dsp

#endif // VOICE_RELAY_DSP_UTILS_H
