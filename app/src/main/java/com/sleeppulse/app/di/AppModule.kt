package com.sleeppulse.app.di

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import androidx.room.Room
import com.sleeppulse.app.data.local.NightlySummaryDao
import com.sleeppulse.app.data.local.SleepPulseDatabase
import com.sleeppulse.app.data.local.SleepSessionDao
import com.sleeppulse.app.data.repository.PrefsSettingsStore
import com.sleeppulse.app.data.repository.SettingsStore
import com.sleeppulse.app.data.repository.SleepRepository
import com.sleeppulse.app.data.repository.SleepRepositoryImpl
import com.sleeppulse.app.data.source.BleDeviceScanner
import com.sleeppulse.app.data.source.BleScanSource
import com.sleeppulse.app.data.source.BleSensorDataSource
import com.sleeppulse.app.data.source.BleTargetDeviceSink
import com.sleeppulse.app.data.source.SensorDataSource
import com.sleeppulse.app.data.source.SensorSourceManager
import com.sleeppulse.app.data.source.SimulatedSensorDataSource
import com.sleeppulse.app.notifications.SleepSummaryNotifier
import com.sleeppulse.app.notifications.SleepSummaryNotifierImpl
import com.sleeppulse.app.notifications.WindDownScheduler
import com.sleeppulse.app.notifications.WindDownSchedulerImpl
import com.sleeppulse.app.notifications.SmartAlarmScheduler
import com.sleeppulse.app.notifications.SmartAlarmSchedulerImpl
import com.sleeppulse.app.tracking.HealthConnectManager
import com.sleeppulse.app.tracking.PrefsSleepSyncStore
import com.sleeppulse.app.tracking.SleepSyncStore
import com.sleeppulse.app.tracking.SleepStagePredictor
import com.sleeppulse.app.widget.WidgetRefresher
import com.sleeppulse.app.widget.WidgetRefresherImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

@Module
@InstallIn(SingletonComponent::class)
abstract class BindingsModule {

    // Bound to the SensorSourceManager, which delegates dynamically between the simulated
    // and real BLE source based on the user's preference in Settings.
    @Binds
    @Singleton
    abstract fun bindSensorDataSource(impl: SensorSourceManager): SensorDataSource

    @Binds
    abstract fun bindBleScanSource(impl: BleDeviceScanner): BleScanSource

    @Binds
    abstract fun bindBleTargetDeviceSink(impl: BleSensorDataSource): BleTargetDeviceSink

    @Binds
    @Singleton
    abstract fun bindSleepRepository(impl: SleepRepositoryImpl): SleepRepository

    @Binds
    @Singleton
    abstract fun bindSleepSummaryNotifier(impl: SleepSummaryNotifierImpl): SleepSummaryNotifier

    @Binds
    @Singleton
    abstract fun bindWindDownScheduler(impl: WindDownSchedulerImpl): WindDownScheduler

    @Binds
    @Singleton
    abstract fun bindSmartAlarmScheduler(impl: SmartAlarmSchedulerImpl): SmartAlarmScheduler

    @Binds
    abstract fun bindSettingsStore(impl: PrefsSettingsStore): SettingsStore

    @Binds
    abstract fun bindSleepSyncStore(impl: PrefsSleepSyncStore): SleepSyncStore

    @Binds
    @Singleton
    abstract fun bindWidgetRefresher(impl: WidgetRefresherImpl): WidgetRefresher
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): SleepPulseDatabase =
        Room.databaseBuilder(context, SleepPulseDatabase::class.java, "sleeppulse.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideNightlySummaryDao(database: SleepPulseDatabase): NightlySummaryDao =
        database.nightlySummaryDao()

    @Provides
    fun provideSleepSessionDao(database: SleepPulseDatabase): SleepSessionDao =
        database.sleepSessionDao()

    @Provides
    @Singleton
    fun provideApplicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides
    fun provideNowMillis(): () -> Long = System::currentTimeMillis

    @Provides
    @Singleton
    fun provideAlarmManager(@ApplicationContext context: Context): AlarmManager =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    @Provides
    @Singleton
    fun provideNotificationManager(@ApplicationContext context: Context): NotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    @Provides
    @Singleton
    fun provideHealthConnectManager(@ApplicationContext context: Context): HealthConnectManager =
        HealthConnectManager(context)

    @Provides
    @Singleton
    fun provideSleepStagePredictor(@ApplicationContext context: Context): SleepStagePredictor =
        SleepStagePredictor(context)
}
