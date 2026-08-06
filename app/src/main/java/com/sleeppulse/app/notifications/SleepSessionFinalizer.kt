package com.sleeppulse.app.notifications

import com.sleeppulse.app.data.repository.SleepRepository
import com.sleeppulse.app.ui.dashboard.RecoveryScoreCalculator
import com.sleeppulse.app.widget.WidgetRefresher
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Turns a just-ended sleep session into user-visible feedback: fires the summary
 * notification and refreshes the home-screen widget. Deliberately plain Kotlin (no Android
 * Service dependency) so this orchestration is unit-testable without Robolectric — the
 * actual [android.app.Service] that calls this stays a thin shell.
 */
@Singleton
class SleepSessionFinalizer @Inject constructor(
    private val repository: SleepRepository,
    private val notifier: SleepSummaryNotifier,
    private val widgetRefresher: WidgetRefresher,
    private val appScope: CoroutineScope,
) {
    suspend fun finalize() {
        val summary = try {
            repository.disconnectSensor()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Leave the session unfinalized in Room; SleepRepositoryImpl.recoverUnfinalizedSessions()
            // retries it on next app launch rather than losing the night's data.
            android.util.Log.w("SleepPulse", "Session finalize failed; will retry on next launch", e)
            return
        } ?: return

        val recovery = RecoveryScoreCalculator.scoreLatest(repository.recentNights().first())
        notifier.notify(summary, recovery)
        widgetRefresher.refresh()
    }

    /**
     * Fire-and-forget variant that launches [finalize] on [appScope] (the application-scoped
     * CoroutineScope from [com.sleeppulse.app.di.DatabaseModule]) instead of the caller's own
     * scope. Callers like [com.sleeppulse.app.services.SleepTrackingService] must use this
     * rather than launching finalize() on their own scope, since that scope is typically
     * cancelled (e.g. in Service.onDestroy()) within milliseconds of being launched — before
     * the Room read + upsert + Health Connect write + widget refresh can complete.
     */
    fun finalizeAsync() {
        appScope.launch { finalize() }
    }
}
