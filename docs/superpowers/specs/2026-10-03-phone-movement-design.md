# Phone-on-the-bed movement for BLE sleep stages

## Context

BLE mode predicts sleep stages from heart rate and HRV only: the Heart Rate Service carries no movement, so
`BleSensorDataSource` passes `movement = null` to `SleepStagePredictor` (#7) and BLE mode can never predict
AWAKE. This adds an optional, real movement signal from the phone's own accelerometer, for people who sleep with
the phone on the bed. Stage is predicted inside each data source, so `SensorReading` does not change.

## Scope

In scope:
- A pure `MovementScoreCalculator` (accelerometer samples → 0..1 movement score).
- `PhoneMotionMonitor`: registers the accelerometer inside `SleepTrackingService` while a session runs.
- A singleton `PhoneMovement` holder that the service writes and `BleSensorDataSource` reads.
- One new setting, `phoneMovementEnabled` (default **off**), and one Settings toggle row.

Out of scope: a strap's own accelerometer (vendor protocols, needs hardware to test); persisting movement per
reading; any restlessness metric or new screen; Simulated mode (it generates its own movement); a calibration UI.

## Design

### 1. Movement score (pure, unit-tested, `tracking/MovementScoreCalculator.kt`)

`score(samples: List<AccelSample>): Float?` where `AccelSample(timestampMillis, x, y, z)` in m/s².
- Per sample, deviation = `|‖(x,y,z)‖ − 9.81|` (orientation-independent: only change from gravity counts).
- Score = `clamp(maxDeviation over the samples / FULL_SCALE_DEVIATION, 0, 1)`; `FULL_SCALE_DEVIATION = 2.0` m/s²,
  so the predictor's existing AWAKE threshold (`movement > 0.8`) means a peak deviation above 1.6 m/s²
  (rolling over, picking the phone up) within the window, while breathing and a still phone score about 0.
- Returns `null` for an empty sample list (nothing measured). Non-finite samples are ignored.
- Window and constants are named `const val`s: `WINDOW_MILLIS = 30_000`, `FULL_SCALE_DEVIATION = 2.0f`.
  They are first guesses and need tuning against real nights (see Risks).

### 2. Sensor listener (`tracking/PhoneMotionMonitor.kt`, Android-bound)

- Wraps `SensorManager` and `Sensor.TYPE_ACCELEROMETER`; `start(onScore)` / `stop()`.
- Samples at `SENSOR_DELAY_NORMAL` with a 5 s `maxReportLatencyUs` (batched, fewer wake-ups); keeps a rolling
  30 s buffer; every 5 s computes the score with `MovementScoreCalculator` and publishes it.
- Does nothing (no registration) when there is no accelerometer; no new permission is needed
  (rate is far below the 200 Hz cap that needs `HIGH_SAMPLING_RATE_SENSORS`).

### 3. Shared holder (`tracking/PhoneMovement.kt`, `@Singleton`)

- `publish(score: Float?, nowMillis: Long)` and `current(nowMillis: Long): Float?`.
- `current` returns `null` when nothing was published, the last value was `null`, or it is older than
  `STALE_AFTER_MILLIS = 60_000` — so a dead monitor can never leave a stale AWAKE/quiet reading behind.

### 4. Wiring

- `SleepTrackingService`: in `onStartCommand`, next to the noise monitor, start `PhoneMotionMonitor` only when
  `settings.phoneMovementEnabled` is true **and** `settings.dataSourceMode == BLE`; on stop/destroy call `stop()` and
  `PhoneMovement.publish(null, now)`. Same scope and lifecycle rules as `NoiseMonitor` (it is cancelled with the
  service scope; finalize stays on the application scope, untouched).
- `BleSensorDataSource`: inject `PhoneMovement` and use `movement = phoneMovement.current(now)` instead of `null`
  when predicting each stage. Heart-rate/HRV logic is unchanged.
- `SettingsRepository`/`Settings`/`SettingsStore`: add `phoneMovementEnabled: Boolean = false`, a
  `StateFlow<Boolean>`, `setPhoneMovementEnabled`, persisted by `PrefsSettingsStore` (unknown/missing → false).
  Follow the `amoledBlack` setting exactly.
- Settings UI: one toggle row, "Phone on the bed", with the subtitle "Uses the phone's motion sensor to spot
  waking; only in BLE mode". Shown only when the BLE source is selected; wired through the existing
  `SettingsContract` intent/state pattern (no logic in the Composable).
- `CLAUDE.md`: describe the new classes, the toggle, and that the thresholds are untuned.

### 5. Behaviour table

| Situation | movement passed to the predictor |
| --- | --- |
| Toggle off, or Simulated mode | `null` (unchanged from today) |
| Toggle on, BLE, monitor running, score fresh | the 0..1 score |
| Toggle on, but no accelerometer / monitor stopped / score older than 60 s | `null` |

## Testing

- Unit: `MovementScoreCalculator` (still phone → ~0; breathing-sized noise → < 0.1; a 3 m/s² spike → 1.0 and
  above the AWAKE threshold; a 1.0 m/s² bump → 0.5; empty → null; NaN ignored; orientation independence by
  rotating the same samples); `PhoneMovement` (fresh, stale, null, never published); `SettingsRepository`/
  `PrefsSettingsStore` persistence and default-false; `SettingsViewModel` toggle intent.
- Existing `SleepStagePredictorTest` already covers movement above/below the threshold and `null`.
- On device (Redmi, Android 13): with the toggle on and a fake strap connected, the debug log shows scores near 0
  with the phone still, rising when the phone is moved; with the toggle off nothing is registered.
- Not unit-testable (Android-bound): `PhoneMotionMonitor` and the BLE wiring — verified on the device only.

## Risks and limits

- **Thresholds are guesses.** `FULL_SCALE_DEVIATION`, the 30 s window and the 0.8 AWAKE threshold were chosen
  without real sleep data. Ship with a debug log of the score so real nights can show whether a roll-over reads as
  AWAKE too often (a soft mattress dampens motion; a partner moving also reads). Tuning is a follow-up.
- It measures **bed movement, not the wearer's**. It only helps when the phone is on the bed; otherwise leave it off.
- **Battery:** accelerometer at normal rate with batching while a session runs; the BLE strap already keeps the CPU
  awake, so the added cost should be small but is unmeasured.
- A rolling AWAKE from a single large shift can flicker a stage for up to one window; smoothing is out of scope.
