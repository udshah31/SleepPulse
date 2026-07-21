package com.sleeppulse.app.data.source

import com.sleeppulse.app.data.model.SensorConnectionState
import com.sleeppulse.app.data.model.SensorReading
import com.sleeppulse.app.data.repository.SettingsRepository
import com.sleeppulse.app.ui.settings.DataSourceMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A proxy [SensorDataSource] that dynamically delegates to either the [SimulatedSensorDataSource]
 * or the [BleSensorDataSource] based on the user's preference in [SettingsRepository].
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class SensorSourceManager @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val simulatedSource: SimulatedSensorDataSource,
    private val bleSource: BleSensorDataSource,
) : SensorDataSource {

    private val activeSource: SensorDataSource
        get() = when (settingsRepository.dataSourceMode.value) {
            DataSourceMode.SIMULATED -> simulatedSource
            DataSourceMode.BLE -> bleSource
        }

    override val connectionState: Flow<SensorConnectionState> =
        settingsRepository.dataSourceMode.flatMapLatest { mode ->
            when (mode) {
                DataSourceMode.SIMULATED -> simulatedSource.connectionState
                DataSourceMode.BLE -> bleSource.connectionState
            }
        }

    override fun readings(): Flow<SensorReading> =
        settingsRepository.dataSourceMode.flatMapLatest { mode ->
            when (mode) {
                DataSourceMode.SIMULATED -> simulatedSource.readings()
                DataSourceMode.BLE -> bleSource.readings()
            }
        }

    override suspend fun connect() {
        activeSource.connect()
    }

    override suspend fun disconnect() {
        // Disconnect both just to be safe, ensuring no background resources are left hanging
        // if the mode was switched while a connection was active.
        simulatedSource.disconnect()
        bleSource.disconnect()
    }
}
