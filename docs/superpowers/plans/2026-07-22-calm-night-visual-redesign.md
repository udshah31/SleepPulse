# Calm Night Visual Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace SleepPulse's time-of-day/dynamic-color theming with one fixed dark "Calm Night" theme (indigo-violet accent on deep navy), and fix the Settings screen's scroll-overflow/overlap bug by restructuring it into grouped cards.

**Architecture:** `ui/theme/Theme.kt` is the single source of Material3 `ColorScheme` colors; every other screen (`Dashboard`, `History`, `Recovery`, `Alarm`) already reads colors via `MaterialTheme.colorScheme.*` or the named constants (`SleepIndigo`, `RecoveryGreen`, `CautionAmber`, `AlertCoral`) exported from `Theme.kt`, so retinting those two sources re-skins the whole app without touching those four screens' code. `ui/settings/SettingsScreen.kt` is the one screen that needs structural changes: replacing flat `RadioButton` rows with card-grouped rows and adding vertical scroll.

**Tech Stack:** Kotlin, Jetpack Compose, Material3, Hilt (no new dependencies).

## Global Constraints

- Dark-only theme — no light-theme variant, no `isSystemInDarkTheme()` branching, no Material You (`dynamicDarkColorScheme`/`dynamicLightColorScheme`).
- Background gradient: `#0B0F22` → `#111633`. Surface: `#151B3D`. Surface-dim (dividers/inactive pills/off-switch-track): `#1C2248`.
- Accent (primary): `#7C8BFF`. On-accent text/icon color: `#0B0F22`.
- Text primary: `#E4E6F5`. Text secondary: `#8B93C4`. Text tertiary/disabled: `#5C6489`.
- Success: `#5FD98A`. Danger: `#FF7A7A`. Warning: `#F6C358`.
- AMOLED Dark Mode setting must still override `background`/`surface` to pure black when enabled — do not remove that behavior.
- No changes to any `*Contract.kt`, `*ViewModel.kt`, or `data/` files — this is presentation-layer only.

---

### Task 1: Rewrite Theme.kt to a single Calm Night color scheme

**Files:**
- Modify: `app/src/main/java/com/sleeppulse/app/ui/theme/Theme.kt`
- Test: `app/src/test/java/com/sleeppulse/app/ui/theme/SleepPulseThemeTest.kt` (new)

**Interfaces:**
- Produces: `SleepPulseTheme(amoledBlack: Boolean = false, content: @Composable () -> Unit)` — the `darkTheme` and `dynamicColor` parameters are removed. Callers: `MainActivity.kt:26` already calls it as `SleepPulseTheme(amoledBlack = amoledBlack) { ... }`, so no caller change needed.
- Produces: color constants `SleepIndigo = Color(0xFF7C8BFF)`, `RecoveryGreen = Color(0xFF5FD98A)`, `CautionAmber = Color(0xFFF6C358)`, `AlertCoral = Color(0xFFFF7A7A)` — same names as before (so `DashboardScreen.kt`, `SleepScoreGauge.kt` keep compiling unchanged), new hex values matching the Calm Night palette.
- Produces: `CalmNightBackground = Color(0xFF0B0F22)`, `CalmNightSurface = Color(0xFF151B3D)`, `CalmNightSurfaceDim = Color(0xFF1C2248)`, `CalmNightTextSecondary = Color(0xFF8B93C4)`.

Since `SleepPulseThemeTest` needs a Compose UI test harness that doesn't exist yet in this project's unit-test setup (only JVM unit tests exist per `app/src/test`), skip a Compose-render test and instead write a plain JVM test that asserts the exported color constants have the exact expected hex values — that's the only thing in this file that's meaningfully unit-testable without Robolectric/instrumentation.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/sleeppulse/app/ui/theme/SleepPulseThemeTest.kt`:

```kotlin
package com.sleeppulse.app.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class SleepPulseThemeTest {

    @Test
    fun `accent color matches Calm Night palette`() {
        assertEquals(Color(0xFF7C8BFF), SleepIndigo)
    }

    @Test
    fun `success color matches Calm Night palette`() {
        assertEquals(Color(0xFF5FD98A), RecoveryGreen)
    }

    @Test
    fun `warning color matches Calm Night palette`() {
        assertEquals(Color(0xFFF6C358), CautionAmber)
    }

    @Test
    fun `danger color matches Calm Night palette`() {
        assertEquals(Color(0xFFFF7A7A), AlertCoral)
    }

    @Test
    fun `background gradient start matches Calm Night palette`() {
        assertEquals(Color(0xFF0B0F22), CalmNightBackground)
    }

    @Test
    fun `surface color matches Calm Night palette`() {
        assertEquals(Color(0xFF151B3D), CalmNightSurface)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.sleeppulse.app.ui.theme.SleepPulseThemeTest"`
Expected: FAIL — compile error, `CalmNightBackground`/`CalmNightSurface` unresolved and `SleepIndigo`/`RecoveryGreen`/`CautionAmber`/`AlertCoral` still hold the old hex values.

- [ ] **Step 3: Rewrite Theme.kt**

Replace the full contents of `app/src/main/java/com/sleeppulse/app/ui/theme/Theme.kt` with:

```kotlin
package com.sleeppulse.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Calm Night palette
val SleepIndigo = Color(0xFF7C8BFF)
val SleepIndigoDark = Color(0xFF5A63B8)
val RecoveryGreen = Color(0xFF5FD98A)
val CautionAmber = Color(0xFFF6C358)
val AlertCoral = Color(0xFFFF7A7A)

val CalmNightBackground = Color(0xFF0B0F22)
val CalmNightBackgroundEnd = Color(0xFF111633)
val CalmNightSurface = Color(0xFF151B3D)
val CalmNightSurfaceDim = Color(0xFF1C2248)
val CalmNightTextPrimary = Color(0xFFE4E6F5)
val CalmNightTextSecondary = Color(0xFF8B93C4)
val CalmNightTextTertiary = Color(0xFF5C6489)

private val CalmNightColors = darkColorScheme(
    primary = SleepIndigo,
    onPrimary = CalmNightBackground,
    secondary = RecoveryGreen,
    tertiary = CautionAmber,
    error = AlertCoral,
    background = CalmNightBackground,
    surface = CalmNightSurface,
    surfaceVariant = CalmNightSurfaceDim,
    onBackground = CalmNightTextPrimary,
    onSurface = CalmNightTextPrimary,
    onSurfaceVariant = CalmNightTextSecondary,
)

@Composable
fun SleepPulseTheme(
    amoledBlack: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (amoledBlack) {
        CalmNightColors.copy(
            background = Color.Black,
            surface = Color.Black,
        )
    } else {
        CalmNightColors
    }

    MaterialTheme(colorScheme = colorScheme, content = content)
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.sleeppulse.app.ui.theme.SleepPulseThemeTest"`
Expected: PASS — 6 tests green.

- [ ] **Step 5: Run the full unit test suite to check nothing else broke**

Run: `./gradlew :app:test`
Expected: BUILD SUCCESSFUL, all existing tests still pass (this change only touches color values and theme wiring, no ViewModel/Contract logic).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/sleeppulse/app/ui/theme/Theme.kt app/src/test/java/com/sleeppulse/app/ui/theme/SleepPulseThemeTest.kt
git commit -m "feat: replace time-based/dynamic theming with fixed Calm Night dark theme"
```

---

### Task 2: Restructure SettingsScreen into scrollable, card-grouped sections

**Files:**
- Modify: `app/src/main/java/com/sleeppulse/app/ui/settings/SettingsScreen.kt`

**Interfaces:**
- Consumes: `SettingsViewModel.state: StateFlow<SettingsState>` (unchanged), `SettingsIntent.SetDataSource/SetTemperatureUnit/SetTargetBedtime/SetTargetWakeup/SetAmoledBlack/SetSelectedBleDevice` (unchanged — see existing `SettingsContract.kt`), `DataSourceMode.entries`, `TemperatureUnit.entries` (unchanged).
- Consumes new theme tokens from Task 1: `CalmNightSurface`, `CalmNightSurfaceDim`, `SleepIndigo`, `CalmNightTextSecondary`, `CalmNightBackground` (as `onPrimary` text color).
- Produces: no new public API — `SettingsScreen(onNavigateToScan, selectedBleDeviceLabel, viewModel)` signature is unchanged, so `SleepPulseApp.kt`'s nav graph call site needs no changes.

This screen has no existing test file and its logic (which intent fires on which click) is unchanged — only the layout markup changes — so no new test is added here; verification is the manual device walk in Task 3.

- [ ] **Step 1: Replace the body of SettingsScreen.kt**

Replace the full contents of `app/src/main/java/com/sleeppulse/app/ui/settings/SettingsScreen.kt` with:

```kotlin
package com.sleeppulse.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sleeppulse.app.ui.theme.CalmNightSurface
import com.sleeppulse.app.ui.theme.CalmNightTextSecondary

@Composable
fun SettingsScreen(
    onNavigateToScan: () -> Unit = {},
    selectedBleDeviceLabel: String? = null,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(selectedBleDeviceLabel) {
        selectedBleDeviceLabel?.let {
            viewModel.onIntent(SettingsIntent.SetSelectedBleDevice(it))
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        Text(text = "Settings", style = MaterialTheme.typography.headlineSmall)

        SettingsSection(title = "Data source") {
            DataSourceMode.entries.forEach { mode ->
                val enabled = mode != DataSourceMode.BLE
                SettingsOptionRow(
                    label = mode.label(),
                    selected = state.dataSourceMode == mode,
                    enabled = enabled,
                    badge = if (!enabled) "Soon" else null,
                    onClick = { viewModel.onIntent(SettingsIntent.SetDataSource(mode)) },
                )
            }
            if (state.dataSourceMode == DataSourceMode.BLE) {
                Button(onClick = onNavigateToScan, modifier = Modifier.padding(top = 8.dp)) {
                    Text("Scan for device")
                }
                state.selectedBleDeviceLabel?.let { label ->
                    Text(text = "Selected: $label", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        SettingsSection(title = "Temperature unit") {
            TemperatureUnit.entries.forEach { unit ->
                SettingsOptionRow(
                    label = unit.label(),
                    selected = state.temperatureUnit == unit,
                    onClick = { viewModel.onIntent(SettingsIntent.SetTemperatureUnit(unit)) },
                )
            }
        }

        SettingsSection(title = "Target bedtime") {
            val bedtimeOptions = listOf(
                Pair(21, 30) to "9:30 PM",
                Pair(22, 30) to "10:30 PM",
                Pair(23, 30) to "11:30 PM",
            )
            bedtimeOptions.forEach { (time, label) ->
                SettingsOptionRow(
                    label = label,
                    selected = state.targetBedtimeHour == time.first && state.targetBedtimeMinute == time.second,
                    onClick = { viewModel.onIntent(SettingsIntent.SetTargetBedtime(time.first, time.second)) },
                )
            }
        }

        SettingsSection(title = "Smart wake-up alarm") {
            val wakeupOptions = listOf(
                Triple(6, 30, 30) to "6:30 AM (30m window)",
                Triple(7, 0, 30) to "7:00 AM (30m window)",
                Triple(7, 30, 30) to "7:30 AM (30m window)",
            )
            wakeupOptions.forEach { (time, label) ->
                SettingsOptionRow(
                    label = label,
                    selected = state.targetWakeupHour == time.first && state.targetWakeupMinute == time.second,
                    onClick = {
                        viewModel.onIntent(SettingsIntent.SetTargetWakeup(time.first, time.second, time.third))
                    },
                )
            }
        }

        SettingsSection(title = "Display") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp, horizontal = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "AMOLED Dark Mode", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = "Pure black background to save battery",
                        style = MaterialTheme.typography.bodySmall,
                        color = CalmNightTextSecondary,
                    )
                }
                Switch(
                    checked = state.amoledBlack,
                    onCheckedChange = { viewModel.onIntent(SettingsIntent.SetAmoledBlack(it)) },
                )
            }
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = CalmNightTextSecondary,
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(CalmNightSurface),
            content = content,
        )
    }
}

@Composable
private fun SettingsOptionRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true,
    badge: String? = null,
) {
    val rowBackground = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else Color.Transparent
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .background(rowBackground)
            .padding(vertical = 12.dp, horizontal = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else CalmNightTextSecondary,
            )
            if (badge != null) {
                Text(
                    text = badge,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
        if (selected) {
            Text(
                text = "✓",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

private fun DataSourceMode.label(): String = when (this) {
    DataSourceMode.SIMULATED -> "Simulated"
    DataSourceMode.BLE -> "BLE sensor (coming soon)"
}

private fun TemperatureUnit.label(): String = when (this) {
    TemperatureUnit.CELSIUS -> "Celsius"
    TemperatureUnit.FAHRENHEIT -> "Fahrenheit"
}
```

Note: `SettingsSection`'s `content` parameter type `ColumnScope` requires importing `androidx.compose.foundation.layout.ColumnScope` — add that import alongside the others above.

- [ ] **Step 2: Add the missing ColumnScope import**

In the same file, add to the import block:

```kotlin
import androidx.compose.foundation.layout.ColumnScope
```

- [ ] **Step 3: Build to verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL. If it fails on the `ColumnScope` import ordering, Kotlin import order doesn't matter for compilation — re-check for a typo instead.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/sleeppulse/app/ui/settings/SettingsScreen.kt
git commit -m "fix: restructure Settings into scrollable card-grouped sections, fixing bottom-nav overlap"
```

---

### Task 3: Build, install, and visually verify on device/emulator

**Files:** none (verification only)

**Interfaces:** none

- [ ] **Step 1: Assemble and install the debug build**

Run: `./gradlew :app:installDebug`
Expected: `BUILD SUCCESSFUL`, `Installed on 1 device.` If a physical device is connected via `adb`, it installs there; otherwise it targets whatever's the sole connected `adb` device/emulator — run `adb devices -l` first if it's ambiguous which target it picked.

- [ ] **Step 2: Launch the app and walk every tab**

```bash
adb shell am start -n com.sleeppulse.app/.MainActivity
adb shell input tap 212 1130   # Dashboard
adb shell screencap -p /sdcard/dashboard.png
adb shell input tap 359 1130   # Recovery
adb shell screencap -p /sdcard/recovery.png
adb shell input tap 212 1130   # History (shares tab index with Dashboard per bottom nav)
adb shell screencap -p /sdcard/history.png
adb shell input tap 654 1130   # Settings
adb shell screencap -p /sdcard/settings.png
adb pull /sdcard/dashboard.png .
adb pull /sdcard/recovery.png .
adb pull /sdcard/history.png .
adb pull /sdcard/settings.png .
```

Expected: all four screenshots show the deep-navy Calm Night background and indigo-violet (`#7C8BFF`) accents instead of the old multi-theme colors. On the Settings screenshot specifically: scroll to the bottom of the screen (`adb shell input swipe 400 1000 400 300`) and re-screenshot — confirm the "Display / AMOLED Dark Mode" row is fully visible above the bottom nav with no overlap, proving the scroll fix works.

- [ ] **Step 3: Run the full test suite one more time**

Run: `./gradlew :app:test`
Expected: BUILD SUCCESSFUL, same pass count as before Task 1 plus the 6 new theme tests.

- [ ] **Step 4: Clean up screenshot artifacts and commit is already done**

```bash
rm -f dashboard.png recovery.png history.png settings.png
adb shell rm /sdcard/dashboard.png /sdcard/recovery.png /sdcard/history.png /sdcard/settings.png
```

No commit needed for this task — it's verification of Tasks 1–2, which are already committed.

---

## Out of scope (deferred, not part of this plan)

- The full card-based hero layouts sketched in the mockups for Dashboard/History/Recovery/Alarm (gradient hero card for Recovery readiness, ringed history rows, etc.) are cosmetic upgrades beyond a palette swap. Since those screens already read colors from `MaterialTheme.colorScheme`/the named constants, they get the new Calm Night hues "for free" from Task 1, but their layout structure (plain `Text`/`Row` vs. the mockups' card wrapping) is unchanged. If the user wants the exact card layouts from the mockups on those four screens too, that's a follow-on plan — flag it back to brainstorming as a separate spec rather than folding it in silently here.
- Tweak #5 from the mockup review (animated arc-reveal on the Dashboard score ring) is explicitly deferred per the design spec — nice-to-have, not blocking.
