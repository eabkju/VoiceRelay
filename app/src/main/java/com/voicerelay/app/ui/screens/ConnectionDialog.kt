package com.voicerelay.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.voicerelay.app.model.RemoteDeviceItem
import com.voicerelay.app.model.TransportCodec
import com.voicerelay.app.model.TransportProtocol

@Composable
fun ConnectionDialog(
    currentProtocol: TransportProtocol,
    onProtocolChanged: (TransportProtocol) -> Unit,
    currentCodec: TransportCodec,
    onCodecChanged: (TransportCodec) -> Unit,
    useBluetoothScoMic: Boolean,
    onUseBluetoothScoMicChanged: (Boolean) -> Unit,
    bondedDevices: List<RemoteDeviceItem>,
    discoveredDevices: List<RemoteDeviceItem>,
    onStartDiscovery: () -> Unit,
    onConnectBluetoothDevice: (String) -> Unit,
    localIpAddress: String,
    onConnectWifi: (String, Int) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(if (currentProtocol == TransportProtocol.BLUETOOTH_RFCOMM) 0 else 1) }
    var targetWifiIp by remember { mutableStateOf("192.168.1.100") }
    var targetWifiPort by remember { mutableStateOf("45454") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                Text(
                    text = "Connection & Transport Settings",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(12.dp))

                TabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = {
                            selectedTab = 0
                            onProtocolChanged(TransportProtocol.BLUETOOTH_RFCOMM)
                        },
                        text = { Text("Bluetooth") },
                        icon = { Icon(Icons.Default.Bluetooth, contentDescription = null) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = {
                            selectedTab = 1
                            onProtocolChanged(TransportProtocol.WIFI_UDP)
                        },
                        text = { Text("Wi-Fi UDP") },
                        icon = { Icon(Icons.Default.Wifi, contentDescription = null) }
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (selectedTab == 0) {
                    // --- BLUETOOTH TAB ---
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "DEVICES",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        IconButton(onClick = onStartDiscovery) {
                            Icon(Icons.Default.Refresh, contentDescription = "Scan")
                        }
                    }

                    LazyColumn(modifier = Modifier.weight(1f)) {
                        item {
                            Text(
                                "Bonded Devices",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        items(bondedDevices) { dev ->
                            DeviceRow(dev) { onConnectBluetoothDevice(dev.address) }
                        }

                        if (discoveredDevices.isNotEmpty()) {
                            item {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    "Discovered Devices",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            items(discoveredDevices) { dev ->
                                DeviceRow(dev) { onConnectBluetoothDevice(dev.address) }
                            }
                        }
                    }
                } else {
                    // --- WI-FI UDP TAB ---
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Local Device IP: $localIpAddress",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = targetWifiIp,
                            onValueChange = { targetWifiIp = it },
                            label = { Text("Receiver Phone IP Address") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = targetWifiPort,
                            onValueChange = { targetWifiPort = it },
                            label = { Text("UDP Port") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = {
                                val port = targetWifiPort.toIntOrNull() ?: 45454
                                onConnectWifi(targetWifiIp, port)
                                onDismiss()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Connect Wi-Fi Stream")
                        }
                    }
                }

                Divider(modifier = Modifier.padding(vertical = 10.dp))

                // Transport Codec selector
                Text(
                    text = "Transport Codec: ${currentCodec.title}",
                    style = MaterialTheme.typography.labelMedium
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    TransportCodec.values().forEach { codec ->
                        FilterChip(
                            selected = currentCodec == codec,
                            onClick = { onCodecChanged(codec) },
                            label = { Text("${codec.sampleRate / 1000}k") }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Bluetooth Headset Mic toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Bluetooth Headset Mic", style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = useBluetoothScoMic,
                        onCheckedChange = onUseBluetoothScoMicChanged
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Close")
                }
            }
        }
    }
}

@Composable
private fun DeviceRow(device: RemoteDeviceItem, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(device.name, style = MaterialTheme.typography.titleSmall)
                Text(device.address, style = MaterialTheme.typography.labelSmall)
            }
            Text(
                text = if (device.isBonded) "Paired" else "Connect",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
