package com.sleeppulse.app.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class SmartAlarmReceiver : BroadcastReceiver() {

    @Inject
    lateinit var smartAlarmScheduler: SmartAlarmScheduler

    override fun onReceive(context: Context, intent: Intent) {
        smartAlarmScheduler.fireAlarmNow()
    }
}
