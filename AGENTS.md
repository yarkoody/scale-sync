# Scale Sync: agent instructions

Shared by Codex and Claude Code. Read `PLAN.md` before substantial work and keep it current.

## What the app is

An Android app (Kotlin, Compose, GPLv3) that reads bathroom scales **only from their BLE
advertisements** and writes weigh-ins to Health Connect. It never connects to a scale, has no
account, no cloud and **no INTERNET permission**. Keep it that way: no network libraries, no
analytics, no crash reporters. Anything that leaves the phone goes through the Android share
sheet, started by the user.

## Layout

- `core/` is pure Kotlin (no Android imports) so it runs in JVM unit tests:
  - `Advertisement.kt` plain-data advertisement; `ScaleDriver.kt` the driver interface,
    `ScaleFrame`, capabilities; `Drivers.kt` the registry.
  - `drivers/` one file per scale family. A driver only implements `matches(ad)` and
    `parse(ad)`; it must never throw (the registry wraps it, but don't rely on that).
  - `WeighInDetector.kt`, `BodyComposition.kt` are generic: they never look at driver ids.
- `ble/` Android scanning; `data/` storage (SharedPreferences, CSV history, diagnostic capture);
  `health/` Health Connect; `ui/` Compose.

## Adding a scale driver

1. Add `core/drivers/<Vendor><Model>Driver.kt` and register it in `Drivers.all`.
2. Mark it `SupportLevel.EXPERIMENTAL` unless the maintainer has tested it on the real scale.
   Declare only the `Field`s the advertisement really carries. Set `vendorFormula` only when the
   vendor's impedance formula is implemented; otherwise impedance gets bodymiscale's formulas.
3. Unit tests from **real captures** (openScale tests/issues, BLE Scale Sync tests, or a user's
   diagnostic capture), with a comment saying where each capture comes from. Test that the
   driver does not claim other drivers' captures.
4. Add a row to `docs/PROVENANCE.md` (ported from which file of which project, or written from
   protocol notes) and to the README's supported-scales table.
5. `matches` must not overlap another driver's real advertisements; if it can, the pairing UI
   asks the user, which is acceptable but should be rare.

## Rules that matter

- Weight-only scales: write weight and BMR only to Health Connect; never write an estimated
  body fat. BMI is shown in the app.
- The existing Mi Body Composition Scale behaviour is the reference; its tests must stay green.
- Driver ids (`ScaleDriver.id`) are persisted. Never rename one after it has shipped.
- Provenance matters: an iPhone port may need a non-GPL core. Record the source of every
  ported file in `docs/PROVENANCE.md`.

## Build and test

```
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ANDROID_HOME=/opt/homebrew/share/android-commandlinetools \
  ./gradlew testDebugUnitTest assembleDebug
```

compileSdk 36; pin library versions that fit it rather than installing new SDK packages.
