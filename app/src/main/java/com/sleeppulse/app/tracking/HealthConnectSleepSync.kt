package com.sleeppulse.app.tracking

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

/**
 * Keeps a local copy of other apps' sleep sessions in step with Health Connect.
 *
 * - First run, or an expired token: take a changes token, then read the last [WINDOW] in full.
 * - After that: fetch only what changed since the stored token.
 * - Read access gone (revoked, or never granted): drop the token and the cached sessions, since
 *   we no longer have permission to hold that data. A later grant starts over with a full read.
 */
@Singleton
class HealthConnectSleepSync @Inject constructor(
    private val manager: HealthConnectManager,
    private val store: SleepSyncStore,
    private val nowMillis: () -> Long,
) {
    private val _sessions = MutableStateFlow(store.sessions)
    val sessions: StateFlow<List<ExternalSleepSession>> = _sessions.asStateFlow()

    private val mutex = Mutex()

    /**
     * Returns false if Health Connect failed for a reason other than lost read access (a
     * transient error, or a background read the device refused); the cache and token are left
     * as they were so the next attempt picks up where this one stopped. Never throws, so
     * fire-and-forget callers (app start, History) can't crash on it.
     */
    suspend fun sync(): Boolean = mutex.withLock {
        try {
            val token = store.token
            when (val result = token?.let { manager.getSleepChanges(it) }) {
                is SleepChanges.Changes -> save(
                    applyChanges(_sessions.value, result.copy(upserted = withHeartRate(result.upserted))),
                    result.nextToken,
                )
                SleepChanges.NoPermission -> clear()
                SleepChanges.TokenExpired, null -> fullResync()
            }
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.w("SleepPulse", "Health Connect sleep sync failed; will retry", e)
            false
        }
    }

    private suspend fun fullResync() {
        val token = manager.getChangesToken() ?: return clear()
        val now = Instant.ofEpochMilli(nowMillis())
        save(withHeartRate(manager.readSleepSessions(now.minus(WINDOW), now)), token)
    }

    // ponytail: heart rate is read once, when the session arrives; the token only tracks sleep
    // records, so heart rate an app uploads after its session isn't picked up until a full resync.
    private suspend fun withHeartRate(sessions: List<ExternalSleepSession>) =
        sessions.map { it.copy(avgHeartRateBpm = manager.averageHeartRate(it)) }

    private fun save(sessions: List<ExternalSleepSession>, token: String) {
        store.sessions = sessions
        store.token = token
        _sessions.value = sessions
    }

    private fun clear() {
        store.token = null
        store.sessions = emptyList()
        _sessions.value = emptyList()
    }

    companion object {
        val WINDOW: Duration = Duration.ofDays(30)

        /** An upsert replaces any session with the same id; a deletion removes it. */
        fun applyChanges(current: List<ExternalSleepSession>, changes: SleepChanges.Changes): List<ExternalSleepSession> {
            val replaced = changes.upserted.map { it.id }.toSet()
            val deleted = changes.deletedIds.toSet()
            return (current.filter { it.id !in replaced && it.id !in deleted } + changes.upserted)
                .sortedBy { it.startMillis }
        }
    }
}

/** Where the sync keeps its token and cache between launches. */
interface SleepSyncStore {
    var token: String?
    var sessions: List<ExternalSleepSession>
}

class PrefsSleepSyncStore @Inject constructor(@ApplicationContext context: Context) : SleepSyncStore {
    private val prefs = context.getSharedPreferences("health_connect_sleep_sync", Context.MODE_PRIVATE)

    override var token: String?
        get() = prefs.getString(KEY_TOKEN, null)
        set(value) = prefs.edit().putString(KEY_TOKEN, value).apply()

    override var sessions: List<ExternalSleepSession>
        get() {
            val array = JSONArray(prefs.getString(KEY_SESSIONS, "[]"))
            return (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                ExternalSleepSession(
                    id = o.getString("id"),
                    startMillis = o.getLong("start"),
                    endMillis = o.getLong("end"),
                    deepSleepMinutes = o.getInt("deep"),
                    remSleepMinutes = o.getInt("rem"),
                    sourcePackage = o.getString("source"),
                    avgHeartRateBpm = if (o.has("hr")) o.getLong("hr") else null,
                )
            }
        }
        set(value) {
            val array = JSONArray()
            value.forEach {
                array.put(
                    JSONObject()
                        .put("id", it.id).put("start", it.startMillis).put("end", it.endMillis)
                        .put("deep", it.deepSleepMinutes).put("rem", it.remSleepMinutes)
                        .put("source", it.sourcePackage)
                        .apply { it.avgHeartRateBpm?.let { hr -> put("hr", hr) } }
                )
            }
            prefs.edit().putString(KEY_SESSIONS, array.toString()).apply()
        }

    private companion object {
        const val KEY_TOKEN = "changes_token"
        const val KEY_SESSIONS = "sessions"
    }
}
