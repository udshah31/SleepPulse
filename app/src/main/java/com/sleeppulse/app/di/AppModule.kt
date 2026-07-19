package com.sleeppulse.app.di

import android.content.Context
import androidx.room.Room
import com.sleeppulse.app.data.local.NightlySummaryDao
import com.sleeppulse.app.data.local.SleepPulseDatabase
import com.sleeppulse.app.data.local.SleepSessionDao
import com.sleeppulse.app.data.repository.SleepRepository
import com.sleeppulse.app.data.repository.SleepRepositoryImpl
import com.sleeppulse.app.data.source.SensorDataSource
import com.sleeppulse.app.data.source.SimulatedSensorDataSource
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class BindingsModule {

    // Bound to the simulator for now; swap to BleSensorDataSource once a real
    // peripheral scan/connect flow is wired up. See README for the swap steps.
    @Binds
    @Singleton
    abstract fun bindSensorDataSource(impl: SimulatedSensorDataSource): SensorDataSource

    @Binds
    @Singleton
    abstract fun bindSleepRepository(impl: SleepRepositoryImpl): SleepRepository
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
}
