package com.voicerelay.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.voicerelay.app.model.*
import com.voicerelay.app.ui.components.*
import com.voicerelay.app.ui.theme.NeonCyan
import com.voicerelay.app.ui.theme.RedPeak

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainRelayScreen(
    currentRole: AppRole,
    onRoleSelected: (AppRole) -> Unit,
    isActive: Boolean,
    onToggleActive: () -> Unit,
    connectionStatus: ConnectionStatus,
    latencyMs: Float,
    peakLevel: Float,
    rmsLevel: Float,
    currentEffect: VoiceEffect,
    onEffectSelected: (VoiceEffect) -> Unit,
    effectIntensity: Float,
    onIntensityChanged: (Float) -> Unit,
    masterGain: Float,
    onMasterGainChanged: (Float) -> Unit,
    onAutoTuneKeyChanged: (Int) -> Unit,
    onAutoTuneScaleChanged: (Int) -> Unit,
    onOpenConnectionSettings: () -> Unit
) {
    var selectedAutoTuneKey by remember { mutableStateOf(0) }
    var selectedAutoTuneScale by remember { mutableStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "VoiceRelay",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            currentRole.title,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                actions = {
                    LatencyIndicator(latencyMs = latencyMs)
                    Spacer(modifier = Modifier.width(6.dp))
                    ConnectionStatusChip(status = connectionStatus)
                    Spacer(modifier = Modifier.width(6.dp))
                    IconButton(onClick = onOpenConnectionSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(10.dp))

            // --- ROLE SELECTOR ---
            SingleChoiceRoleBar(
                selectedRole = currentRole,
                enabled = !isActive,
                onRoleSelected = onRoleSelected
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Role explanation hint
            Text(
                text = when (currentRole) {
                    AppRole.LOCAL_AMP -> "🔊 Local Mic -> Real-Time Effects -> Speaker / Headphones"
                    AppRole.MIC -> "📡 Streaming to remote phone (Switch to 'Local Amplifier' for single-phone test)"
                    AppRole.SPEAKER -> "🎧 Playing incoming stream from sender phone in real time"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            // --- ACOUSTIC FEEDBACK PRECAUTION BANNER ---
            if (currentRole == AppRole.LOCAL_AMP || isActive) {
                FeedbackWarningBanner()
                Spacer(modifier = Modifier.height(12.dp))
            }

            // --- AUDIO LEVEL VU METER ---
            AudioLevelMeter(
                peak = peakLevel,
                rms = rmsLevel,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // --- BIG START / STOP BUTTON ---
            Button(
                onClick = onToggleActive,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isActive) RedPeak else MaterialTheme.colorScheme.primary
                )
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isActive) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isActive) "STOP AUDIO RELAY" else "START AUDIO RELAY",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // --- MASTER GAIN / INTENSITY CONTROLS ---
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    // Master Volume Gain Slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Master Gain / Volume", style = MaterialTheme.typography.labelMedium)
                        Text("${(masterGain * 100).toInt()}%", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                    Slider(
                        value = masterGain,
                        onValueChange = onMasterGainChanged,
                        valueRange = 0.0f..2.0f
                    )

                    // Effect Intensity Slider
                    if (currentEffect != VoiceEffect.NONE) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("${currentEffect.displayName} Intensity", style = MaterialTheme.typography.labelMedium)
                            Text("${(effectIntensity * 100).toInt()}%", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        }
                        Slider(
                            value = effectIntensity,
                            onValueChange = onIntensityChanged,
                            valueRange = 0.0f..1.0f
                        )
                    }

                    // Auto-Tune Key & Scale Snapping
                    if (currentEffect == VoiceEffect.AUTOTUNE) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Divider()
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Auto-Tune Key & Musical Scale", style = MaterialTheme.typography.labelMedium)

                        // Key Selector
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Key: ${MusicalKey.values()[selectedAutoTuneKey].noteName}", style = MaterialTheme.typography.bodySmall)
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                listOf(0, 2, 4, 5, 7, 9, 11).forEach { kIdx ->
                                    val key = MusicalKey.values()[kIdx]
                                    FilterChip(
                                        selected = selectedAutoTuneKey == kIdx,
                                        onClick = {
                                            selectedAutoTuneKey = kIdx
                                            onAutoTuneKeyChanged(kIdx)
                                        },
                                        label = { Text(key.noteName) }
                                    )
                                }
                            }
                        }

                        // Scale Selector
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            MusicalScale.values().forEach { scale ->
                                FilterChip(
                                    selected = selectedAutoTuneScale == scale.id,
                                    onClick = {
                                        selectedAutoTuneScale = scale.id
                                        onAutoTuneScaleChanged(scale.id)
                                    },
                                    label = { Text(scale.scaleName) }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // --- EFFECT PRESET GRID ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "VOICE EFFECTS PRESETS",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = currentEffect.displayName,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(340.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(VoiceEffect.values()) { effect ->
                    EffectCard(
                        effect = effect,
                        isSelected = currentEffect == effect,
                        onClick = { onEffectSelected(effect) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun SingleChoiceRoleBar(
    selectedRole: AppRole,
    enabled: Boolean,
    onRoleSelected: (AppRole) -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            AppRole.values().forEach { role ->
                val isSelected = role == selectedRole
                FilterChip(
                    selected = isSelected,
                    enabled = enabled,
                    onClick = { onRoleSelected(role) },
                    label = { Text(role.title) },
                    leadingIcon = {
                        val icon = when (role) {
                            AppRole.MIC -> Icons.Default.Mic
                            AppRole.SPEAKER -> Icons.Default.VolumeUp
                            AppRole.LOCAL_AMP -> Icons.Default.GraphicEq
                        }
                        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                )
            }
        }
    }
}

@Composable
private fun ConnectionStatusChip(status: ConnectionStatus) {
    val (color, text) = when (status) {
        ConnectionStatus.IDLE -> Color.Gray to "Idle"
        ConnectionStatus.LISTENING -> NeonCyan to "Listening"
        ConnectionStatus.CONNECTING -> Color(0xFFFFD54F) to "Connecting"
        ConnectionStatus.CONNECTED -> Color(0xFF69F0AE) to "Connected"
        ConnectionStatus.DISCONNECTED -> Color.Gray to "Offline"
        ConnectionStatus.ERROR -> RedPeak to "Error"
    }

    Surface(
        shape = CircleShape,
        color = color.copy(alpha = 0.15f),
        modifier = Modifier.clip(CircleShape)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(text, style = MaterialTheme.typography.labelSmall, color = color)
        }
    }
}
