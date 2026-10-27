package com.voicerelay.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.voicerelay.app.model.VoiceEffect

@Composable
fun EffectCard(
    effect: VoiceEffect,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
    val backgroundColor = if (isSelected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }

    val icon: ImageVector = when (effect) {
        VoiceEffect.NONE -> Icons.Default.Mic
        VoiceEffect.CHIPMUNK -> Icons.Default.Pets
        VoiceEffect.CHILD -> Icons.Default.Face
        VoiceEffect.TALKING_TOM -> Icons.Default.Mood
        VoiceEffect.MONSTER -> Icons.Default.SentimentVeryDissatisfied
        VoiceEffect.FLANGER -> Icons.Default.Waves
        VoiceEffect.AUTOTUNE -> Icons.Default.AutoFixHigh
        VoiceEffect.ROBOT -> Icons.Default.PrecisionManufacturing
        VoiceEffect.ECHO -> Icons.Default.Repeat
        VoiceEffect.REVERB -> Icons.Default.SurroundSound
    }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(backgroundColor)
            .border(1.5.dp, borderColor, RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = effect.displayName,
            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(32.dp)
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = effect.displayName,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal),
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            textAlign = TextAlign.Center,
            fontSize = 11.sp
        )
    }
}
