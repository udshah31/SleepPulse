package com.sleeppulse.app.ui.scan

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sleeppulse.app.data.source.ScannedDevice

@Composable
fun ScanScreen(
    onDeviceSelected: (ScannedDevice) -> Unit,
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
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(text = "Nearby devices", style = MaterialTheme.typography.titleMedium)

        val error = state.error
        if (error != null) {
            Text(text = error)
            Button(
                onClick = {
                    permissionLauncher.launch(
                        arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT),
                    )
                },
            ) {
                Text("Retry")
            }
        } else {
            LazyColumn {
                items(state.devices, key = { it.address }) { device ->
                    Text(
                        text = device.name ?: device.address,
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable { viewModel.onIntent(ScanIntent.SelectDevice(device)) }
                            .padding(vertical = 12.dp),
                    )
                }
            }
        }
    }
}
