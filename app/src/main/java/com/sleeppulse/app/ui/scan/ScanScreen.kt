package com.sleeppulse.app.ui.scan

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sleeppulse.app.data.source.ScannedDevice
import com.sleeppulse.app.ui.components.CalmNightCard
import com.sleeppulse.app.ui.components.CalmNightSectionLabel
import com.sleeppulse.app.ui.theme.CalmNightTextSecondary
import com.sleeppulse.app.ui.theme.SleepIndigo

@Composable
fun ScanScreen(
    onDeviceSelected: (ScannedDevice) -> Unit,
    onBack: () -> Unit = {},
    viewModel: ScanViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        if (results.values.all { it }) {
            viewModel.onIntent(ScanIntent.StartScan)
        } else {
            viewModel.onIntent(ScanIntent.PermissionDenied)
        }
    }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT),
        )
    }

    LaunchedEffect(Unit) {
        viewModel.deviceSelected.collect { device -> onDeviceSelected(device) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "Connect a sensor", style = MaterialTheme.typography.headlineSmall)
                Text(
                    text = "Choose a nearby heart-rate device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = CalmNightTextSecondary,
                )
            }
            if (state.isScanning) {
                CircularProgressIndicator(
                    modifier = Modifier.padding(end = 4.dp).padding(2.dp),
                    color = SleepIndigo,
                    strokeWidth = 2.dp,
                )
            }
        }

        val error = state.error
        if (error != null) {
            CalmNightCard(modifier = Modifier.fillMaxWidth()) {
                CalmNightSectionLabel(text = "Bluetooth unavailable")
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Button(
                    onClick = {
                        permissionLauncher.launch(
                            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT),
                        )
                    },
                    modifier = Modifier.padding(top = 14.dp),
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = null)
                    Text("Retry", modifier = Modifier.padding(start = 8.dp))
                }
            }
        } else if (state.devices.isEmpty()) {
            CalmNightCard(
                modifier = Modifier.fillMaxWidth(),
                containerColor = androidx.compose.ui.graphics.Color.Transparent,
            ) {
                Icon(
                    Icons.Filled.Bluetooth,
                    contentDescription = null,
                    tint = SleepIndigo,
                )
                Text(
                    text = if (state.isScanning) "Looking for nearby devices…" else "No devices found yet",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 12.dp),
                )
                Text(
                    text = "Keep your sensor awake and nearby. Compatible devices will appear here as they advertise.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = CalmNightTextSecondary,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        } else {
            CalmNightSectionLabel(text = "Nearby devices")
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(state.devices, key = { it.address }) { device ->
                    CalmNightCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.onIntent(ScanIntent.SelectDevice(device)) },
                        padding = 16.dp,
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Filled.Bluetooth, contentDescription = null, tint = SleepIndigo)
                            Column(modifier = Modifier.padding(start = 12.dp)) {
                                Text(
                                    text = device.name ?: "Unnamed sensor",
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Text(
                                    text = device.address,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = CalmNightTextSecondary,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
