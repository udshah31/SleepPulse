package com.sleeppulse.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.sleeppulse.app.ui.history.NightWithTrend

@Composable
fun WeeklyTrendsChart(
    nights: List<NightWithTrend>,
    modifier: Modifier = Modifier
) {
    if (nights.isEmpty()) return

    val primaryColor = MaterialTheme.colorScheme.primary

    // recentNights() is newest-first, so reverse it for a left-to-right chronological chart
    val chronologicalNights = nights.reversed()
    val maxScore = 100f
    val minScore = 0f

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp)
            .padding(16.dp)
    ) {
        val width = size.width
        val height = size.height

        val stepX = if (chronologicalNights.size > 1) {
            width / (chronologicalNights.size - 1).toFloat()
        } else {
            width
        }

        val path = Path()
        val points = mutableListOf<Offset>()

        chronologicalNights.forEachIndexed { index, night ->
            val score = night.summary.sleepScore.toFloat()
            val normalizedScore = (score - minScore) / (maxScore - minScore)
            val x = index * stepX
            val y = height - (normalizedScore * height)
            val point = Offset(x, y)
            points.add(point)

            if (index == 0) {
                path.moveTo(point.x, point.y)
            } else {
                path.lineTo(point.x, point.y)
            }
        }

        // Draw the line
        drawPath(
            path = path,
            color = primaryColor,
            style = Stroke(
                width = 4.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )

        // Draw the points
        points.forEach { point ->
            drawCircle(
                color = primaryColor,
                radius = 6.dp.toPx(),
                center = point
            )
            drawCircle(
                color = Color.White,
                radius = 3.dp.toPx(),
                center = point
            )
        }
    }
}
