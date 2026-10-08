package com.sleeppulse.shared.db

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers

object IosDatabaseFactory {
    fun open(path: String): SleepPulseDatabase {
        require(path.startsWith("/")) { "Database path must be absolute" }
        return Room.databaseBuilder<SleepPulseDatabase>(
            name = path,
            factory = { SleepPulseDatabaseConstructor.initialize() },
        ).setDriver(BundledSQLiteDriver())
            .addMigrations(*SleepPulseDatabase.MIGRATIONS)
            .setQueryCoroutineContext(Dispatchers.Default)
            .build()
    }
}
