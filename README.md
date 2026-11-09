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

## Pull request agents

Both run on Claude Sonnet 5.5 (`claude-sonnet-5-5`) through the Claude GitHub App and need the repository secret
`CLAUDE_CODE_OAUTH_TOKEN` (run `claude setup-token` on a computer signed in to Claude).

- **Claude PR review** (`.github/workflows/claude-review.yml`) reviews every non-draft PR on
  each push, comments on the lines it has problems with, and submits "Request changes" or a
  plain review comment.
- **Claude merge agent** (`.github/workflows/claude-merge.yml`) acts only on PRs labelled
  `automerge`. It merges `main` into a conflicting PR and resolves the conflicts; once CI is
  green and the review on the latest commit was addressed, it approves and squash-merges.
  Without the label it does nothing, so add the label when you want a PR merged for you.
  Stacked PRs (a branch built on another open PR) wait until the PR below them is merged, so
  you can label a whole stack at once and it merges bottom-up.

## License

GPLv3, like openScale. `docs/PROVENANCE.md` lists which files are ported and from where.
