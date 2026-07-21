package com.sleeppulse.wear

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable

@Composable
fun WearApp() {
    val context = LocalContext.current
    var heartRate by remember { mutableStateOf(0) }
    var sleepStage by remember { mutableStateOf("UNKNOWN") }
    
    DisposableEffect(context) {
        val dataClient = Wearable.getDataClient(context)
        val listener = DataClient.OnDataChangedListener { dataEvents ->
            for (event in dataEvents) {
                if (event.type == DataEvent.TYPE_CHANGED && event.dataItem.uri.path == "/sensor_data") {
                    val dataMap = DataMapItem.fromDataItem(event.dataItem).dataMap
                    heartRate = dataMap.getInt("heartRate", 0)
                    sleepStage = dataMap.getString("sleepStage", "UNKNOWN")
                }
            }
        }
        dataClient.addListener(listener)
        onDispose {
            dataClient.removeListener(listener)
        }
    }
    MaterialTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "SleepPulse",
                style = MaterialTheme.typography.title1,
                color = Color.White
            )
            Text(
                text = "HR: $heartRate bpm\nStage: $sleepStage",
                style = MaterialTheme.typography.body2,
                color = Color.LightGray,
                textAlign = TextAlign.Center
            )
        }
    }
}
