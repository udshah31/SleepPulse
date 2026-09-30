package com.sleeppulse.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sleeppulse.app.ui.theme.CalmNightBackground
import com.sleeppulse.app.ui.theme.CalmNightBackgroundEnd
import com.sleeppulse.app.ui.theme.CalmNightSurface
import com.sleeppulse.app.ui.theme.CalmNightTextSecondary
import com.sleeppulse.app.ui.theme.SleepIndigo

private val CalmNightGradient = Brush.verticalGradient(
    colors = listOf(CalmNightBackground, CalmNightBackgroundEnd),
)

@Composable
fun CalmNightBackdrop(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    // AMOLED mode swaps the theme background to pure black; the gradient is only for the
    // default navy theme, otherwise the setting would silently do nothing.
    val background = MaterialTheme.colorScheme.background
    val brush = if (background == CalmNightBackground) CalmNightGradient else SolidColor(background)
    Box(modifier = modifier.background(brush)) {
        // A plain Box (unlike Surface) doesn't set a content colour, so Text without an explicit
        // color fell back to black — unreadable on navy.
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
            content()
        }
    }
}

@Composable
fun CalmNightCard(
    modifier: Modifier = Modifier,
    padding: Dp = 18.dp,
    shape: RoundedCornerShape = RoundedCornerShape(24.dp),
    containerColor: Color = CalmNightSurface,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .clip(shape)
            .background(containerColor)
            .border(1.dp, Color.White.copy(alpha = 0.05f), shape)
            .padding(padding),
    ) {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
            content()
        }
    }
}

@Composable
fun CalmNightSectionLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text.uppercase(),
        modifier = modifier,
        style = MaterialTheme.typography.labelMedium,
        color = CalmNightTextSecondary,
    )
}

@Composable
fun CalmNightStatusPill(
    text: String,
    modifier: Modifier = Modifier,
    active: Boolean = true,
) {
    val color = if (active) SleepIndigo else CalmNightTextSecondary
    androidx.compose.foundation.layout.Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(color)
                .padding(3.dp),
        )
        Text(
            text = text,
            modifier = Modifier.padding(start = 6.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color,
        )
    }
}
