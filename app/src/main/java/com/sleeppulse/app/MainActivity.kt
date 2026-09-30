package com.sleeppulse.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.sleeppulse.app.ui.SleepPulseApp
import com.sleeppulse.app.ui.theme.SleepPulseTheme
import com.sleeppulse.app.data.repository.SettingsRepository
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var settingsRepository: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val amoledBlack by settingsRepository.amoledBlack.collectAsState()
            SleepPulseTheme(amoledBlack = amoledBlack) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Transparent,
                ) {
                    SleepPulseApp()
                }
            }
        }
    }
}
