package com.voicerelay.app.audio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build

class AudioFocusManager(
    private val context: Context,
    private val onFocusLost: () -> Unit,
    private val onNoisyHeadsetUnplug: () -> Unit
) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var focusRequest: AudioFocusRequest? = null
    private var isReceiverRegistered = false

    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (AudioManager.ACTION_AUDIO_BECOMING_NOISY == intent?.action) {
                onNoisyHeadsetUnplug()
            }
        }
    }

    private val afChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                onFocusLost()
            }
        }
    }

    fun requestFocus(): Boolean {
        registerNoisyReceiver()

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()

        focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(audioAttributes)
            .setOnAudioFocusChangeListener(afChangeListener)
            .setAcceptsDelayedFocusGain(false)
            .build()

        val result = audioManager.requestAudioFocus(focusRequest!!)
        return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    fun abandonFocus() {
        unregisterNoisyReceiver()
        focusRequest?.let {
            audioManager.abandonAudioFocusRequest(it)
        }
        focusRequest = null
    }

    fun setBluetoothScoEnabled(enabled: Boolean) {
        try {
            if (enabled) {
                audioManager.startBluetoothSco()
                audioManager.isBluetoothScoOn = true
            } else {
                audioManager.isBluetoothScoOn = false
                audioManager.stopBluetoothSco()
            }
        } catch (_: Exception) {}
    }

    private fun registerNoisyReceiver() {
        if (!isReceiverRegistered) {
            val filter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
            context.registerReceiver(noisyReceiver, filter)
            isReceiverRegistered = true
        }
    }

    private fun unregisterNoisyReceiver() {
        if (isReceiverRegistered) {
            try {
                context.unregisterReceiver(noisyReceiver)
            } catch (_: Exception) {}
            isReceiverRegistered = false
        }
    }
}
