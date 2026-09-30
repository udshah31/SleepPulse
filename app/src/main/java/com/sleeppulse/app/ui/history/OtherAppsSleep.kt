package com.sleeppulse.app.ui.history

import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sleeppulse.app.tracking.ExternalSleepSession
import com.sleeppulse.app.tracking.HealthConnectSleepSync
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One line in History's "From other apps" section. */
data class OtherAppsSleepRow(
    val id: String,
    val date: String,
    val duration: String,
    val stages: String?,
    val source: String,
)

/**
 * Newest first. [label] turns a package name into what the user sees (the app's name when
 * it's visible to us, else the package itself). Stage text is omitted when the source wrote
 * no deep/REM data, rather than showing "Deep 0m".
 */
fun toOtherAppsRows(
    sessions: List<ExternalSleepSession>,
    zone: ZoneId,
    label: (String) -> String = { it },
): List<OtherAppsSleepRow> {
    val dateFormat = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault())
    return sessions.sortedByDescending { it.startMillis }.map { s ->
        OtherAppsSleepRow(
            id = s.id,
            date = Instant.ofEpochMilli(s.startMillis).atZone(zone).format(dateFormat),
            duration = hoursMinutes(((s.endMillis - s.startMillis) / 60_000L).toInt()),
            stages = if (s.deepSleepMinutes == 0 && s.remSleepMinutes == 0) null
            else "Deep ${hoursMinutes(s.deepSleepMinutes)} · REM ${hoursMinutes(s.remSleepMinutes)}",
            source = label(s.sourcePackage),
        )
    }
}

private fun hoursMinutes(minutes: Int) = "%dh %02dm".format(minutes / 60, minutes % 60)

@HiltViewModel
class OtherAppsSleepViewModel @Inject constructor(
    private val sync: HealthConnectSleepSync,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    val rows: StateFlow<List<OtherAppsSleepRow>> = sync.sessions
        .map { toOtherAppsRows(it, ZoneId.systemDefault(), ::appLabel) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        // Opening History pulls anything written since the last sync (incremental after the first).
        viewModelScope.launch { sync.sync() }
    }

    private fun appLabel(packageName: String): String = try {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
    } catch (e: PackageManager.NameNotFoundException) {
        packageName // not visible to us without a <queries> entry
    }
}

/** Renders nothing until another app has written sleep to Health Connect. */
@Composable
fun OtherAppsSleepSection(viewModel: OtherAppsSleepViewModel = hiltViewModel()) {
    val rows by viewModel.rows.collectAsState()
    if (rows.isEmpty()) return

    Column(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
        Text(
            text = "FROM OTHER APPS",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        rows.forEach { row ->
            Card(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(row.date, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = listOfNotNull(row.duration, row.stages).joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = row.source,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
