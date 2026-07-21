package com.sleeppulse.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import android.os.Build
import androidx.compose.ui.graphics.Color

// Default Night / Evening Colors
val SleepIndigo = Color(0xFF4F5DFF)
val SleepIndigoDark = Color(0xFF2E3A9E)
val RecoveryGreen = Color(0xFF2ED9A6)
val CautionAmber = Color(0xFFFFB454)
val AlertCoral = Color(0xFFFF5C7A)
val MidnightBg = Color(0xFF0E1020)
val MidnightSurface = Color(0xFF171A33)

// Morning Colors
val MorningOrange = Color(0xFFFF8B3D)
val MorningYellow = Color(0xFFFFD15C)
val MorningBg = Color(0xFFFFF4EB)
val MorningSurface = Color(0xFFFFFFFF)

// Daytime Colors
val DaySkyBlue = Color(0xFF42A5F5)
val DayTeal = Color(0xFF26A69A)
val DayBg = Color(0xFFF0F8FF)
val DaySurface = Color(0xFFFFFFFF)

// Evening Colors
val EveningPurple = Color(0xFF7E57C2)
val EveningIndigo = Color(0xFF5C6BC0)
val EveningBg = Color(0xFF1F1B24)
val EveningSurface = Color(0xFF2C2735)

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

private val MorningColors = lightColorScheme(
    primary = MorningOrange,
    secondary = MorningYellow,
    background = MorningBg,
    surface = MorningSurface,
)

private val DayColors = lightColorScheme(
    primary = DaySkyBlue,
    secondary = DayTeal,
    background = DayBg,
    surface = DaySurface,
)

private val EveningColors = darkColorScheme(
    primary = EveningPurple,
    secondary = EveningIndigo,
    background = EveningBg,
    surface = EveningSurface,
)

@Composable
fun SleepPulseTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    amoledBlack: Boolean = false,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    
    val timeBasedColorScheme = when (hour) {
        in 6..11 -> MorningColors
        in 12..17 -> DayColors
        in 18..21 -> EveningColors
        else -> DarkColors // Night
    }

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        else -> timeBasedColorScheme
    }

    val finalColorScheme = if (darkTheme && amoledBlack) {
        colorScheme.copy(
            background = Color.Black,
            surface = Color.Black
        )
    } else {
        colorScheme
    }

    MaterialTheme(colorScheme = finalColorScheme, content = content)
}
