# Scale Sync

A small Android app for the **Xiaomi Mi Body Composition Scale 2** (and the first Mi Body
Composition Scale, Bluetooth name `MIBCS`/`MIBFS`). It reads every weigh-in the moment the
scale finishes and writes it straight to **Health Connect**, so Google Health and other apps
see it. No separate sync app, no cloud account, no internet permission.

Built from [openScale](https://github.com/oliexdev/openScale)'s Xiaomi code (GPLv3).

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

- `core/MiScaleFrame.kt` parses the 13-byte frame the scale puts in its advertisement
  service data (UUID 0x181B), same layout as openScale's `parseLive13`.
- `ble/ScaleScanner.kt` registers a system-held, address-filtered scan with a
  `PendingIntent`, so `ScanResultReceiver` runs whenever the scale advertises.
- `core/WeighInDetector.kt` collapses the repeated frames into one weigh-in, saving as soon
  as impedance arrives, or weight-only 20 s later if it never does.
- `health/HealthConnectWriter.kt` writes records with client ids per weigh-in, so a later
  upgrade or recalculation replaces rather than duplicates.

## Build

`./gradlew assembleRelease` (or push to `main`; GitHub Actions builds and publishes the APK
to the `latest` release). Without signing secrets each CI build gets a new debug key, so
updating means uninstalling first. To keep one key, add repository secrets
`SIGNING_KEYSTORE_B64` (base64 of a keystore with alias `scalesync`) and `SIGNING_PASSWORD`.

## License

GPLv3, like openScale.
