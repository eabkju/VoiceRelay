package com.voicerelay.app.audio

import com.voicerelay.app.model.AppRole
import com.voicerelay.app.model.TransportCodec
import com.voicerelay.app.model.VoiceEffect

class AudioEngineBridge {

    companion object {
        init {
            System.loadLibrary("voicerelay")
        }
    }

    interface PacketCaptureListener {
        fun onPacketCaptured(data: ByteArray)
    }

    private var packetListener: PacketCaptureListener? = null

    fun setPacketListener(listener: PacketCaptureListener?) {
        this.packetListener = listener
    }

    // Called from C++ JNI when capture thread has a new encoded packet
    @Suppress("unused")
    fun onAudioPacketCaptured(data: ByteArray) {
        packetListener?.onPacketCaptured(data)
    }

    fun init() {
        nativeInit()
    }

    fun start(role: AppRole, codec: TransportCodec): Boolean {
        return nativeStart(role.id, codec.id)
    }

    fun stop() {
        nativeStop()
    }

    fun setRole(role: AppRole) {
        nativeSetRole(role.id)
    }

    fun setCodec(codec: TransportCodec) {
        nativeSetCodec(codec.id)
    }

    fun setEffect(effect: VoiceEffect) {
        nativeSetEffectPreset(effect.id)
    }

    fun setIntensity(intensity: Float) {
        nativeSetEffectIntensity(intensity)
    }

    fun setMasterGain(gain: Float) {
        nativeSetMasterGain(gain)
    }

    fun setAutoTuneKey(rootKey: Int) {
        nativeSetAutoTuneKey(rootKey)
    }

    fun setAutoTuneScale(scale: Int) {
        nativeSetAutoTuneScale(scale)
    }

    fun receivePacket(data: ByteArray) {
        nativeReceiveAudioPacket(data, data.size)
    }

    fun getPeakLevel(): Float = nativeGetPeakLevel()
    fun getRmsLevel(): Float = nativeGetRmsLevel()
    fun getEstimatedLatencyMs(): Float = nativeGetEstimatedLatencyMs()

    fun destroy() {
        nativeDestroy()
    }

    // Native declarations
    private external fun nativeInit()
    private external fun nativeStart(role: Int, codec: Int): Boolean
    private external fun nativeStop()
    private external fun nativeSetRole(role: Int)
    private external fun nativeSetCodec(codec: Int)
    private external fun nativeSetEffectPreset(preset: Int)
    private external fun nativeSetEffectIntensity(intensity: Float)
    private external fun nativeSetMasterGain(gain: Float)
    private external fun nativeSetAutoTuneKey(rootKey: Int)
    private external fun nativeSetAutoTuneScale(scale: Int)
    private external fun nativeReceiveAudioPacket(data: ByteArray, size: Int)
    private external fun nativeGetPeakLevel(): Float
    private external fun nativeGetRmsLevel(): Float
    private external fun nativeGetEstimatedLatencyMs(): Float
    private external fun nativeDestroy()
}
