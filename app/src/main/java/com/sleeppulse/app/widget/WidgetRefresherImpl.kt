package com.sleeppulse.app.widget

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WidgetRefresherImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : WidgetRefresher {
    override suspend fun refresh() = SleepPulseWidget.refresh(context)
}
