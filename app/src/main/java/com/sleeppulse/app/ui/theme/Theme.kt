package com.sleeppulse.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Calm Night palette
val SleepIndigo = Color(0xFF7C8BFF)
val SleepIndigoDark = Color(0xFF5A63B8)
val RecoveryGreen = Color(0xFF5FD98A)
val CautionAmber = Color(0xFFF6C358)
val AlertCoral = Color(0xFFFF7A7A)

val CalmNightBackground = Color(0xFF0B0F22)
val CalmNightBackgroundEnd = Color(0xFF111633)
val CalmNightSurface = Color(0xFF151B3D)
val CalmNightSurfaceDim = Color(0xFF1C2248)
val CalmNightTextPrimary = Color(0xFFE4E6F5)
val CalmNightTextSecondary = Color(0xFF8B93C4)
val CalmNightTextTertiary = Color(0xFF5C6489)

// Dashboard accent — clinical teal used for guidance banners and the primary wind-down CTA.
val ClinicalTeal = Color(0xFF49C7B8)

private val CalmNightColors = darkColorScheme(
    primary = SleepIndigo,
    onPrimary = CalmNightBackground,
    secondary = RecoveryGreen,
    tertiary = CautionAmber,
    error = AlertCoral,
    background = CalmNightBackground,
    surface = CalmNightSurface,
    surfaceVariant = CalmNightSurfaceDim,
    onBackground = CalmNightTextPrimary,
    onSurface = CalmNightTextPrimary,
    onSurfaceVariant = CalmNightTextSecondary,
)

@Composable
fun SleepPulseTheme(
    amoledBlack: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (amoledBlack) {
        CalmNightColors.copy(
            background = Color.Black,
            surface = Color.Black,
        )
    } else {
        CalmNightColors
    }

    MaterialTheme(colorScheme = colorScheme, content = content)
}
