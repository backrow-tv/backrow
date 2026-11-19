# Compatibility

Backrow runs on Android TV, Google TV and Fire OS devices. It does not run on Fire TV devices with Vega OS (Fire TV Stick 4K Select, October 2025, and later Vega devices), Samsung Tizen, LG webOS, Roku, Apple TV, Vizio or Hisense VIDAA TVs. Phones and tablets are not supported.

**Hardware floor: Android 7.1 (API 25) / Fire OS 6 or newer** (ADR-0006). Fire OS 5 devices such as the Fire TV Stick 2nd gen (2016) and Basic Edition (2017) are not supported, because current AndroidX libraries no longer run on Android 5.1–7.0.

## Tiers

| Tier | Meaning | Examples |
|---|---|---|
| **1 — must be great** | Every release is hand-tested here; the performance budget is measured here | Fire TV Stick 4K (2018, Fire OS 6), Fire TV Stick 4K Max (2021/2023), Fire TV Cube 2nd/3rd gen, Chromecast with Google TV, Google TV Streamer, NVIDIA Shield TV (2017/2019), 2020+ Sony/TCL/Hisense/Philips Google TVs |
| **2 — must work** | Tested before stable tags; performance budget applies to the weak reference device | Fire TV Stick Lite / 3rd gen (2020, Fire OS 7, 1 GB RAM — the weak reference device), Xiaomi Mi Box S, 2017–2019 Android TVs on Android 7.1 or newer |
| **3 — best effort** | Community-reported; bugs fixed when cheap | No-name Android TV boxes on Android 7.1+ with unusual firmware |
| **Unsupported** | Issues are closed with a link here | Fire OS 5 (Fire TV Stick 2nd gen, Basic Edition, Fire TV 2nd gen box), Android below 7.1, Vega OS, Tizen, webOS, tvOS, Roku, phones, tablets |

## Fire OS to Android mapping

| Fire OS | Android | API | Typical devices |
|---|---|---|---|
| 5 | 5.1 | 22 | Fire TV Stick 2nd gen (2016), Basic Edition (2017), Fire TV 2nd gen box. Not supported (ADR-0006). |
| 6 | 7.1 | 25 | Fire TV Stick 4K (2018), Fire TV Cube 1st gen, Fire TV 3rd gen pendant |
| 7 | 9 | 28 | Fire TV Stick Lite / 3rd gen (2020), Stick 4K Max (2021), Cube 2nd/3rd gen, Fire TV smart TVs |
| 8 | 11 | 30 | Fire TV Stick 4K / 4K Max 2nd gen (2023) |
| Vega OS | — | — | Fire TV Stick 4K Select (Oct 2025) and later. Not Android. Not supported. |

## Codec expectations

- H.264/AVC: hardware on everything; the fallback.
- VP9: hardware on most 2015+ devices; software VP9 is excluded on weak CPUs.
- AV1: hardware only on recent devices (for example Fire TV Stick 4K Max 2nd gen, Google TV Streamer); never software-decoded.
- HDR (HDR10/HLG) and 60 fps: offered only when the device reports support; the settings screen shows what the device claims.

## Reporting a device

Bug reports must include: device model, Fire OS or Android version, Backrow version and channel, whether signed in, and `adb logcat -s Backrow` output where possible. See `.github/ISSUE_TEMPLATE/bug_report.yml`.
