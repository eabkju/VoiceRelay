package com.voicerelay.app.audio

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.voicerelay.app.MainActivity
import com.voicerelay.app.R
import com.voicerelay.app.model.*
import com.voicerelay.app.network.BluetoothRelayManager
import com.voicerelay.app.network.WifiUdpRelayManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AudioRelayService : Service(), AudioEngineBridge.PacketCaptureListener {

    companion object {
        const val CHANNEL_ID = "voice_relay_service_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.voicerelay.app.action.START"
        const val ACTION_STOP = "com.voicerelay.app.action.STOP"
        const val EXTRA_ROLE = "extra_role"
    }

    inner class LocalBinder : Binder() {
        fun getService(): AudioRelayService = this@AudioRelayService
    }

    private val binder = LocalBinder()
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    val audioEngine = AudioEngineBridge()
    lateinit var bluetoothRelay: BluetoothRelayManager
    lateinit var wifiRelay: WifiUdpRelayManager
    private lateinit var audioFocusManager: AudioFocusManager

    private var wakeLock: PowerManager.WakeLock? = null

    // Relay State Flows
    private val _currentRole = MutableStateFlow(AppRole.MIC)
    val currentRole: StateFlow<AppRole> = _currentRole.asStateFlow()

    private val _isRelayActive = MutableStateFlow(false)
    val isRelayActive: StateFlow<Boolean> = _isRelayActive.asStateFlow()

    private val _currentEffect = MutableStateFlow(VoiceEffect.NONE)
    val currentEffect: StateFlow<VoiceEffect> = _currentEffect.asStateFlow()

    private val _effectIntensity = MutableStateFlow(0.85f)
    val effectIntensity: StateFlow<Float> = _effectIntensity.asStateFlow()

    private val _masterGain = MutableStateFlow(1.0f)
    val masterGain: StateFlow<Float> = _masterGain.asStateFlow()

    private val _transportProtocol = MutableStateFlow(TransportProtocol.BLUETOOTH_RFCOMM)
    val transportProtocol: StateFlow<TransportProtocol> = _transportProtocol.asStateFlow()

    private val _transportCodec = MutableStateFlow(TransportCodec.PCM16_48K)
    val transportCodec: StateFlow<TransportCodec> = _transportCodec.asStateFlow()

    private val _useBluetoothScoMic = MutableStateFlow(false)
    val useBluetoothScoMic: StateFlow<Boolean> = _useBluetoothScoMic.asStateFlow()

    private val _livePeakLevel = MutableStateFlow(0.0f)
    val livePeakLevel: StateFlow<Float> = _livePeakLevel.asStateFlow()

    private val _liveRmsLevel = MutableStateFlow(0.0f)
    val liveRmsLevel: StateFlow<Float> = _liveRmsLevel.asStateFlow()

    private val _liveLatencyMs = MutableStateFlow(0.0f)
    val liveLatencyMs: StateFlow<Float> = _liveLatencyMs.asStateFlow()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "VoiceRelay:AudioWakeLock")

        audioEngine.init()
        audioEngine.setPacketListener(this)

        audioFocusManager = AudioFocusManager(
            context = this,
            onFocusLost = {
                // Keep running if in background, or lower volume
            },
            onNoisyHeadsetUnplug = {
                if (_currentRole.value == AppRole.LOCAL_AMP) {
                    stopRelay() // Protect against sudden acoustic feedback when headphones unplug
                }
            }
        )

        bluetoothRelay = BluetoothRelayManager(this) { packet ->
            audioEngine.receivePacket(packet)
        }

        wifiRelay = WifiUdpRelayManager(this) { packet ->
            audioEngine.receivePacket(packet)
        }

        startLevelMonitoring()
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopRelay()
                stopSelf()
            }
            ACTION_START -> {
                val roleId = intent.getIntExtra(EXTRA_ROLE, AppRole.MIC.id)
                val role = AppRole.values().firstOrNull { it.id == roleId } ?: AppRole.MIC
                startRelay(role)
            }
        }
        return START_NOT_STICKY
    }

    fun startRelay(role: AppRole) {
        _currentRole.value = role

        val hasFocus = audioFocusManager.requestFocus()
        audioFocusManager.setBluetoothScoEnabled(_useBluetoothScoMic.value)

        val started = audioEngine.start(role, _transportCodec.value)
        if (started) {
            _isRelayActive.value = true
            wakeLock?.acquire(3 * 3600 * 1000L) // 3 hours timeout safety

            if (role == AppRole.SPEAKER) {
                if (_transportProtocol.value == TransportProtocol.BLUETOOTH_RFCOMM) {
                    bluetoothRelay.startListening()
                } else {
                    wifiRelay.startListening()
                }
            }

            startForegroundWithNotification(role)
        }
    }

    fun stopRelay() {
        _isRelayActive.value = false
        audioEngine.stop()
        bluetoothRelay.disconnect()
        wifiRelay.stop()
        audioFocusManager.setBluetoothScoEnabled(false)
        audioFocusManager.abandonFocus()

        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    // Capture callback from C++ engine: send to network
    override fun onPacketCaptured(data: ByteArray) {
        if (_isRelayActive.value && _currentRole.value == AppRole.MIC) {
            if (_transportProtocol.value == TransportProtocol.BLUETOOTH_RFCOMM) {
                bluetoothRelay.sendPacket(data)
            } else {
                wifiRelay.sendPacket(data)
            }
        }
    }

    fun setEffect(effect: VoiceEffect) {
        _currentEffect.value = effect
        audioEngine.setEffect(effect)
        setIntensity(effect.defaultIntensity)
    }

    fun setIntensity(intensity: Float) {
        _effectIntensity.value = intensity
        audioEngine.setIntensity(intensity)
    }

    fun setMasterGain(gain: Float) {
        _masterGain.value = gain
        audioEngine.setMasterGain(gain)
    }

    fun setAutoTuneKey(key: Int) {
        audioEngine.setAutoTuneKey(key)
    }

    fun setAutoTuneScale(scale: Int) {
        audioEngine.setAutoTuneScale(scale)
    }

    fun setTransportProtocol(proto: TransportProtocol) {
        _transportProtocol.value = proto
    }

    fun setTransportCodec(codec: TransportCodec) {
        _transportCodec.value = codec
        audioEngine.setCodec(codec)
    }

    fun setBluetoothScoMic(enabled: Boolean) {
        _useBluetoothScoMic.value = enabled
        if (_isRelayActive.value) {
            audioFocusManager.setBluetoothScoEnabled(enabled)
        }
    }

    private fun startLevelMonitoring() {
        scope.launch {
            while (isActive) {
                if (_isRelayActive.value) {
                    _livePeakLevel.value = audioEngine.getPeakLevel()
                    _liveRmsLevel.value = audioEngine.getRmsLevel()
                    _liveLatencyMs.value = audioEngine.getEstimatedLatencyMs()
                } else {
                    _livePeakLevel.value = 0.0f
                    _liveRmsLevel.value = 0.0f
                }
                delay(33) // ~30 FPS updates
            }
        }
    }

    @SuppressLint("InlinedApi")
    private fun startForegroundWithNotification(role: AppRole) {
        val stopIntent = Intent(this, AudioRelayService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val activityIntent = Intent(this, MainActivity::class.java)
        val activityPendingIntent = PendingIntent.getActivity(
            this,
            0,
            activityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("VoiceRelay: ${role.title}")
            .setContentText("Active audio stream running • Live effects enabled")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(activityPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        val serviceTypes = ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE

        startForeground(NOTIFICATION_ID, notification, serviceTypes)
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notification_channel_desc)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    override fun onDestroy() {
        stopRelay()
        audioEngine.destroy()
        bluetoothRelay.release()
        wifiRelay.release()
        scope.cancel()
        super.onDestroy()
    }
}
