# ADR-0006: minSdk and hardware floor
2026-09-22 · Proposed — decided after the Session 2 spike

## Context
Fire OS 5 (Android 5.1, API 22) devices such as the Fire TV Stick 2nd gen (2016) and Basic Edition (2017) are still common and have 1 GB RAM. Fire OS 6 (Android 7.1, API 25) covers the Fire TV Stick 4K (2018) and Cube 1st gen; Fire OS 7 (Android 9, API 28) and Fire OS 8 (Android 11, API 30) cover 2020+ devices. Compose supports API 21+, but baseline profiles (faster cold start) only work on API 24+, and Compose costs more CPU and memory than Leanback.

## Decision (provisional)
- `minSdk = 22`, on the condition that the scaffold (Session 1) and the playback spike (Session 2) meet the performance budget in CLAUDE.md on a Fire TV Stick 2nd gen.
- If they do not, `minSdk = 25` (Fire OS 6 and Android 7.1+, roughly 2018+ hardware), recorded in docs/COMPATIBILITY.md and the README.
- Either way: `targetSdk` and `compileSdk` follow the latest stable Android; core library desugaring is on; Android below 5.1 and Vega OS are never targets.
- Runtime tuning, not build-time: buffer sizes, image cache limits and prefetch depth are derived from `ActivityManager.memoryClass` / `isLowRamDevice` at startup.

## Consequences
- Device tiers and the test matrix live in docs/COMPATIBILITY.md and are updated when this ADR is accepted.
- Raising `minSdk` later requires a new ADR and a deprecation notice one stable release ahead.

## Alternatives considered
- `minSdk` 21: adds almost no devices over 22 in the TV world and costs desugaring edge cases.
- `minSdk` 28+: cleaner APIs, but abandons the 2016–2019 sticks that many potential users still run.
