package com.sleeppulse.app.ui.dashboard

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun WindDownFlow(
    step: WindDownStep,
    onAdvance: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        AnimatedContent(
            targetState = step,
            transitionSpec = {
                (slideInVertically(animationSpec = tween(350)) { it / 2 } + fadeIn(tween(350))) togetherWith
                    (slideOutVertically(animationSpec = tween(250)) { -it / 2 } + fadeOut(tween(250)))
            },
            label = "wind-down-step",
        ) { targetStep ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = targetStep.title(), style = MaterialTheme.typography.headlineSmall)
                Text(text = targetStep.description(), style = MaterialTheme.typography.bodyMedium)
            }
        }

        if (step == WindDownStep.DONE) {
            Button(onClick = onCancel) { Text("Finish") }
        } else {
            Button(onClick = onAdvance) { Text("Next") }
            OutlinedButton(onClick = onCancel) { Text("Cancel") }
        }
    }
}

private fun WindDownStep.title(): String = when (this) {
    WindDownStep.BREATHE -> "Breathe"
    WindDownStep.DIM_LIGHTS -> "Dim the lights"
    WindDownStep.SET_ALARM -> "Set your alarm"
    WindDownStep.DONE -> "You're all set"
}

private fun WindDownStep.description(): String = when (this) {
    WindDownStep.BREATHE -> "Take five slow, deep breaths to settle your heart rate."
    WindDownStep.DIM_LIGHTS -> "Lower the lights around you to cue your body for sleep."
    WindDownStep.SET_ALARM -> "Confirm your wake time so SleepPulse can track a full night."
    WindDownStep.DONE -> "Sleep well — SleepPulse is now tracking your night."
}
