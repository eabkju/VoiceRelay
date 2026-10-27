package com.voicerelay.app.network

import android.content.Context
import android.net.wifi.WifiManager
import android.text.format.Formatter
import android.util.Log
import com.voicerelay.app.model.ConnectionStatus
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

class WifiUdpRelayManager(
    private val context: Context,
    private val onPacketReceived: (ByteArray) -> Unit
) {
    companion object {
        private const val TAG = "VoiceRelayWifi"
        const val DEFAULT_PORT = 45454
    }

    private val _connectionStatus = MutableStateFlow(ConnectionStatus.IDLE)
    val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var socket: DatagramSocket? = null
    private var targetAddress: InetAddress? = null
    private var targetPort: Int = DEFAULT_PORT
    private var isRunning = false

    @Suppress("DEPRECATION")
    fun getLocalIpAddress(): String {
        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            return Formatter.formatIpAddress(wifiManager.connectionInfo.ipAddress)
        } catch (_: Exception) {
            return "127.0.0.1"
        }
    }

    // --- SPEAKER / RECEIVER: Bind port and listen ---
    fun startListening(port: Int = DEFAULT_PORT) {
        stop()
        isRunning = true
        _connectionStatus.value = ConnectionStatus.LISTENING

        scope.launch {
            try {
                socket = DatagramSocket(port)
                _connectionStatus.value = ConnectionStatus.CONNECTED
                Log.i(TAG, "UDP socket listening on port $port")

                val buffer = ByteArray(2048)
                val packet = DatagramPacket(buffer, buffer.size)

                while (isRunning && socket?.isClosed == false) {
                    socket?.receive(packet)
                    val data = packet.data.copyOfRange(packet.offset, packet.offset + packet.length)
                    onPacketReceived(data)
                }
            } catch (e: Exception) {
                if (isRunning) {
                    Log.e(TAG, "UDP listener error: ${e.message}")
                    _connectionStatus.value = ConnectionStatus.ERROR
                }
            } finally {
                _connectionStatus.value = ConnectionStatus.DISCONNECTED
            }
        }
    }

    // --- MIC / SENDER: Configure target IP address and port ---
    fun startSender(targetIp: String, port: Int = DEFAULT_PORT) {
        stop()
        isRunning = true
        _connectionStatus.value = ConnectionStatus.CONNECTING

        scope.launch {
            try {
                targetAddress = InetAddress.getByName(targetIp)
                targetPort = port
                socket = DatagramSocket()
                _connectionStatus.value = ConnectionStatus.CONNECTED
                Log.i(TAG, "UDP sender ready for $targetIp:$port")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to resolve target IP: ${e.message}")
                _connectionStatus.value = ConnectionStatus.ERROR
            }
        }
    }

    fun sendPacket(data: ByteArray) {
        if (!isRunning || socket == null || targetAddress == null) return
        try {
            val packet = DatagramPacket(data, data.size, targetAddress, targetPort)
            socket?.send(packet)
        } catch (e: Exception) {
            Log.e(TAG, "UDP send failed: ${e.message}")
        }
    }

    fun stop() {
        isRunning = false
        try {
            socket?.close()
            socket = null
        } catch (_: Exception) {}
        _connectionStatus.value = ConnectionStatus.DISCONNECTED
    }

    fun release() {
        stop()
        scope.cancel()
    }
}
