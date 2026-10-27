package com.voicerelay.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.voicerelay.app.ui.theme.AmberWarning

@Composable
fun FeedbackWarningBanner(
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(AmberWarning.copy(alpha = 0.12f))
            .border(1.dp, AmberWarning.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = "Feedback Warning",
            tint = AmberWarning,
            modifier = Modifier.size(28.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(
                text = "Acoustic Feedback Precaution",
                style = MaterialTheme.typography.titleSmall,
                color = AmberWarning
            )
            Text(
                text = "Use headphones or keep phones in separate rooms to prevent high-pitched squealing feedback.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
            )
        }
    }
}
