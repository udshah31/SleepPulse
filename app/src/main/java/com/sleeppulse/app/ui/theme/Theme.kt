package com.sleeppulse.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val SleepIndigo = Color(0xFF4F5DFF)
val SleepIndigoDark = Color(0xFF2E3A9E)
val RecoveryGreen = Color(0xFF2ED9A6)
val CautionAmber = Color(0xFFFFB454)
val AlertCoral = Color(0xFFFF5C7A)
val MidnightBg = Color(0xFF0E1020)
val MidnightSurface = Color(0xFF171A33)

private val DarkColors = darkColorScheme(
    primary = SleepIndigo,
    secondary = RecoveryGreen,
    tertiary = CautionAmber,
    background = MidnightBg,
    surface = MidnightSurface,
)

private val LightColors = lightColorScheme(
    primary = SleepIndigoDark,
    secondary = RecoveryGreen,
    tertiary = CautionAmber,
)

@Composable
fun SleepPulseTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkColors else LightColors
    MaterialTheme(colorScheme = colors, content = content)
}
