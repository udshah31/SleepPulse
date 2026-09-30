package com.sleeppulse.app.ui.breathe

import androidx.compose.animation.animateColor
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sleeppulse.app.ui.components.CalmNightSectionLabel
import com.sleeppulse.app.ui.theme.CalmNightBackground
import com.sleeppulse.app.ui.theme.CalmNightTextSecondary
import com.sleeppulse.app.ui.theme.SleepIndigo
import com.sleeppulse.app.ui.theme.SleepIndigoDark
import kotlinx.coroutines.delay

// Ring radius as a multiple of a quarter of the 300dp box (75dp). The smallest ring (idle and
// end of exhale) must still hold the 32sp label; the largest plus its 18dp glow must fit the box.
private const val MIN_SCALE = 0.9f
private const val MAX_SCALE = 1.7f

enum class BreatheState(val text: String, val durationMs: Int) {
    INHALE("Inhale", 4000),
    HOLD("Hold", 7000),
    EXHALE("Exhale", 8000),
}

@Composable
fun BreatheScreen(onBack: () -> Unit) {
    var currentState by remember { mutableStateOf(BreatheState.INHALE) }
    var isPlaying by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(isPlaying, currentState) {
        if (isPlaying) {
            delay(currentState.durationMs.toLong())
            currentState = when (currentState) {
                BreatheState.INHALE -> BreatheState.HOLD
                BreatheState.HOLD -> BreatheState.EXHALE
                BreatheState.EXHALE -> BreatheState.INHALE
            }
        }
    }

    val transition = updateTransition(targetState = currentState, label = "breathe_transition")
    val circleScale by transition.animateFloat(
        transitionSpec = { tween(durationMillis = targetState.durationMs, easing = LinearEasing) },
        label = "circle_scale",
    ) { state ->
        when (state) {
            BreatheState.INHALE -> MAX_SCALE
            BreatheState.HOLD -> MAX_SCALE
            BreatheState.EXHALE -> MIN_SCALE
        }
    }
    val circleColor by transition.animateColor(
        transitionSpec = { tween(durationMillis = targetState.durationMs / 2, easing = LinearEasing) },
        label = "circle_color",
    ) { state ->
        when (state) {
            BreatheState.INHALE -> SleepIndigo.copy(alpha = 0.8f)
            BreatheState.HOLD -> SleepIndigo
            BreatheState.EXHALE -> SleepIndigoDark
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
            }
            Column(modifier = Modifier.padding(start = 4.dp)) {
                Text(text = "Breathe", style = MaterialTheme.typography.headlineSmall)
                Text(
                    text = "A gentle reset before sleep",
                    style = MaterialTheme.typography.bodySmall,
                    color = CalmNightTextSecondary,
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))
        CalmNightSectionLabel(text = "4 · 7 · 8 practice")
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .padding(top = 18.dp)
                .size(300.dp),
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val baseRadius = size.minDimension / 4
                val animatedRadius = baseRadius * (if (isPlaying) circleScale else MIN_SCALE)
                drawCircle(color = circleColor.copy(alpha = 0.08f), radius = animatedRadius + 18.dp.toPx())
                drawCircle(color = circleColor.copy(alpha = 0.14f), radius = animatedRadius + 11.dp.toPx())
                drawCircle(color = circleColor, radius = animatedRadius, style = Stroke(width = 3.dp.toPx()))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (isPlaying) currentState.text else "Ready",
                    fontSize = 32.sp,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                if (isPlaying) {
                    Text(
                        text = phaseHint(currentState),
                        style = MaterialTheme.typography.bodySmall,
                        color = CalmNightTextSecondary,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                isPlaying = !isPlaying
                if (!isPlaying) currentState = BreatheState.INHALE
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = SleepIndigo,
                contentColor = CalmNightBackground,
            ),
        ) {
            Text(text = if (isPlaying) "Stop practice" else "Start practice")
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

private fun phaseHint(state: BreatheState): String = when (state) {
    BreatheState.INHALE -> "Fill your lungs slowly"
    BreatheState.HOLD -> "Let the stillness settle"
    BreatheState.EXHALE -> "Release the day"
}
