package com.sleeppulse.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface NightlySummaryDao {
    @Query("SELECT * FROM nightly_summary ORDER BY dateEpochDay DESC LIMIT 30")
    fun observeRecent(): Flow<List<NightlySummaryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(summary: NightlySummaryEntity)

    @Query(
        "DELETE FROM nightly_summary WHERE dateEpochDay NOT IN " +
            "(SELECT dateEpochDay FROM nightly_summary ORDER BY dateEpochDay DESC LIMIT 30)"
    )
    suspend fun trimToLast30Days()
}
