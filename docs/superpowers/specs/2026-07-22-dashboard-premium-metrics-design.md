# Dashboard premium metrics redesign

## Context

Competitive review of Eight Sleep and Whoop's Play Store listings (all screenshots
in both apps' galleries) surfaced a consistent premium-feeling pattern: one
dominant number in a color-coded ring/arc, a plain-English insight sentence
naming the specific factor behind that number, and per-metric rows showing a
value alongside a trend arrow versus a rolling baseline — all on a borderless,
thin-divider layout instead of Material cards.

SleepPulse's Dashboard already implements the color-coded gauge
(`SleepScoreGauge.kt`) and a 7-day rolling baseline comparison
(`RecoveryScoreCalculator`), but that baseline is only used to produce one
aggregate Recovery Score — it isn't surfaced per-metric, and the guidance
sentence is a static 4-way lookup by tier rather than naming which signal
(HRV or resting HR) actually drove the score.

## Goals

1. Show HR and HRV each as a row with an icon, label, value, and a trend arrow
   (▲/▼) with the delta versus a 7-day rolling average — replacing the current
   flat "Heart rate 64 bpm · HRV 52 ms" text line.
2. Extend the Recovery guidance sentence to name the dominant deviating factor
   (HRV vs. resting HR) instead of a static per-tier message.
3. Restyle `RecoveryScoreCard` from a Material `Card` (bordered/elevated box) to
   a borderless section with a thin top divider, consistent with the
   competitors' "no boxes, just spacing" look. The new metric rows live inside
   this restyled section, under the score and guidance text.

Out of scope: gauge color banding (already matches the target 3-band
Red/Amber/Green scheme, no change needed), and any changes to History, Alarm,
or Settings screens — this is a Dashboard-only pass.

## Design

### Data: per-metric 7-day baseline

Add a pure function alongside `RecoveryScoreCalculator`, e.g.
`MetricBaseline.compute(lastNight: NightlySummary, baseline: List<NightlySummary>): MetricBaselineResult`
returning the 7-day average HR and HRV plus each metric's signed percent delta
from `lastNight`. This mirrors the existing HRV/RHR deviation math already in
`RecoveryScoreCalculator.score()` — that calculation is reused/extracted rather
than duplicated, since both need "deviation from rolling baseline" for HRV and
RHR.

`DashboardState` gains a nullable `metricBaseline: MetricBaselineResult?` field
(null while baseline is still building, same convention as `recoveryResult`),
populated by `DashboardViewModel` from the same `NightlySummary` history it
already loads for `RecoveryScoreCalculator`.

### Dominant-factor guidance sentence

`RecoveryScoreCalculator.score()` already computes `hrvDeviation` and
`rhrDeviation` internally but discards them after weighting into the score.
Return them as part of `RecoveryResult` (new fields `hrvDeviation: Double`,
`rhrDeviation: Double`), and replace the static `guidanceFor(tier)` lookup with
a function that:
- picks whichever of the two deviations has the larger absolute magnitude as
  the "dominant factor",
- names it in a template sentence combined with the tier, e.g.:
  - "Your HRV is {X}% above your weekly average, suggesting strong recovery."
  - "Your resting heart rate is {X}% above your weekly average — consider an easier day."
- falls back to the existing static tier message only if both deviations are
  within a small neutral threshold (e.g. ±3%), to avoid manufacturing a
  spurious "why" when nothing actually moved.

This stays fully offline/deterministic — no new dependencies.

### UI: `MetricRow` composable

New `ui/components/MetricRow.kt`:
- Row layout: leading icon, label (small, muted), trailing value + small
  colored ▲/▼ arrow + delta percentage.
- Arrow color: green when the deviation is favorable for that metric
  (higher HRV, lower RHR), red/amber when unfavorable — reusing the existing
  `RecoveryGreen`/`CautionAmber`/`AlertCoral` theme tokens, not new colors.
- No card background — sits directly on the screen background, separated from
  the next row by a single 1dp divider (new `CalmNightDivider` color token if
  one doesn't already exist, else reuse `CalmNightTextSecondary` at low alpha).

`DashboardScreen` replaces the current
`Text("Heart rate ${reading.heartRateBpm} bpm  ·  HRV ...")` line with two
`MetricRow`s (Heart rate, HRV), rendered only when `state.metricBaseline` is
non-null; otherwise the existing raw values still show as plain text (same
graceful-degradation pattern already used for `recoveryResult == null`).

### `RecoveryScoreCard` restyle

Replace the `Card { ... }` wrapper with a `Column` plus a `HorizontalDivider`
(or a 1dp `Box` in the existing divider color) above it, dropping the
Material elevation/surface-tint background. Internal padding and text styles
stay as-is; the two new `MetricRow`s are added inside this same `Column`,
after the existing guidance `Text`.

### Testing

- Unit tests for the new baseline-delta computation (mirrors existing
  `RecoveryScoreCalculatorTest` patterns already in the repo) — covers: no
  baseline yet (null), favorable deviation, unfavorable deviation, and the
  neutral-threshold fallback for the guidance sentence.
- No new instrumented/UI tests — consistent with the project's current phase
  (deferred per README/CLAUDE.md).

## Error handling

None of this introduces new failure modes: all inputs are already-validated
`NightlySummary` values from Room via the existing `SleepRepository` path. The
only new "empty" case (fewer than `MIN_BASELINE_NIGHTS` nights) is handled the
same way the existing `RecoveryResult? == null` case is — a graceful fallback
to the current plain-text display, not an error state.
