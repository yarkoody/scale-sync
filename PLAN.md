# Scale Sync plan

## Outcome

Scale Sync reads many brands of BLE bathroom scale from their advertisements alone (no
connection, no account, no internet permission) and writes weigh-ins to Health Connect, then
ships on Google Play. An iPhone version comes later; it may need a non-GPL core, so the origin
of every ported file is tracked in `docs/PROVENANCE.md`.

## Current phase

Multi-scale support on Android: driver layer first, then one scale family per PR. Each new
driver ships as Experimental (the maintainer owns only a Mi Body Composition Scale 2).

## Milestones

| # | Milestone | Exit condition (observable) | Status |
|---|---|---|---|
| 1 | Driver layer | `ScaleDriver` interface + registry; pairing auto-detects ("Found: X — is this yours?"); composition chosen by what arrived; diagnostic capture share; Experimental badge; all tests green | PR open |
| 2 | Xiaomi Mi Scale v1 / Mi Smart Scale 2 | 0x181D 10-byte broadcast parsed, unit tests from captures | PR open |
| 3 | OKOK / Chipsea | Manufacturer-data drivers with capture tests | Not started |
| 4 | Eufy P3 / C20 / P2 | Broadcast weight with capture tests | Not started |
| 5 | QN, Etekcity Fit 8S, PICOOC Mini Lite, AAA-series | Drivers with capture tests | Not started |
| 6 | Xiaomi S400/S200 | Encrypted MiBeacon with a user-supplied bindkey | Later |
| 7 | Google Play | Store listing, privacy policy, signed release | Later |

## Decisions

- Drivers live in pure-Kotlin `core/` and see an `Advertisement` value, not Android types, so
  every driver is tested on the JVM from captured bytes.
- Body composition by what the scale sent: impedance + known vendor formula (Xiaomi) → the
  profile's formula; impedance, unknown vendor → bodymiscale; weight only → weight + BMR to
  Health Connect, BMI in-app, no estimated body fat anywhere.
- Scales without a stable flag count as stable after the weight holds within 0.1 kg for 2 s;
  weight-only scales save 5 s after that (Mi BCS keeps its 20 s wait for impedance).
- The history CSV gained a trailing `scale` column (driver id). Old rows default to the Mi BCS.
- Each step is its own branch and PR against `main`; later PRs include earlier commits until
  those merge, so merge in order.
- Diagnostic capture: a rolling local file (256 KB cap) of raw advertisements from the paired
  scale, plus every nearby device while pairing; shared only via the share sheet.

## Risks

- Experimental drivers are written from other projects' notes; a real scale may differ
  (byte order, flags, unit). Mitigation: diagnostic capture and conservative `matches`.
- Manufacturer-data drivers (OKOK, QN, ...) key on company ids that many unrelated devices
  may also use; `matches` must check length and markers, not the id alone.
- Background scans are address-filtered; a scale that rotates its address would stop waking
  the app.

## Verification record

- 2026-11-09 milestone 2: green (30 tests); v1 and v2 captures from xiaomi-ble's test suite.

- 2026-11-09 milestone 1: `./gradlew testDebugUnitTest assembleDebug` green (22 tests). Not yet
  run on a phone with the Mi BCS 2.

## Next slice

Milestone 3: OKOK / Chipsea manufacturer-data drivers (openScale `OkOkHandler`,
`ChipseaBleHandler`), with captures from openScale issues or BLE Scale Sync tests.
