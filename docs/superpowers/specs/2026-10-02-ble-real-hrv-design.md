# Real HRV from BLE heart-rate straps

## Context

`BleSensorDataSource` reads only the bpm from the Heart Rate Measurement characteristic
(`0x2A37`) and fills `hrvMillis` with a fixed `50.0` (and movement with a fixed `0.5f`). Those fake
values reach the sleep score, the nightly averages, recovery and the dashboard; only the Health
Connect HRV write is guarded against them (skipped in BLE mode).

The same characteristic can carry RR-intervals (flags bit 4, UINT16 each, units of 1/1024 s). Many
chest straps send them; many watches and rings do not. This spec makes BLE mode report **real HRV
(RMSSD) when the device provides RR-intervals, and "unknown" otherwise** — never a made-up number.

## Scope

In scope:
- Parse RR-intervals from `0x2A37`; compute a rolling RMSSD.
- Make HRV nullable end to end (`null` = unknown), with a Room v4 → v5 migration.
- Every consumer of HRV tolerates `null` (scoring, recovery, trends, UI, export, watch, Health Connect).

Out of scope (separate work): real sleep-stage detection and real movement from BLE; vendor-specific
HRV characteristics; any network/AI service (HRV is arithmetic and stays on-device).

## Design

### 1. Parsing and RMSSD (pure, unit-tested, new files in `data/source/`)

- `HeartRateMeasurement.parse(bytes): HeartRateMeasurement?` — pure function over the characteristic
  bytes. Replaces `parseHeartRate`. Returns `bpm` and `rrIntervalsMillis: List<Double>`; handles the
  UINT8/UINT16 bpm flag, the energy-expended field (bit 3, 2 bytes, skipped) and the RR field
  (bit 4, 1/1024 s → ms). A truncated packet yields `null`, not a crash.
- `RmssdCalculator` — keeps a rolling window (60 s) of RR-intervals; `add(timestampMillis, rrMs)`
  and `rmssd(nowMillis): Double?`. Beats outside 300..2000 ms are dropped, and a successive pair is
  skipped if either beat was dropped. Returns `null` with fewer than 10 valid successive
  differences. Owned by `BleSensorDataSource`, reset on (re)connect.
- `BleSensorDataSource.onCharacteristicChanged` emits `hrvMillis = rmssd` (null while unknown). The
  fixed `50.0` placeholder is deleted. The predictor receives the real value or null; the fixed
  movement placeholder is left as is (out of scope).

### 2. Nullable HRV in the model

- `SensorReading.hrvMillis: Double?`, `NightlySummary.avgHrvMillis: Double?`,
  `SessionReadingEntity.hrvMillis: Double?`, `NightlySummaryEntity.avgHrvMillis: Double?`.
- `NightSummaryBuilder`: `avgHrvMillis` = mean of non-null readings; `null` if there are none.
- `SimulatedSensorDataSource` is unchanged in behaviour (always non-null).

### 3. Room migration 4 → 5

SQLite can't relax NOT NULL in place, so `MIGRATION_4_5` recreates `session_reading` and
`nightly_summary` (create new table with the v5 schema, copy rows, drop old, rename) and adds
indices exactly as the v5 schema JSON expects. `SleepPulseDatabase.VERSION = 5`, migration added to
`MIGRATIONS`, `app/schemas/.../5.json` committed.

Fake data clean-up: v4 BLE nights hold the placeholder, always exactly `50.0`. The migration sets
`hrvMillis` / `avgHrvMillis` to `NULL` where the value is exactly `50.0`. The simulated source
produces non-integer random values, so this can hit a simulated night only with negligible
probability, and wrongly nulling one just shows "—" for that night. (Reviewer: say if you would
rather keep old values untouched.)

### 4. Consumers tolerate null

| Consumer | Behaviour with unknown HRV |
| --- | --- |
| `SleepScoreCalculator` | Uses non-null readings for the HRV component; with none, the score is the HR component rescaled to 0..100 so a strap without HRV can still reach a full score |
| `SleepStagePredictor` | `hrvMillis: Long?`; HR/movement-only rules when null (never `DEEP`/`REM` on the HRV conditions) |
| `RecoveryScoreCalculator`, `RecoveryReadinessCalculator` | HRV term dropped when the night's or the baseline's HRV is unknown (baseline needs ≥3 nights with HRV, else HR-only); the existing `null`-under-3-nights rule is unchanged |
| `HrvTrendCalculator`, `MetricBaselineCalculator`, `SleepVariabilityCalculator` | Average/std-dev over non-null nights; result `null`/absent if fewer than the existing minimum |
| Dashboard, `RecoveryScoreCard`, History row | Show "—" and no HRV chart line instead of a number |
| `DataExporter` (CSV) | Empty cell |
| `WearDataClient` | Omit the `hrv` key when null |
| `HealthConnectManager.buildHrvRecords` | The existing 1..200 ms filter becomes `hrvMillis != null && in 1.0..200.0`; the "skip in BLE mode" rule is replaced by this per-reading rule, so a strap with real RR data now writes HRV to Health Connect and a strap without writes none. Placeholder values are never written |

## Testing

- Unit: `HeartRateMeasurement.parse` (8/16-bit bpm, energy field, 1 and several RR values, truncated
  packets); `RmssdCalculator` (known intervals → known RMSSD, window eviction, outlier drop,
  too-few-beats → null); each consumer's null path; `NightSummaryBuilder` with all/some/no HRV;
  `HealthConnectManager.buildHrvRecords` skips null.
- Instrumented (`SleepPulseDatabaseMigrationTest`): the generic loop covers 4 → 5 automatically;
  add a data-preservation test — rows written at v4 (including a `50.0` HRV and a normal one) are
  read at v5 with values copied and `50.0` nulled.
- On-device: a strap that sends RR-intervals (RMSSD appears) and a device that does not (HRV shows
  "—", score still computed). Device model still to be confirmed by the user.

## Risks

- Wide change (~17 files + schema). Mitigated by `Double?` making the compiler list every
  consumer, and by the migration test.
- RMSSD from a wrist/ring HR stream that sends no RR is simply unavailable; that is intended.
- Straps differ in whether they send RR while moving; the minimum-beats rule means HRV may flicker
  to unknown, which is the honest outcome.
