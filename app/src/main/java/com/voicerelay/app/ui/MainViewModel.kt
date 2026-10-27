package com.voicerelay.app.ui

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.voicerelay.app.audio.AudioRelayService
import com.voicerelay.app.model.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private var relayService: AudioRelayService? = null
    private val _isServiceBound = MutableStateFlow(false)

    // Exposed UI States
    val role = MutableStateFlow(AppRole.LOCAL_AMP)
    val isActive = MutableStateFlow(false)
    val currentEffect = MutableStateFlow(VoiceEffect.NONE)
    val effectIntensity = MutableStateFlow(0.85f)
    val masterGain = MutableStateFlow(1.0f)
    val transportProtocol = MutableStateFlow(TransportProtocol.BLUETOOTH_RFCOMM)
    val transportCodec = MutableStateFlow(TransportCodec.PCM16_48K)
    val useBluetoothScoMic = MutableStateFlow(false)
    val livePeakLevel = MutableStateFlow(0.0f)
    val liveRmsLevel = MutableStateFlow(0.0f)
    val liveLatencyMs = MutableStateFlow(0.0f)
    val connectionStatus = MutableStateFlow(ConnectionStatus.IDLE)
    val bondedDevices = MutableStateFlow<List<RemoteDeviceItem>>(emptyList())
    val discoveredDevices = MutableStateFlow<List<RemoteDeviceItem>>(emptyList())
    val localIpAddress = MutableStateFlow("127.0.0.1")

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val service = (binder as AudioRelayService.LocalBinder).getService()
            relayService = service
            _isServiceBound.value = true

            // Sync with service flows
            viewModelScope.launch {
                service.currentRole.collect { role.value = it }
            }
            viewModelScope.launch {
                service.isRelayActive.collect { isActive.value = it }
            }
            viewModelScope.launch {
                service.currentEffect.collect { currentEffect.value = it }
            }
            viewModelScope.launch {
                service.effectIntensity.collect { effectIntensity.value = it }
            }
            viewModelScope.launch {
                service.masterGain.collect { masterGain.value = it }
            }
            viewModelScope.launch {
                service.transportProtocol.collect { transportProtocol.value = it }
            }
            viewModelScope.launch {
                service.transportCodec.collect { transportCodec.value = it }
            }
            viewModelScope.launch {
                service.useBluetoothScoMic.collect { useBluetoothScoMic.value = it }
            }
            viewModelScope.launch {
                service.livePeakLevel.collect { livePeakLevel.value = it }
            }
            viewModelScope.launch {
                service.liveRmsLevel.collect { liveRmsLevel.value = it }
            }
            viewModelScope.launch {
                service.liveLatencyMs.collect { liveLatencyMs.value = it }
            }
            viewModelScope.launch {
                service.bluetoothRelay.connectionStatus.collect { status ->
                    if (service.transportProtocol.value == TransportProtocol.BLUETOOTH_RFCOMM) {
                        connectionStatus.value = status
                    }
                }
            }
            viewModelScope.launch {
                service.wifiRelay.connectionStatus.collect { status ->
                    if (service.transportProtocol.value == TransportProtocol.WIFI_UDP) {
                        connectionStatus.value = status
                    }
                }
            }
            viewModelScope.launch {
                service.bluetoothRelay.discoveredDevices.collect { discoveredDevices.value = it }
            }

            bondedDevices.value = service.bluetoothRelay.getBondedDevices()
            localIpAddress.value = service.wifiRelay.getLocalIpAddress()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            relayService = null
            _isServiceBound.value = false
        }
    }

    init {
        val intent = Intent(application, AudioRelayService::class.java)
        application.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    fun toggleRelay() {
        relayService?.let { service ->
            if (isActive.value) {
                service.stopRelay()
            } else {
                service.startRelay(role.value)
            }
        }
    }

    fun selectRole(newRole: AppRole) {
        role.value = newRole
        relayService?.let { service ->
            if (isActive.value) {
                service.stopRelay()
                service.startRelay(newRole)
            }
        }
    }

    fun selectEffect(effect: VoiceEffect) {
        relayService?.setEffect(effect)
    }

    fun setIntensity(intensity: Float) {
        relayService?.setIntensity(intensity)
    }

    fun setMasterGain(gain: Float) {
        relayService?.setMasterGain(gain)
    }

    fun setAutoTuneKey(key: Int) {
        relayService?.setAutoTuneKey(key)
    }

    fun setAutoTuneScale(scale: Int) {
        relayService?.setAutoTuneScale(scale)
    }

    fun setProtocol(proto: TransportProtocol) {
        relayService?.setTransportProtocol(proto)
    }

    fun setCodec(codec: TransportCodec) {
        relayService?.setTransportCodec(codec)
    }

    fun setBluetoothScoMic(enabled: Boolean) {
        relayService?.setBluetoothScoMic(enabled)
    }

    fun startDiscovery() {
        relayService?.bluetoothRelay?.startDiscovery()
        bondedDevices.value = relayService?.bluetoothRelay?.getBondedDevices() ?: emptyList()
    }

    fun connectBluetooth(address: String) {
        relayService?.bluetoothRelay?.connectToDevice(address)
    }

    fun connectWifi(targetIp: String, port: Int) {
        relayService?.wifiRelay?.startSender(targetIp, port)
    }

    override fun onCleared() {
        getApplication<Application>().unbindService(serviceConnection)
        super.onCleared()
    }
}
