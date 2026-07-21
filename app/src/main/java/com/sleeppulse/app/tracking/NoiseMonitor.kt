package com.sleeppulse.app.tracking

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.File
import kotlin.math.log10

class NoiseMonitor(private val context: Context) {
    private var recorder: MediaRecorder? = null
    
    fun startMonitoring(): Flow<Double> = flow {
        try {
            val dummyFile = File(context.cacheDir, "dummy_audio.3gp")
            recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            
            recorder?.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
                setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
                setOutputFile(dummyFile.absolutePath)
                prepare()
                start()
            }

            // We sample the amplitude every 5 seconds
            while (true) {
                delay(5000)
                val amplitude = recorder?.maxAmplitude ?: 0
                if (amplitude > 0) {
                    val db = 20 * log10(amplitude.toDouble())
                    emit(db)
                } else {
                    emit(0.0)
                }
            }
        } catch (e: Exception) {
            Log.e("NoiseMonitor", "Error monitoring noise", e)
        } finally {
            stopMonitoring()
        }
    }

    fun stopMonitoring() {
        try {
            recorder?.apply {
                stop()
                release()
            }
        } catch (e: Exception) {
            // Ignore
        } finally {
            recorder = null
        }
    }
}
