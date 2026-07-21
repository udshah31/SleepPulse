package com.sleeppulse.app.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun SleepConsistencyBadge(
    score: Int?,
    modifier: Modifier = Modifier
) {
    if (score == null) return

    val (color, text) = when {
        score >= 90 -> Color(0xFF4CAF50) to "Excellent"
        score >= 70 -> Color(0xFFFFC107) to "Good"
        else -> Color(0xFFF44336) to "Needs Improvement"
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f)),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = null,
                tint = color,
                modifier = Modifier.padding(end = 16.dp)
            )
            Text(
                text = "Consistency: $score/100 ($text)",
                style = MaterialTheme.typography.bodyLarge,
                color = color
            )
        }
    }
}
