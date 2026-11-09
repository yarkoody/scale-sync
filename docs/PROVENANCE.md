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
| `core/drivers/OkokChipseaDriver.kt` | **ported** | From BLE Scale Sync `src/scales/okok-chipsea.ts` (dialects, properties whitelist, address-echo match, r1 is not impedance). The C0 dialect is also decoded by ble_monitor's Xiaogui parser (MIT), a permissive reference. |
| `core/drivers/EufyDrivers.kt` | **ported** | C20/P3 from BLE Scale Sync `src/scales/eufy-c20-p3.ts`; P2 broadcast from BLE Scale Sync `src/scales/eufy-p2.ts`. Weight offsets and names were first published by bdr99/eufylife-ble-client (MIT), a permissive reference. |
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
| `core/drivers/OkokChipseaDriverTest.kt`, `core/drivers/TestAds.kt` | captures as collected in BLE Scale Sync `tests/scales/okok-chipsea.test.ts` (from openScale and ble_monitor issue attachments); test code original |
| `core/drivers/EufyDriversTest.kt` | C20/P3 captures as collected in BLE Scale Sync `tests/scales/eufy-c20-p3.test.ts` (from bdr99/eufylife-ble-client #3, #5); P2 Pro frames from eufylife-ble-client#8; test code original |
| `core/MiScaleFrameTest.kt`, `core/WeighInDetectorTest.kt`, `core/DriverLayerTest.kt`, `core/SteadyWeightTest.kt` | original |
