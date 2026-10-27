package com.voicerelay.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.voicerelay.app.ui.MainViewModel
import com.voicerelay.app.ui.screens.ConnectionDialog
import com.voicerelay.app.ui.screens.MainRelayScreen
import com.voicerelay.app.ui.screens.PermissionsScreen
import com.voicerelay.app.ui.theme.VoiceRelayTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            VoiceRelayTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    var hasPermissions by remember { mutableStateOf(checkRequiredPermissions()) }
                    var showConnectionDialog by remember { mutableStateOf(false) }

                    if (!hasPermissions) {
                        PermissionsScreen(
                            onPermissionsGranted = { hasPermissions = true }
                        )
                    } else {
                        val currentRole by viewModel.role.collectAsState()
                        val isActive by viewModel.isActive.collectAsState()
                        val connectionStatus by viewModel.connectionStatus.collectAsState()
                        val latencyMs by viewModel.liveLatencyMs.collectAsState()
                        val peakLevel by viewModel.livePeakLevel.collectAsState()
                        val rmsLevel by viewModel.liveRmsLevel.collectAsState()
                        val currentEffect by viewModel.currentEffect.collectAsState()
                        val effectIntensity by viewModel.effectIntensity.collectAsState()
                        val masterGain by viewModel.masterGain.collectAsState()
                        val currentProtocol by viewModel.transportProtocol.collectAsState()
                        val currentCodec by viewModel.transportCodec.collectAsState()
                        val useBluetoothScoMic by viewModel.useBluetoothScoMic.collectAsState()
                        val bondedDevices by viewModel.bondedDevices.collectAsState()
                        val discoveredDevices by viewModel.discoveredDevices.collectAsState()
                        val localIpAddress by viewModel.localIpAddress.collectAsState()

                        MainRelayScreen(
                            currentRole = currentRole,
                            onRoleSelected = { viewModel.selectRole(it) },
                            isActive = isActive,
                            onToggleActive = { viewModel.toggleRelay() },
                            connectionStatus = connectionStatus,
                            latencyMs = latencyMs,
                            peakLevel = peakLevel,
                            rmsLevel = rmsLevel,
                            currentEffect = currentEffect,
                            onEffectSelected = { viewModel.selectEffect(it) },
                            effectIntensity = effectIntensity,
                            onIntensityChanged = { viewModel.setIntensity(it) },
                            masterGain = masterGain,
                            onMasterGainChanged = { viewModel.setMasterGain(it) },
                            onAutoTuneKeyChanged = { viewModel.setAutoTuneKey(it) },
                            onAutoTuneScaleChanged = { viewModel.setAutoTuneScale(it) },
                            onOpenConnectionSettings = { showConnectionDialog = true }
                        )

                        if (showConnectionDialog) {
                            ConnectionDialog(
                                currentProtocol = currentProtocol,
                                onProtocolChanged = { viewModel.setProtocol(it) },
                                currentCodec = currentCodec,
                                onCodecChanged = { viewModel.setCodec(it) },
                                useBluetoothScoMic = useBluetoothScoMic,
                                onUseBluetoothScoMicChanged = { viewModel.setBluetoothScoMic(it) },
                                bondedDevices = bondedDevices,
                                discoveredDevices = discoveredDevices,
                                onStartDiscovery = { viewModel.startDiscovery() },
                                onConnectBluetoothDevice = { address ->
                                    viewModel.connectBluetooth(address)
                                    showConnectionDialog = false
                                },
                                localIpAddress = localIpAddress,
                                onConnectWifi = { ip, port ->
                                    viewModel.connectWifi(ip, port)
                                    showConnectionDialog = false
                                },
                                onDismiss = { showConnectionDialog = false }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun checkRequiredPermissions(): Boolean {
        val recordAudio = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        val btConnect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        return recordAudio && btConnect
    }
}
