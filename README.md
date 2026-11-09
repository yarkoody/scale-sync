# Scale Sync

A small Android app that reads Bluetooth bathroom scales from their broadcasts and writes
every weigh-in straight to **Health Connect**, so Google Health and other apps see it. It never
connects to the scale: no pairing ritual, no vendor app, no cloud account, no internet
permission.

Built from [openScale](https://github.com/oliexdev/openScale)'s scale code (GPLv3).

## Supported scales

| Scale | Bluetooth name | Reads | Status |
|---|---|---|---|
| Xiaomi Mi Body Composition Scale 2 (XMTZC05HM) and Mi Body Composition Scale (XMTZC02HM) | `MIBFS` / `MIBCS` | weight, impedance → body composition | **Verified** |
| Xiaomi Mi Smart Scale (XMTZC01HM) and Mi Smart Scale 2 (XMTZC04HM) | `MI_SCALE` / `MI SCALE2` | weight | Experimental |
| OKOK / Chipsea broadcast scales (NIX Home, Xiaogui TZC4, MaxxMee QJ-J, BL-26L01, EB8217, …) | none, `Yoda0`/`Yoda1`, or `ADV` | weight | Experimental |
| Eufy Smart Scale C20 (T9130) and P3 (T9150) | `eufy T9130` / `eufy T9150` | weight | Experimental |
| Eufy Smart Scale P2 (T9148) and P2 Pro (T9149) | `eufy T9148` / `eufy T9149` | weight (broadcast only; body composition needs a connection) | Experimental |

**Verified** means tested on the real scale. **Experimental** means written from other
projects' protocol notes and test captures, without the scale in hand: it should work, but if a
reading looks wrong, use **Share diagnostic capture** in the app and open an issue with the file.

When pairing, step on the scale: the app recognises the model ("Found: … is this yours?") and
asks you to pick only if two drivers could match.

## How it differs from openScale

| | openScale + openScale sync | Scale Sync |
|---|---|---|
| Reading the scale | Connects over Bluetooth; the app must be open and connected before you step on | Listens to the scale's broadcasts; Android wakes the app when the scale lights up, even when it's closed |
| Zepp Life | Can steal the connection | Unaffected: Scale Sync never connects |
| Health Connect | Separate app | Built in |
| Socks / stepped off early | Weight only | Weight only, upgraded automatically if the impedance reading arrives a moment later |

## What goes where

- **Health Connect:** weight, body fat, body water (kg), bone mass, lean body mass, BMR.
- **Stays in the app (Export CSV):** impedance, BMI, muscle, visceral fat, protein. Health
  Connect has no type for these.
- **Weight-only scales:** weight and BMR go to Health Connect; BMI is shown in the app. No body
  fat is estimated without impedance.
- **Scales with impedance but no known vendor formula** get the bodymiscale formulas.

Two formula sets, both ported from openScale: **Zepp Life** (the reverse-engineered Mi Fit
algorithm, default) and **bodymiscale** (peer-reviewed formulas from the Home Assistant
integration). BMR always uses the Schofield (WHO) equation.

## Install

1. On the phone, open the repository's **Releases → latest** and download `ScaleSync.apk`.
2. Open it and allow installing from your browser/files app when Android asks.
3. In the app: allow Bluetooth, tap your scale with your foot so it appears, pick it, fill in
   height/birth date/sex, connect Health Connect, and allow running in the background.
4. Step on barefoot and stay on until the scale finishes. A notification confirms the save.

Turn off Zepp Life's weight writing in Health Connect (App permissions → Zepp Life) so
weigh-ins don't show up twice.

## How it works

- `core/drivers/` has one driver per scale family. A driver says whether an advertisement is
  its scale (`matches`) and decodes it (`parse`) into weight, impedance, stable flag and unit,
  and declares what it can read and whether it is Verified or Experimental. For example
  `core/MiScaleFrame.kt` parses the 13-byte frame the Mi BCS puts in its service data
  (UUID 0x181B), same layout as openScale's `parseLive13`.
- `ble/ScaleScanner.kt` registers a system-held, address-filtered scan with a
  `PendingIntent`, so `ScanResultReceiver` runs whenever the scale advertises.
- `core/WeighInDetector.kt` collapses the repeated frames into one weigh-in, saving as soon
  as impedance arrives, or weight-only 20 s later if it never does (5 s for scales that have
  no impedance). Scales without a stable flag count as stable once the weight holds for 2 s.
- `health/HealthConnectWriter.kt` writes records with client ids per weigh-in, so a later
  upgrade or recalculation replaces rather than duplicates.

## Build

`./gradlew assembleRelease` (or push to `main`; GitHub Actions builds and publishes the APK
to the `latest` release). Without signing secrets each CI build gets a new debug key, so
updating means uninstalling first. To keep one key, add repository secrets
`SIGNING_KEYSTORE_B64` (base64 of a keystore with alias `scalesync`) and `SIGNING_PASSWORD`.

## License

GPLv3, like openScale. `docs/PROVENANCE.md` lists which files are ported and from where.
