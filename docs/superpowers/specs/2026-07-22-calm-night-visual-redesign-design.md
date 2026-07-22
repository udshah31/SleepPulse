# Calm Night Visual Redesign

## Problem

The app's current visual identity is inconsistent: `Theme.kt` auto-switches
between four separate color schemes (Morning/Day/Evening/Night) based on
wall-clock hour, plus optionally defers to Material You dynamic color on
Android 12+. The user finds the overall look and feel unsatisfying and wants
one deliberate, consistent identity instead of a shifting one. Separately,
the Settings screen has a real layout bug: its `Column` has no vertical
scroll, so with 11+ rows of content it overflows and visually overlaps the
bottom nav bar.

## Decisions (from brainstorming)

- **Style direction**: "Calm Night" — deep navy/indigo dark theme, soft
  glowing accent circles, minimal chrome. (Chosen over Aurora Gradient,
  Minimal Mono, and Soft Pastel Wellness.)
- **Theme scope**: dark-only. No light-theme variant. The app always renders
  Calm Night regardless of time of day.
- **Accent color**: indigo/violet glow (`#7C8BFF` family) — not cyan/teal,
  not warm amber.
- **Theming system change**: remove both the time-of-day auto-switch
  (Morning/Day/Evening/Night schemes) and Material You dynamic color entirely.
  One fixed `ColorScheme` for the whole app.

## Color Palette

| Token | Value | Usage |
|---|---|---|
| Background | `#0B0F22` → `#111633` (vertical gradient) | Screen background |
| Surface | `#151B3D` | Cards, list rows |
| Surface variant (dim) | `#1C2248` | Dividers, inactive pills, off-state switch track |
| Primary / Accent | `#7C8BFF` | Score ring, selected state, primary buttons, active nav icon |
| Primary dim | `#3A4180` / `#5A63B8` | Secondary ring segments, unselected-but-visible accents |
| On-primary (text on accent bg) | `#0B0F22` | Text/icons drawn on top of accent-colored surfaces |
| Text primary | `#E4E6F5` | Headlines, values |
| Text secondary | `#8B93C4` | Labels, captions, timestamps |
| Text tertiary / disabled | `#5C6489` | Inactive nav labels |
| Success | `#5FD98A` | Positive trend arrows |
| Danger | `#FF7A7A` | Negative trend arrows, falling-HRV/rising-RHR insight icons |
| Warning | `#F6C358` | Caffeine-correlation insight icon |

AMOLED Dark Mode (existing setting) continues to override `background` and
`surface` to pure black when enabled — this still composes with Calm Night
since it's already a dark theme.

## Screen-by-screen changes

**Dashboard** — circular sleep-score gauge gets a soft outer glow
(`box-shadow`-equivalent) in the accent color; ring uses primary/primary-dim
two-tone arc. HR/HRV live metrics move into two side-by-side surface cards.
Sleep-stage bar becomes a single segmented rounded bar in tiered accent
shades. Primary CTA ("Start Wind-Down") is a filled accent button with dark
text for contrast.

**History** — nightly rows become surface cards with the score in a small
ringed circle (ring color = accent, dimmed per how old the entry is),
trend arrows in success/danger colors, secondary metadata in text-secondary.

**Recovery Coach** — readiness score gets its own gradient hero card
(distinct two-tone indigo gradient, not flat surface) to signal it's the
headline number. Each of the 5 insights becomes a surface-card row with a
leading glyph colored by severity (danger/warning/accent).

**Alarm** — large time display in text-primary, wake-window subtext in
text-secondary. Smart wake-up toggle and wake-window-size segmented control
both live in surface cards, consistent with Settings' card grouping.

**Settings** — **structural fix**, not just a color change: replace the bare
`RadioButton` + `Text` `Row`s with grouped surface-card sections (one card
per setting group: Data Source, Temperature Unit, Target Bedtime, Smart
Wake-up Alarm, Display), each row showing a checkmark-in-circle for the
selected option instead of a `RadioButton`. Add `Modifier.verticalScroll` to
the outer `Column` so content scrolls inside the screen instead of
overflowing into the bottom nav — this is what fixes the overlap bug.
Selected rows get a subtly accent-tinted background (not just the
checkmark) so the current choice is scannable at a glance. The disabled
"BLE sensor (coming soon)" row keeps ~50% opacity and gains a small "Soon"
badge for clarity.

## Tweaks folded in from mockup review

1. Selected-row background tint (Data Source / Bedtime / Alarm cards), not
   just a checkmark, to make the current selection scannable.
2. Slightly more vertical spacing above each section label so groups read as
   distinct rather than one continuous list.
3. "Soon" badge next to the disabled BLE row, in addition to the existing
   opacity dim.
4. Switch track uses dim accent (`#1C2248`) when off vs. bright accent
   (`#7C8BFF`) when on, for clearer on/off contrast.
5. Dashboard score ring gets a subtle arc-reveal animation on load (nice-to-
   have; not blocking, can be deferred if it complicates `SleepScoreGauge.kt`
   more than expected).

## Architecture impact

- `ui/theme/Theme.kt`: delete `MorningColors`, `DayColors`, `EveningColors`,
  the `hour`-based `when` block, and the `dynamicDarkColorScheme`/
  `dynamicLightColorScheme` branch. Replace `DarkColors`/`LightColors` with a
  single `CalmNightColors` `darkColorScheme` using the palette above.
  `SleepPulseTheme(...)` becomes a thin wrapper: apply `CalmNightColors`,
  then apply the existing AMOLED-black override when `amoledBlack` is true.
  The `darkTheme`/`dynamicColor` parameters are removed since there is only
  one theme now.
- `ui/settings/SettingsScreen.kt`: restructure from flat `RadioButton` rows
  into card-grouped composables (e.g. a small local `SettingsCard` /
  `SettingsOptionRow` composable reused across the five sections) and add
  `verticalScroll`.
- Other screens (`ui/dashboard`, `ui/history`, `ui/settings` companions)
  get styling-only changes — surface/card colors, ring colors, spacing —
  no changes to their ViewModel/Contract logic, since none of this affects
  state shape or business logic.
- No changes to `data/`, `di/`, or MVI contracts anywhere — this is a
  presentation-layer-only redesign.

## Testing

No new unit-test surface (pure UI styling + one layout fix). Existing
ViewModel/Contract tests are unaffected since Intent/State shapes don't
change. Verification is visual: build, install, and walk all five tabs plus
the Settings scroll fix on a device/emulator, per the existing
`superpowers:verification-before-completion` expectations for UI work.
