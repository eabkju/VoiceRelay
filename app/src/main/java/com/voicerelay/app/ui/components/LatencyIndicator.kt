package com.voicerelay.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.voicerelay.app.ui.theme.NeonCyan

@Composable
fun LatencyIndicator(
    latencyMs: Float,
    modifier: Modifier = Modifier
) {
    val latencyColor = when {
        latencyMs <= 50f -> NeonCyan
        latencyMs <= 120f -> Color(0xFF69F0AE)
        latencyMs <= 250f -> Color(0xFFFFD740)
        else -> Color(0xFFFF5252)
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Speed,
            contentDescription = "Latency",
            tint = latencyColor,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "${latencyMs.toInt()} ms",
            style = MaterialTheme.typography.labelMedium,
            color = latencyColor
        )
    }
}
