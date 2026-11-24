# Device quirks

Record anything device-specific here as it is discovered: measured cold-start and time-to-first-frame numbers, remote key codes, codec surprises, firmware bugs. Date every entry and name the device, OS version and Backrow build. Mark every measurement **physical** or **emulator**: emulator numbers run on the host CPU and never prove the performance budget (CLAUDE.md rule 10).

## Measurements

Cold start = `am force-stop`, then `am start -W`; the number is `TotalTime` (first frame). Select → first frame = the app's own log line `First frame for <id>: N ms after Select` (`adb logcat -s Backrow`), from the key press to `onRenderedFirstFrame`, split into streams / manifest / player; median of 5. "warm" = the player-path warm-up (session, player JS, solver) finished before Select; it starts once Home is drawn or 3 s after launch. Home is static in these builds and calls `ReportDrawn()`, so first frame is also interactive Home where "Fully drawn" is logged. Median of 5 runs after one warm-up launch. "release" is the R8-minified release build signed locally with the debug key for measurement only.

| Date | Device | Kind | OS | Build | Cold start to Home (s) | Select → first frame (s) | Notes |
|---|---|---|---|---|---|---|---|
| 2026-11-19 | Backrow-TV-weak-API25 (Android TV x86 image, 1 GB RAM, 4 vCPU) | emulator | Android 7.1.1, API 25 | c622158 release | 0.34 (0.32–0.38) | n/a | Scaffold: placeholder cards only. |
| 2026-11-19 | Backrow-TV-weak-API25 (as above) | emulator | Android 7.1.1, API 25 | c622158 debug | 0.62 (0.59–0.67) | n/a | |
| 2026-11-19 | Backrow-TV-strong-API36 (Android TV x86 image, 2 GB RAM, 4 vCPU) | emulator | Android 16, API 36 | c622158 release | 0.50 (0.36–0.82) | n/a | First run 0.82 s, then settled. |
| 2026-11-19 | Backrow-TV-strong-API36 (as above) | emulator | Android 16, API 36 | c622158 debug | 2.47 (2.39–2.64) | n/a | Debug is ~5x slower than release here; see quirks. |
| 2026-11-24 | Backrow-TV-weak-API25 | emulator | Android 7.1.1, API 25 | spike branch release | 0.36 (0.34–0.39) | **0.44** (0.42–0.53) warm | Video `SkVqJ1SGeL0`. Tier LOW: 720p24 H.264 on the software decoder (`OMX.google.h264.decoder`), Opus audio. Player JS cached but warm-up still running: 1.70 s. First run ever (empty cache): 7.98 s, of which 6.2 s is preprocessing the player JS in QuickJS. |
| 2026-11-24 | Backrow-TV-strong-API36 | emulator | Android 16, API 36 | spike branch release | 0.68 (0.67–0.71) | **0.58** (0.54–0.82) warm | Tier NORMAL: reaches 1080p24 H.264 on the hardware decoder (`c2.goldfish.h264.decoder`) a few seconds in, Opus audio. Cached, warm-up running: 0.66–0.69 s. First run ever: 7.18 s (preprocessing 6.7 s). Cold start: `main` measured 0.62 s interleaved in the same session, so this slice adds ~65 ms here (none measurable on API 25). |

**The project currently has no physical test devices; all checks run on emulators.** That leaves unverified: the performance budget on a real 1 GB stick, the Fire TV launcher and Fire OS behaviour (Fire OS cannot be emulated), and the Fire TV remote's Menu key on real hardware. Community reports on real devices are especially welcome for these.

Emulators used (create with `avdmanager`, see CLAUDE.md commands):

| AVD | Image | RAM | Role |
|---|---|---|---|
| Backrow-TV-weak-API25 | `system-images;android-25;android-tv;x86` | 1 GB | weak stand-in (lowest supported API) |
| Backrow-TV-strong-API36 | `system-images;android-36;android-tv;x86` | 2 GB | strong stand-in |
| Backrow-GoogleTV-API36 | `system-images;android-36;google-tv;x86` | 2 GB | Google TV launcher and focus behaviour |

`HomeFocusRestorationTest` passes (4/4) on all three, run on 2026-11-19 against c622158 and later.

## Quirks

- **2026-11-24 · API 36 Android TV emulator · spike:** no decoder claims any 1080p**60** format. `c2.goldfish.h264.decoder` (hardware) stops at 720p30, the `c2.android.*` software decoders at 720p60, and H.264 High@4.2 (`avc1.64002a`) 1080p60 is `NO_EXCEEDS_CAPABILITIES` in Media3. 1080p24/30 works. The spike therefore plays a 24 fps video (`SkVqJ1SGeL0`), and `TrackPolicy` asks Media3's own `isFormatSupported` (profile and level included) instead of checking only size and frame rate.
- **2026-11-24 · API 25 Android TV emulator (1 GB) · spike:** `ActivityManager.memoryClass` is **256 MB**, larger than the 2 GB API 36 emulator's 192 MB (the image sets no `dalvik.vm.heapgrowthlimit`). Memory class alone would put the 1 GB device in the high tier, so `BufferPolicy` also treats < 1.5 GiB total RAM as LOW. Record real Fire TV values here when someone has one.
- **2026-11-24 · API 25 Android TV emulator · spike:** `fullyDrawnReporter.addOnReportDrawnListener` never fires (same root as the missing "Fully drawn" line below). The warm-up therefore also starts on a 3 s timer.
- **2026-11-24 · both emulators · spike:** preprocessing YouTube's 2.5–3 MB player JS in QuickJS takes **6–10 s** on the emulators (x86, host CPU), against 0.95 s in node on the host. It runs once per player version and is cached on disk; rebuilding the solver functions from the cache takes 0.6–0.9 s per process start, and solving one video's challenges then takes ~1 ms. **Unmeasured on ARM hardware**: a 1 GB Fire TV stick may be several times slower, which would make the first video after each YouTube player update slow. This is the main open risk of ADR-0009.
- **2026-11-24 · API 36 emulator · spike:** the first use of `Dispatchers.Main` (e.g. `lifecycleScope.launch`) in `MainActivity.onCreate` added ~80 ms to cold start (a service lookup on the main thread). Startup code avoids coroutines; the warm-up trigger is a plain `postDelayed`.
- **2026-11-24 · API 25 emulator · tooling:** `connectedDebugAndroidTest` failed with `INSTALL_FAILED_ALREADY_EXISTS` after the debug APK had been installed by hand; run `adb uninstall dk.backrow.debug` first.
- **2026-11-24 · QuickJS native library · spike:** quickjs-kt reports QuickJS `2026-06-04`. Its `libquickjs.so` loads and runs on both x86 emulators; on Fire OS (ARM, Amazon's Android fork) it is **untested** (ADR-0009).

- **2026-11-19 · API 25 Android TV emulator · c622158:** `ReportDrawn()` / `reportFullyDrawn()` produced no "Fully drawn" line in logcat (the API 36 emulator logs it via `ActivityTaskManager`). Use `TotalTime` from `am start -W` on Android 7.x. Check on a real Fire OS 6 device.
- **2026-11-19 · API 36 Android TV emulator · c622158:** debug builds cold-start in about 2.5 s versus 0.5 s for release. Recent Android versions do not precompile debuggable apps, so Compose runs mostly interpreted. **Always measure the release build** against the performance budget.
- **2026-11-19 · both emulators · c622158:** debug and release show identical banners in the launcher (only the label differs, and Android TV hides labels). Consider a "DEBUG" badge on the debug banner.
- **2026-11-19 · Google TV API 36 emulator · c622158:** the Google TV launcher stays on its "Set up Google TV" sign-in prompt without a Google account, so the app grid cannot be checked visually. `adb shell cmd package query-activities -a android.intent.action.MAIN -c android.intent.category.LEANBACK_LAUNCHER` lists `dk.backrow` and `dk.backrow.debug`, i.e. the launcher can see the app. The Android TV API 25 launcher showed the banner without sign-in.
- **2026-11-19 · tooling (Windows):** `sdkmanager.bat` and `avdmanager.bat` called from Git Bash split package names at `;`. Call them from PowerShell or use `sdkmanager --package_file=<file>`.
- **2026-11-19 · both emulators · c622158:** moving focus down to row 2 scrolls Home so row 1's title leaves the screen. Focus works; the scroll pivot needs UI polish when real rows arrive.
