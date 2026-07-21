package com.sleeppulse.app.ui.breathe

import androidx.compose.animation.core.*
import androidx.compose.animation.animateColor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

enum class BreatheState(val text: String, val durationMs: Int) {
    INHALE("Inhale", 4000),
    HOLD("Hold", 7000),
    EXHALE("Exhale", 8000)
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
        transitionSpec = {
            tween(durationMillis = targetState.durationMs, easing = LinearEasing)
        },
        label = "circle_scale"
    ) { state ->
        when (state) {
            BreatheState.INHALE -> 1.5f
            BreatheState.HOLD -> 1.5f
            BreatheState.EXHALE -> 0.5f
        }
    }
    
    val circleColor by transition.animateColor(
        transitionSpec = {
            tween(durationMillis = targetState.durationMs / 2, easing = LinearEasing)
        },
        label = "circle_color"
    ) { state ->
        when (state) {
            BreatheState.INHALE -> MaterialTheme.colorScheme.secondary
            BreatheState.HOLD -> MaterialTheme.colorScheme.primary
            BreatheState.EXHALE -> MaterialTheme.colorScheme.tertiary
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start
        ) {
            TextButton(onClick = onBack) {
                Text("Back", color = MaterialTheme.colorScheme.primary)
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Text(
            text = "4-7-8 Breathing",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        
        Spacer(modifier = Modifier.weight(1f))
        
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(300.dp)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val baseRadius = size.minDimension / 4
                val animatedRadius = baseRadius * (if (isPlaying) circleScale else 0.5f)
                
                drawCircle(
                    color = circleColor.copy(alpha = 0.2f),
                    radius = animatedRadius + 40f
                )
                
                drawCircle(
                    color = circleColor.copy(alpha = 0.5f),
                    radius = animatedRadius + 20f
                )
                
                drawCircle(
                    color = circleColor,
                    radius = animatedRadius,
                    style = Stroke(width = 8f)
                )
            }
            
            Text(
                text = if (isPlaying) currentState.text else "Ready",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        Button(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                isPlaying = !isPlaying
                if (!isPlaying) currentState = BreatheState.INHALE
            },
            modifier = Modifier
                .fillMaxWidth(0.6f)
                .height(56.dp)
        ) {
            Text(
                text = if (isPlaying) "Stop" else "Start",
                fontSize = 18.sp
            )
        }
        
        Spacer(modifier = Modifier.height(64.dp))
    }
}
