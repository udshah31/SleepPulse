package com.sleeppulse.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sleeppulse.app.ui.theme.AlertCoral
import com.sleeppulse.app.ui.theme.CautionAmber
import com.sleeppulse.app.ui.theme.ClinicalTeal

@Composable
fun SleepConsistencyBadge(
    score: Int?,
    modifier: Modifier = Modifier
) {
    if (score == null) return

    val (color, text) = when {
        score >= 90 -> ClinicalTeal to "Excellent"
        score >= 70 -> CautionAmber to "Good"
        else -> AlertCoral to "Needs improvement"
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
            .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Consistency: $score/100 · $text",
            style = MaterialTheme.typography.bodyLarge,
            color = color
        )
    }
}
