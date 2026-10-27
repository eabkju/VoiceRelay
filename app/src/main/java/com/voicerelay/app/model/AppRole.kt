package com.voicerelay.app.model

enum class AppRole(val id: Int, val title: String, val subtitle: String) {
    MIC(1, "Mic (Sender)", "Capture mic, apply voice effect, and stream to paired speaker"),
    SPEAKER(2, "Speaker (Receiver)", "Receive real-time audio stream and play on speaker/headphones"),
    LOCAL_AMP(3, "Local Amplifier", "Mic -> Effect -> Local phone speaker or Bluetooth headphones")
}
