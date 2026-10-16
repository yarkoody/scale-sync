# My Health (Scale Sync)

A personal Android health app: one place for your wearable, scale and HMO medical data, with
Claude for insights. It started as Scale Sync, a reader for the **Xiaomi Mi Body Composition
Scale 2**, and still is one (the Body tab). Inspired by
[Wellness Project](https://wellnessproject.ai/), but local-first: the app has no internet
permission, so nothing leaves the phone unless you send it somewhere.

## Tabs

- **Today:** last night's sleep, resting heart rate, HRV, steps, blood oxygen, breathing rate
  and weight, each against your own 7- and 30-day baseline, plus check-ups and renewals coming up.
- **Sleep:** nightly duration and stages, bedtime/wake averages and consistency, a 14-night chart.
- **Body:** the scale (live reading, pairing, weigh-ins, CSV export).
- **Records:** blood tests over the years with ranges and trends, medications, history, open
  referrals, and the original PDFs. Israeli HMOs (Meuhedet, Clalit, Maccabi) have no patient
  API, so documents come in as PDFs: add one, send it to Claude, paste Claude's reply back.
- **Claude:** pick a focus (weekly check-in, sleep coach, labs and longevity, body composition,
  doctor visit prep), and send a compact brief of your data to the Claude app.

Wearable data comes from **Health Connect** (Google Health writes Fitbit data there).

### Why Claude goes through the Claude app

A Claude Pro/Max subscription can't be called from other apps; that needs a separate, pay-per-use
API key. So the app builds the brief and hands it (or a PDF) to the Claude app with Android's
share sheet, and the conversation runs on your subscription. Records come back as a JSON block
(`ClaudeBrief.EXTRACT_PROMPT` describes the format) that the app parses and merges.

## Scale details

### How it differs from openScale

| | openScale + openScale sync | Scale Sync |
|---|---|---|
| Reading the scale | Connects over Bluetooth; the app must be open and connected before you step on | Listens to the scale's broadcasts; Android wakes the app when the scale lights up, even when it's closed |
| Zepp Life | Can steal the connection | Unaffected: Scale Sync never connects |
| Health Connect | Separate app | Built in |
| Socks / stepped off early | Weight only | Weight only, upgraded automatically if the impedance reading arrives a moment later |

### What goes where

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

### How it works

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
