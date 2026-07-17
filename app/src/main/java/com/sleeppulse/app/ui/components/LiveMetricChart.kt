package com.sleeppulse.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * A minimal line chart that smoothly animates its vertical scale (via [Animatable]) whenever
 * the incoming point range shifts, so new readings feel like they're settling into place
 * rather than jump-cutting.
 */
@Composable
fun LiveMetricChart(
    values: List<Float>,
    color: Color,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 120.dp,
) {
    val maxValue = remember { Animatable(1f) }
    val minValue = remember { Animatable(0f) }

    val targetMax = (values.maxOrNull() ?: 1f).coerceAtLeast(1f)
    val targetMin = (values.minOrNull() ?: 0f)

    LaunchedEffect(targetMax) {
        maxValue.animateTo(targetMax, animationSpec = tween(600))
    }
    LaunchedEffect(targetMin) {
        minValue.animateTo(targetMin, animationSpec = tween(600))
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height),
    ) {
        if (values.size < 2) return@Canvas

        val range = (maxValue.value - minValue.value).coerceAtLeast(1f)
        val stepX = size.width / (values.size - 1)

        val path = androidx.compose.ui.graphics.Path()
        values.forEachIndexed { index, value ->
            val x = index * stepX
            val normalized = (value - minValue.value) / range
            val y = size.height - (normalized * size.height)
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }

        drawPath(
            path = path,
            color = color,
            style = Stroke(width = 6f, cap = StrokeCap.Round),
        )

        val lastX = (values.size - 1) * stepX
        val lastNormalized = (values.last() - minValue.value) / range
        val lastY = size.height - (lastNormalized * size.height)
        drawCircle(color = color, radius = 10f, center = Offset(lastX, lastY))
    }
}
