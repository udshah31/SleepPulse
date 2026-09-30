package com.sleeppulse.app.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sleeppulse.app.data.model.NightlySummary
import com.sleeppulse.app.data.repository.SleepRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.launch

/** Common sleep factors offered up front; anything else can be typed in. */
val PRESET_TAGS = listOf("Caffeine", "Alcohol", "Late meal", "Exercise", "Late screen time", "Stress", "Nap")

private const val MAX_TAG_LENGTH = 24

/** Presets first, then any custom tags this night already has, so nothing selected is hidden. */
fun tagOptions(selected: List<String>): List<String> =
    PRESET_TAGS + selected.filter { tag -> PRESET_TAGS.none { it.equals(tag, ignoreCase = true) } }

/**
 * Adds a typed tag: trimmed, inner whitespace collapsed, capped at [MAX_TAG_LENGTH]. Blank input
 * or a case-insensitive duplicate (of a preset or a selected tag) leaves the list unchanged —
 * except that typing a preset's name selects that preset in its own spelling, so "caffeine"
 * and "Caffeine" never split one factor's correlation in two.
 */
fun addTypedTag(selected: List<String>, input: String): List<String> {
    val tag = input.trim().replace(Regex("\\s+"), " ").take(MAX_TAG_LENGTH).trim()
    if (tag.isEmpty() || selected.any { it.equals(tag, ignoreCase = true) }) return selected
    val canonical = PRESET_TAGS.firstOrNull { it.equals(tag, ignoreCase = true) } ?: tag
    return selected + canonical
}

fun toggleTag(selected: List<String>, tag: String): List<String> =
    if (tag in selected) selected - tag else selected + tag

@HiltViewModel
class NightTagsViewModel @Inject constructor(
    private val repository: SleepRepository,
) : ViewModel() {
    fun setTags(date: LocalDate, tags: List<String>) {
        viewModelScope.launch { repository.updateTags(date, tags) }
    }
}

/** A night's tags, plus the entry point for editing them. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NightTags(summary: NightlySummary, viewModel: NightTagsViewModel = hiltViewModel()) {
    var editing by remember { mutableStateOf(false) }

    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        summary.tags.forEach { tag ->
            SuggestionChip(
                onClick = { editing = true },
                label = { Text(text = tag, style = MaterialTheme.typography.labelSmall) },
            )
        }
        AssistChip(
            onClick = { editing = true },
            label = {
                Text(
                    text = if (summary.tags.isEmpty()) "+ Add tags" else "Edit",
                    style = MaterialTheme.typography.labelSmall,
                )
            },
        )
    }

    if (editing) {
        TagEditorDialog(
            date = summary.date,
            initial = summary.tags,
            onDismiss = { editing = false },
            onSave = { tags ->
                viewModel.setTags(summary.date, tags)
                editing = false
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagEditorDialog(
    date: LocalDate,
    initial: List<String>,
    onDismiss: () -> Unit,
    onSave: (List<String>) -> Unit,
) {
    var selected by remember { mutableStateOf(initial) }
    var typed by remember { mutableStateOf("") }
    val addTyped = {
        selected = addTypedTag(selected, typed)
        typed = ""
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tags for ${date.format(DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault()))}") },
        text = {
            Column {
                Text(
                    text = "What might have affected this night?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    tagOptions(selected).forEach { tag ->
                        FilterChip(
                            selected = tag in selected,
                            onClick = { selected = toggleTag(selected, tag) },
                            label = { Text(tag) },
                        )
                    }
                }
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it.take(MAX_TAG_LENGTH) },
                    label = { Text("Other") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { addTyped() }),
                    trailingIcon = {
                        TextButton(onClick = addTyped, enabled = typed.isNotBlank()) { Text("Add") }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            // A typed-but-not-added tag is saved too rather than silently dropped.
            TextButton(onClick = { onSave(addTypedTag(selected, typed)) }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
