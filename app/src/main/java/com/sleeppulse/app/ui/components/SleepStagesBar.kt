package com.sleeppulse.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun SleepStagesBar(
    totalMinutes: Int,
    deepMinutes: Int,
    remMinutes: Int,
    modifier: Modifier = Modifier
) {
    if (totalMinutes <= 0) return

    val lightMinutes = (totalMinutes - deepMinutes - remMinutes).coerceAtLeast(0)

    val lightWeight = lightMinutes.toFloat() / totalMinutes.toFloat()
    val deepWeight = deepMinutes.toFloat() / totalMinutes.toFloat()
    val remWeight = remMinutes.toFloat() / totalMinutes.toFloat()

    val lightColor = Color(0xFF64B5F6) // Light Blue
    val deepColor = Color(0xFF5E35B1) // Deep Purple
    val remColor = Color(0xFF4DB6AC) // Teal

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(12.dp)
            .clip(RoundedCornerShape(6.dp))
    ) {
        if (lightWeight > 0) {
            Box(
                modifier = Modifier
                    .weight(lightWeight)
                    .fillMaxHeight()
                    .background(lightColor)
            )
        }
        if (deepWeight > 0) {
            Box(
                modifier = Modifier
                    .weight(deepWeight)
                    .fillMaxHeight()
                    .background(deepColor)
            )
        }
        if (remWeight > 0) {
            Box(
                modifier = Modifier
                    .weight(remWeight)
                    .fillMaxHeight()
                    .background(remColor)
            )
        }
    }
}
