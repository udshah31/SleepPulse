package com.sleeppulse.app.widget

/**
 * Seam around [SleepPulseWidget.refresh] so callers that need to trigger a widget refresh
 * don't need an Android [android.content.Context] dependency directly — keeps them plain
 * Kotlin and unit-testable.
 */
interface WidgetRefresher {
    suspend fun refresh()
}
