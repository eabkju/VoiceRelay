package com.voicerelay.app.model

enum class TransportCodec(val id: Int, val title: String, val sampleRate: Int) {
    PCM16_48K(0, "48 kHz (Ultra Clarity)", 48000),
    PCM16_24K(1, "24 kHz (Standard Balanced)", 24000),
    PCM16_16K(2, "16 kHz (Low Latency / Bandwidth)", 16000)
}

enum class TransportProtocol {
    BLUETOOTH_RFCOMM,
    WIFI_UDP
}

enum class ConnectionStatus {
    IDLE,
    LISTENING,
    CONNECTING,
    CONNECTED,
    DISCONNECTED,
    ERROR
}

data class RemoteDeviceItem(
    val name: String,
    val address: String,
    val isBonded: Boolean
)
