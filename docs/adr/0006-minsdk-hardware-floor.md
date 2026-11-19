# ADR-0006: minSdk and hardware floor
2026-09-22 · Proposed · Accepted 2026-11-19 with `minSdk = 25`, before the Session 2 spike, because the library floor (below) decided it

## Context
Fire OS 5 (Android 5.1, API 22) devices such as the Fire TV Stick 2nd gen (2016) and Basic Edition (2017) are still common and have 1 GB RAM. Fire OS 6 (Android 7.1, API 25) covers the Fire TV Stick 4K (2018) and Cube 1st gen; Fire OS 7 (Android 9, API 28) and Fire OS 8 (Android 11, API 30) cover 2020+ devices. Baseline profiles (faster cold start) only work on API 24+, and Compose costs more CPU and memory than Leanback.

The proposal was `minSdk = 22` if the scaffold and the playback spike met the performance budget on a Fire TV Stick 2nd gen, else 25. Checking the published artifacts while planning the scaffold (the `minSdkVersion` in each AAR manifest on Google Maven and Maven Central, 2026-11-19) settled it before any measurement:

| Library | Latest stable | Its minSdk | Last line supporting API 22 |
|---|---|---|---|
| Compose ui / foundation | 1.13.0 | 24 (1.10–1.12: 23) | 1.9.x |
| navigation-compose | 2.10.2 | 24 | 2.9.x |
| DataStore | 1.3.0 | 24 | 1.1.x |
| tv-material, activity-compose, core, Media3, Room, Coil 3 | current | 23 | older lines |

Keeping API 22 would mean freezing the whole UI, playback and persistence stack on year-old versions with no fixes, holding Renovate back, and facing a forced jump later. That contradicts ADR-0004 (latest stable, small continuous updates).

## Decision
- **`minSdk = 25`** (Fire OS 6 and Android 7.1+, roughly 2018+ hardware). This is the fallback the proposal already named, and it is above every current stable library's floor (24), leaving room for the next AndroidX raise.
- Fire OS 5 / Android 5.1–7.0 devices are unsupported, including the Fire TV Stick 2nd gen and Basic Edition.
- **The weak reference device becomes the Fire TV Stick Lite / 3rd gen (2020, Fire OS 7, 1 GB RAM).** It keeps the 1 GB budget that matters most. When only emulators are available, the weak emulator is API 25 with 1 GB RAM, so the lowest API and the lowest RAM are covered together.
- `targetSdk` and `compileSdk` follow the latest stable Android; core library desugaring is on (subject to the licence decision on `desugar_jdk_libs`); Vega OS is never a target.
- Runtime tuning, not build-time: buffer sizes, image cache limits and prefetch depth are derived from `ActivityManager.memoryClass` / `isLowRamDevice` at startup.

## Consequences
- docs/COMPATIBILITY.md, CLAUDE.md rule 10 and KICKOFF.md are updated to the new floor and reference device. The README states the floor once it describes hardware.
- Baseline profiles work on every supported device.
- Raising `minSdk` again requires a new ADR and a deprecation notice one stable release ahead. Expect AndroidX to keep raising its floor; check each library's minSdk when Renovate proposes a major update.
- Users on Fire TV Stick 2nd gen / Basic Edition have no Backrow build. That is the accepted cost.

## Alternatives considered
- `minSdk` 22 with pinned Compose 1.9 / navigation 2.9 / DataStore 1.1: keeps 2016 sticks, but freezes the stack and blocks updates; rejected.
- `minSdk` 24: the exact current library floor; gains no Fire TV devices over 25 and would break at the next AndroidX raise.
- `minSdk` 28+: cleaner APIs, but abandons the Fire TV Stick 4K (2018) and Cube 1st gen that many users still run.
