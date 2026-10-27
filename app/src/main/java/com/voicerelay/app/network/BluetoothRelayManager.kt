package com.voicerelay.app.network

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import com.voicerelay.app.model.ConnectionStatus
import com.voicerelay.app.model.RemoteDeviceItem
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

class BluetoothRelayManager(
    private val context: Context,
    private val onPacketReceived: (ByteArray) -> Unit
) {
    companion object {
        private const val TAG = "VoiceRelayBT"
        // Custom RFCOMM service UUID for VoiceRelay phone-to-phone audio stream
        val RELAY_UUID: UUID = UUID.fromString("fa87c0d0-afac-11de-8a39-0800200c9a66")
        private const val SERVICE_NAME = "VoiceRelayAudioService"
    }

    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()

    private val _connectionStatus = MutableStateFlow(ConnectionStatus.IDLE)
    val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<RemoteDeviceItem>>(emptyList())
    val discoveredDevices: StateFlow<List<RemoteDeviceItem>> = _discoveredDevices.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var serverSocket: BluetoothServerSocket? = null
    private var activeSocket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null

    private var isAutoReconnectEnabled = true
    private var lastConnectedDeviceAddress: String? = null
    private var isSpeakerMode = false
    private var isRunning = false

    private val discoveryReceiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device: BluetoothDevice? =
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    device?.let { dev ->
                        val name = dev.name ?: "Unknown Device (${dev.address})"
                        val item = RemoteDeviceItem(name, dev.address, dev.bondState == BluetoothDevice.BOND_BONDED)
                        val current = _discoveredDevices.value.toMutableList()
                        if (current.none { it.address == dev.address }) {
                            current.add(item)
                            _discoveredDevices.value = current
                        }
                    }
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun getBondedDevices(): List<RemoteDeviceItem> {
        val bonded = bluetoothAdapter?.bondedDevices ?: return emptyList()
        return bonded.map { dev ->
            RemoteDeviceItem(
                name = dev.name ?: "Bonded Device",
                address = dev.address,
                isBonded = true
            )
        }
    }

    @SuppressLint("MissingPermission")
    fun startDiscovery() {
        bluetoothAdapter?.let { adapter ->
            if (adapter.isDiscovering) {
                adapter.cancelDiscovery()
            }
            val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
            try {
                context.registerReceiver(discoveryReceiver, filter)
            } catch (_: Exception) {}
            adapter.startDiscovery()
        }
    }

    @SuppressLint("MissingPermission")
    fun stopDiscovery() {
        try {
            bluetoothAdapter?.cancelDiscovery()
            context.unregisterReceiver(discoveryReceiver)
        } catch (_: Exception) {}
    }

    // --- SPEAKER / RECEIVER ROLE: Listen for incoming connections ---
    @SuppressLint("MissingPermission")
    fun startListening() {
        disconnect()
        isRunning = true
        isSpeakerMode = true
        _connectionStatus.value = ConnectionStatus.LISTENING

        scope.launch {
            while (isRunning && isSpeakerMode) {
                try {
                    serverSocket = bluetoothAdapter?.listenUsingRfcommWithServiceRecord(
                        SERVICE_NAME,
                        RELAY_UUID
                    )
                    Log.i(TAG, "RFCOMM server socket listening...")
                    val socket = serverSocket?.accept() // Blocking wait
                    if (socket != null) {
                        serverSocket?.close()
                        serverSocket = null
                        handleConnectedSocket(socket)
                    }
                } catch (e: Exception) {
                    if (isRunning && isSpeakerMode) {
                        Log.e(TAG, "Server socket accept error: ${e.message}")
                        delay(2000)
                    }
                }
            }
        }
    }

    // --- MIC / SENDER ROLE: Connect to remote speaker device ---
    @SuppressLint("MissingPermission")
    fun connectToDevice(deviceAddress: String) {
        disconnect()
        isRunning = true
        isSpeakerMode = false
        lastConnectedDeviceAddress = deviceAddress
        _connectionStatus.value = ConnectionStatus.CONNECTING

        scope.launch {
            var attempt = 0
            while (isRunning && !isSpeakerMode) {
                attempt++
                try {
                    val device = bluetoothAdapter?.getRemoteDevice(deviceAddress)
                    if (device == null) {
                        _connectionStatus.value = ConnectionStatus.ERROR
                        break
                    }

                    bluetoothAdapter.cancelDiscovery()
                    Log.i(TAG, "Connecting to ${device.name ?: deviceAddress} (attempt $attempt)...")

                    val socket = device.createRfcommSocketToServiceRecord(RELAY_UUID)
                    socket.connect()

                    handleConnectedSocket(socket)
                    break // Exits connect loop while connected
                } catch (e: Exception) {
                    Log.e(TAG, "RFCOMM connection failed: ${e.message}")
                    _connectionStatus.value = ConnectionStatus.DISCONNECTED

                    if (isAutoReconnectEnabled && isRunning && !isSpeakerMode) {
                        Log.i(TAG, "Retrying Bluetooth connection in 3 seconds...")
                        delay(3000)
                    } else {
                        break
                    }
                }
            }
        }
    }

    private suspend fun handleConnectedSocket(socket: BluetoothSocket) = withContext(Dispatchers.IO) {
        activeSocket = socket
        outputStream = socket.outputStream
        _connectionStatus.value = ConnectionStatus.CONNECTED
        Log.i(TAG, "RFCOMM socket successfully connected!")

        val inputStream: InputStream = socket.inputStream
        val readBuffer = ByteArray(2048)

        try {
            while (isRunning && socket.isConnected) {
                val bytesRead = inputStream.read(readBuffer)
                if (bytesRead > 0) {
                    val packet = readBuffer.copyOf(bytesRead)
                    onPacketReceived(packet)
                } else if (bytesRead < 0) {
                    break
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Socket read terminated: ${e.message}")
        } finally {
            closeActiveSocket()
            _connectionStatus.value = ConnectionStatus.DISCONNECTED

            // Auto-reconnect if unexpectedly dropped
            if (isRunning && isAutoReconnectEnabled) {
                if (isSpeakerMode) {
                    Log.i(TAG, "Speaker restarting listener...")
                    startListening()
                } else if (lastConnectedDeviceAddress != null) {
                    Log.i(TAG, "Mic auto-reconnecting to $lastConnectedDeviceAddress...")
                    delay(2000)
                    connectToDevice(lastConnectedDeviceAddress!!)
                }
            }
        }
    }

    fun sendPacket(data: ByteArray) {
        try {
            val out = outputStream
            if (out != null && activeSocket?.isConnected == true) {
                out.write(data)
                out.flush()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send packet: ${e.message}")
        }
    }

    private fun closeActiveSocket() {
        try {
            outputStream?.close()
            outputStream = null
            activeSocket?.close()
            activeSocket = null
        } catch (_: Exception) {}
    }

    fun disconnect() {
        isRunning = false
        closeActiveSocket()
        try {
            serverSocket?.close()
            serverSocket = null
        } catch (_: Exception) {}
        _connectionStatus.value = ConnectionStatus.DISCONNECTED
    }

    fun release() {
        disconnect()
        stopDiscovery()
        scope.cancel()
    }
}
