package com.sleeppulse.app.tracking

import android.content.Context
import com.sleeppulse.app.data.model.SleepStage
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class SleepStagePredictor(private val context: Context) {
    
    private var interpreter: Interpreter? = null

    init {
        try {
            val modelBuffer = loadModelFile(context, "sleep_model.tflite")
            val options = Interpreter.Options()
            interpreter = Interpreter(modelBuffer, options)
            android.util.Log.i("SleepPulse", "Successfully loaded sleep_model.tflite")
        } catch (e: Exception) {
            android.util.Log.w("SleepPulse", "Failed to load TFLite model, falling back to heuristic predictions: ${e.message}")
        }
    }

    private fun loadModelFile(context: Context, modelName: String): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd(modelName)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    fun predict(heartRateBpm: Int, hrvMillis: Long, movement: Float): SleepStage {
        val tflite = interpreter
        if (tflite != null) {
            try {
                // Input shape: [1, 3] (hr, hrv, movement)
                val input = arrayOf(floatArrayOf(heartRateBpm.toFloat(), hrvMillis.toFloat(), movement))
                // Output shape: [1, 4] (probabilities for WAKE, LIGHT, DEEP, REM)
                val output = Array(1) { FloatArray(4) }
                
                tflite.run(input, output)
                
                val probs = output[0]
                val maxIdx = probs.indices.maxByOrNull { probs[it] } ?: 0
                return when (maxIdx) {
                    0 -> SleepStage.AWAKE
                    1 -> SleepStage.LIGHT
                    2 -> SleepStage.DEEP
                    3 -> SleepStage.REM
                    else -> SleepStage.AWAKE
                }
            } catch (e: Exception) {
                android.util.Log.e("SleepPulse", "Error running inference, falling back", e)
            }
        }
        
        // Fallback logic when model isn't available
        return when {
            movement > 0.8f -> SleepStage.AWAKE
            heartRateBpm < 50 && hrvMillis > 60 -> SleepStage.DEEP
            heartRateBpm in 50..65 && hrvMillis < 40 -> SleepStage.REM
            else -> SleepStage.LIGHT
        }
    }
    
    fun close() {
        interpreter?.close()
        interpreter = null
    }
}
