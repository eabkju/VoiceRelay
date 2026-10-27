package com.voicerelay.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.voicerelay.app.ui.theme.AmberWarning
import com.voicerelay.app.ui.theme.NeonCyan
import com.voicerelay.app.ui.theme.RedPeak

@Composable
fun AudioLevelMeter(
    peak: Float,
    rms: Float,
    modifier: Modifier = Modifier
) {
    val animatedPeak by animateFloatAsState(
        targetValue = peak.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 50),
        label = "peakAnim"
    )

    val animatedRms by animateFloatAsState(
        targetValue = rms.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 60),
        label = "rmsAnim"
    )

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "AUDIO LEVEL",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "${(animatedPeak * 100).toInt()}%",
                style = MaterialTheme.typography.labelSmall,
                color = if (animatedPeak > 0.85f) RedPeak else MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            // RMS fill bar
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(animatedRms)
                    .clip(RoundedCornerShape(5.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(NeonCyan, AmberWarning, RedPeak)
                        )
                    )
            )

            // Peak indicator line
            if (animatedPeak > 0.05f) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(3.dp)
                        .offset(x = (animatedPeak * 300).dp.coerceAtMost(320.dp))
                        .background(if (animatedPeak > 0.9f) RedPeak else Color.White)
                )
            }
        }
    }
}
