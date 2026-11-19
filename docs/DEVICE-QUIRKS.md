# Device quirks

Record anything device-specific here as it is discovered: measured cold-start and time-to-first-frame numbers, remote key codes, codec surprises, firmware bugs. Date every entry and name the device, OS version and Backrow build. Mark every measurement **physical** or **emulator**: emulator numbers run on the host CPU and never prove the performance budget (CLAUDE.md rule 10).

## Measurements

Cold start = `am force-stop`, then `am start -W`; the number is `TotalTime` (first frame). Home is static in these builds and calls `ReportDrawn()`, so first frame is also interactive Home where "Fully drawn" is logged. Median of 5 runs after one warm-up launch. "release" is the R8-minified release build signed locally with the debug key for measurement only.

| Date | Device | Kind | OS | Build | Cold start to Home (s) | Select → first frame (s) | Notes |
|---|---|---|---|---|---|---|---|
| 2026-11-19 | Backrow-TV-weak-API25 (Android TV x86 image, 1 GB RAM, 4 vCPU) | emulator | Android 7.1.1, API 25 | c622158 release | 0.34 (0.32–0.38) | n/a | Scaffold: placeholder cards only. |
| 2026-11-19 | Backrow-TV-weak-API25 (as above) | emulator | Android 7.1.1, API 25 | c622158 debug | 0.62 (0.59–0.67) | n/a | |
| 2026-11-19 | Backrow-TV-strong-API36 (Android TV x86 image, 2 GB RAM, 4 vCPU) | emulator | Android 16, API 36 | c622158 release | 0.50 (0.36–0.82) | n/a | First run 0.82 s, then settled. |
| 2026-11-19 | Backrow-TV-strong-API36 (as above) | emulator | Android 16, API 36 | c622158 debug | 2.47 (2.39–2.64) | n/a | Debug is ~5x slower than release here; see quirks. |

**The project currently has no physical test devices; all checks run on emulators.** That leaves unverified: the performance budget on a real 1 GB stick, the Fire TV launcher and Fire OS behaviour (Fire OS cannot be emulated), and the Fire TV remote's Menu key on real hardware. Community reports on real devices are especially welcome for these.

Emulators used (create with `avdmanager`, see CLAUDE.md commands):

| AVD | Image | RAM | Role |
|---|---|---|---|
| Backrow-TV-weak-API25 | `system-images;android-25;android-tv;x86` | 1 GB | weak stand-in (lowest supported API) |
| Backrow-TV-strong-API36 | `system-images;android-36;android-tv;x86` | 2 GB | strong stand-in |
| Backrow-GoogleTV-API36 | `system-images;android-36;google-tv;x86` | 2 GB | Google TV launcher and focus behaviour |

`HomeFocusRestorationTest` passes (4/4) on all three, run on 2026-11-19 against c622158 and later.

## Quirks

- **2026-11-19 · API 25 Android TV emulator · c622158:** `ReportDrawn()` / `reportFullyDrawn()` produced no "Fully drawn" line in logcat (the API 36 emulator logs it via `ActivityTaskManager`). Use `TotalTime` from `am start -W` on Android 7.x. Check on a real Fire OS 6 device.
- **2026-11-19 · API 36 Android TV emulator · c622158:** debug builds cold-start in about 2.5 s versus 0.5 s for release. Recent Android versions do not precompile debuggable apps, so Compose runs mostly interpreted. **Always measure the release build** against the performance budget.
- **2026-11-19 · both emulators · c622158:** debug and release show identical banners in the launcher (only the label differs, and Android TV hides labels). Consider a "DEBUG" badge on the debug banner.
- **2026-11-19 · Google TV API 36 emulator · c622158:** the Google TV launcher stays on its "Set up Google TV" sign-in prompt without a Google account, so the app grid cannot be checked visually. `adb shell cmd package query-activities -a android.intent.action.MAIN -c android.intent.category.LEANBACK_LAUNCHER` lists `dk.backrow` and `dk.backrow.debug`, i.e. the launcher can see the app. The Android TV API 25 launcher showed the banner without sign-in.
- **2026-11-19 · tooling (Windows):** `sdkmanager.bat` and `avdmanager.bat` called from Git Bash split package names at `;`. Call them from PowerShell or use `sdkmanager --package_file=<file>`.
- **2026-11-19 · both emulators · c622158:** moving focus down to row 2 scrolls Home so row 1's title leaves the screen. Focus works; the scroll pivot needs UI polish when real rows arrive.
