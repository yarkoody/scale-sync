# Provenance

Where each source file comes from. This matters because an iPhone version may need a core that
isn't GPL: files marked **ported** carry GPLv3 code from another project and would have to be
rewritten from protocol notes; files marked **original** were written for Scale Sync.

Sources:
- **openScale**: github.com/oliexdev/openScale, GPLv3, (C) olie.xdev. Reference commit
  `35a67c7` (2026-10-31).
- **BLE Scale Sync**: ble-scale-sync.pages.dev, GPLv3.
- **Protocol notes**: public descriptions of a byte layout (reverse-engineering write-ups,
  issue threads with captures). Layout facts, not copied code.

Test captures (byte strings in `app/src/test`) are facts about what a device sends; their
source is noted next to each one in the test.

## app/src/main/java/io/github/yarkoody/scalesync

| File | Origin | Notes |
|---|---|---|
| `core/BodyComposition.kt` | **ported** | `XiaomiLib` from openScale `MiScaleLib.kt` (itself from prototux/MIBCS-reverse-engineering); `ScienceLib` from openScale `BodyMiScaleLib.kt` (bodymiscale, dckiller51, GPLv3). `compute()` policy is original. |
| `core/MiScaleFrame.kt` | **ported** | 13-byte layout from openScale `MiScaleHandler.parseLive13` and prototux/MIBCS-reverse-engineering. |
| `core/drivers/XiaomiMiBcsDriver.kt` | **ported** | Name rules from openScale `MiScaleHandler.supportFor`. |
| `core/drivers/XiaomiMiScaleDriver.kt` | **ported** | 10-byte layout from openScale `MiScaleHandler.parseHistory10`. The same layout is in Bluetooth-Devices/xiaomi-ble `_parse_scale_v1` (Apache-2.0), so a non-GPL rewrite has a permissive reference. |
| `core/Advertisement.kt` | original | |
| `core/ScaleDriver.kt` | original | |
| `core/Drivers.kt` | original | |
| `core/Capture.kt` | original | |
| `core/WeighInDetector.kt` | original | |
| `ble/ScaleScanner.kt`, `ble/Receivers.kt` | original | Android-only. |
| `data/*`, `health/*`, `ui/*`, `Processor.kt`, `*Worker.kt`, `Notifier.kt` | original | Android-only; an iPhone port replaces these anyway. |

## Tests (app/src/test)

| File | Origin |
|---|---|
| `core/BodyCompositionTest.kt` | reference values from openScale `MiScaleLibTest` / `BodyMiScaleLibTest` |
| `core/drivers/XiaomiDriversTest.kt` | captures from Bluetooth-Devices/xiaomi-ble `tests/test_parser.py` (Apache-2.0); test code original |
| `core/MiScaleFrameTest.kt`, `core/WeighInDetectorTest.kt`, `core/DriverLayerTest.kt`, `core/SteadyWeightTest.kt` | original |
