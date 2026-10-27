package com.voicerelay.app.model

enum class VoiceEffect(
    val id: Int,
    val displayName: String,
    val description: String,
    val iconName: String,
    val defaultIntensity: Float
) {
    NONE(0, "Natural", "Clean passthrough with low-latency limiter", "mic", 1.0f),
    CHIPMUNK(1, "Chipmunk", "+7 semitones pitch shift with formant boost", "pets", 0.85f),
    CHILD(2, "Child", "+4 semitones pitch shift with warm timbre", "child_care", 0.8f),
    TALKING_TOM(3, "Talking Tom", "+9 semitones cartoon voice effect", "smart_toy", 0.9f),
    MONSTER(4, "Deep Monster", "-6 semitones pitch drop with dark formant", "mood_bad", 0.85f),
    FLANGER(5, "Flanger", "Modulated 1-10ms delay with LFO sweep", "waves", 0.75f),
    AUTOTUNE(6, "Auto-Tune", "YIN pitch detection & PSOLA scale snapping", "auto_fix_high", 0.9f),
    ROBOT(7, "Robot", "50Hz ring modulation with harmonic sidebands", "precision_manufacturing", 0.85f),
    ECHO(8, "Echo", "Warm feedback delay line (150-400ms)", "repeat", 0.6f),
    REVERB(9, "Reverb", "Schroeder-Freeverb room acoustic simulation", "surround_sound", 0.65f)
}

enum class MusicalKey(val index: Int, val noteName: String) {
    C(0, "C"),
    C_SHARP(1, "C#"),
    D(2, "D"),
    D_SHARP(3, "D#"),
    E(4, "E"),
    F(5, "F"),
    F_SHARP(6, "F#"),
    G(7, "G"),
    G_SHARP(8, "G#"),
    A(9, "A"),
    A_SHARP(10, "A#"),
    B(11, "B")
}

enum class MusicalScale(val id: Int, val scaleName: String) {
    CHROMATIC(0, "Chromatic (All Notes)"),
    MAJOR(1, "Major"),
    MINOR(2, "Natural Minor"),
    PENTATONIC(3, "Pentatonic")
}
