# Backrow — project instructions for Claude Code

Read this file and every file in `docs/adr/` before writing code. These rules override generic best practices. When a rule and a request conflict, say so and ask.

## What Backrow is

An open-source (Apache-2.0) media client for YouTube on **Android TV, Google TV and Fire OS (Fire TV)**, operated entirely with a remote control. Priorities, in order:

1. A polished 10-foot UI that feels like a first-class TV app, not a template.
2. Reliable playback on cheap hardware (1 GB RAM sticks are a supported tier).
3. SponsorBlock built in.
4. Privacy: an anonymous mode (no Google account) that is fully usable.
5. A release process users can verify (CI-only builds, checksums, attestations).

**Non-goals for v1:** phones/tablets, Vega OS Fire TV, Samsung Tizen, LG webOS, Apple TV, comments, live chat, casting, downloads, plugins, Shorts.

## Facts to keep in mind

- Backrow is not affiliated with Google or YouTube and does not use the official YouTube Data API. It talks to YouTube's internal "InnerTube" endpoints the way YouTube's own TV client does. That layer breaks often and YouTube actively blocks unofficial clients; design for breakage (fixtures, fallbacks, fast hotfix releases).
- Users sideload the app. There is no app store, no Google Play Services, no Firebase.
- The maintainer reviews every change but does not write most of the code. Explain non-obvious decisions in commit messages and PR descriptions. Prefer boring, readable code over clever code.
- Your training data about YouTube internals is stale by definition. See "Reference sources" below.

## Stack (do not deviate without an ADR — see ADR-0004)

- Kotlin 2.x, JDK 21 toolchain, latest stable Android Gradle Plugin and Gradle, Kotlin DSL build scripts, Gradle version catalog (`gradle/libs.versions.toml`).
- UI: Jetpack Compose with **Compose for TV** (`androidx.tv:tv-material`) on standard Compose foundation. No Leanback, no XML layouts, no Views except the surface Media3 needs. Navigation: `androidx.navigation:navigation-compose` (ADR-0010).
- Playback: **Media3 ExoPlayer**, DASH.
- DI: Koin. Async: coroutines + Flow. Networking: OkHttp + kotlinx.serialization. Images: Coil 3. Persistence: Room + DataStore. No Retrofit, Gson, RxJava, Hilt.
- Tests: JUnit 4 + kotlinx-coroutines-test + MockWebServer for JVM tests; Compose UI tests on the Android TV emulator for critical flows.
- `minSdk = 22` (provisional, ADR-0006), `targetSdk` and `compileSdk` = latest stable, core library desugaring on.
- Versions: latest **stable** releases only (never alpha, beta or RC), resolved from the live release pages, not from memory. List the chosen versions in the PR description.
- **No dependencies beyond this list without asking first,** including small or debug-only ones (no LeakCanary, no Timber).

## Module layout

```
:app                 TV UI (Compose), navigation, Koin wiring. Never builds network requests itself;
                     Coil loads only image URLs handed to it by :core:* repositories.
:core:model          Plain Kotlin data classes shared by everything.
:core:innertube      The ONLY module that knows YouTube: hosts, endpoints, headers, client profiles,
                     URL transforms. Pure Kotlin/JVM (no Android imports). Exposes repositories, hides InnerTube.
:core:player         Media3 glue: DASH manifest builder, track-selection policy, buffer policy per device class.
:core:sponsorblock   SponsorBlock client (ADR-0008). Pure Kotlin/JVM.
:core:data           Room + DataStore: local subscriptions, history, settings, SponsorBlock user id.
:core:updater        In-app update checker/installer (later, ADR-0007).
```

Dependencies point inward: `:app → :core:*`; `:core:*` never depends on `:app`; `:core:innertube`, `:core:sponsorblock` and `:core:model` must compile without the Android SDK.

**YouTube network boundary.** Only `:core:innertube` contains YouTube hostnames, endpoint paths, request headers, client profiles or URL transforms (signature, n-parameter, query rewriting). Other modules may fetch YouTube-hosted content (Media3 fetching stream segments, Coil fetching thumbnails), but only through opaque URLs that `:core:innertube` hands them, with exactly the headers it supplies. If code outside `:core:innertube` needs to know anything about how YouTube works, that knowledge belongs in `:core:innertube` behind an interface.

## Hard rules

1. **Licensing.** No GPL, AGPL or LGPL dependencies, ever. Do not copy code from NewPipe, NewPipeExtractor, Invidious, Piped or any GPL/AGPL project, not even "adapted". Permissively licensed references only (below). The Licensee plugin's allow-list is the gate: **Apache-2.0, MIT, BSD-2-Clause, BSD-3-Clause, ISC, Unlicense**, nothing else. If a needed dependency has any other license, stop and ask; do not widen the list without an ADR. A license declared only by URL is allowed one artifact at a time, each entry with a comment naming the artifact.
2. **No Google proprietary libraries:** no Play Services, Firebase, Play Integrity, Cast SDK.
3. **No vendored or forked libraries** in the repo. Published artifacts only, pinned in the version catalog. If a patch is needed, upstream it.
4. **Never write InnerTube request/response code from memory.** Work from the clones in `reference/` (gitignored) and state which file you based it on.
5. **No secrets in the repo,** ever: signing keys, tokens, cookies, account credentials, not in code, tests, fixtures, docs or commit messages.
6. **Unit tests never touch the network.** Record sanitised fixtures into `src/test/resources` and replay them with MockWebServer. Live tests against YouTube exist only in the nightly canary workflow and are skipped unless `BACKROW_LIVE_TESTS=1`. A unit test scans every `src/test/resources` tree and fails on visitor data, cookies, authorization headers or tokens, so sanitising is checked mechanically; never weaken it to make a fixture pass.
7. Any change to `:core:innertube`, the DASH manifest builder, token storage or the updater ships with tests in the same PR.
8. **Remote-only navigation.** Every screen and control is reachable with D-pad, Select and Back. Verify initial focus, focus order, focus restoration when returning, and that focus is never lost. Many remotes (Chromecast with Google TV, Google TV Streamer) have no Menu key, so nothing may depend on it: Settings is reached through a focusable gear entry in the top bar or side rail, and the Fire TV Menu key is only a shortcut to the same places. Long-press Select opens context menus.
9. All user-facing text lives in `strings.xml`. No hardcoded UI strings.
10. **Performance budget** on the weak reference device (Fire TV Stick 2nd gen, 1 GB RAM): cold start to interactive Home under 3 s, no visible jank scrolling a row of 20 thumbnails, playback starts under 2 s after selecting a video on a 50 Mbit connection. Measure before saying "done".
11. **Privacy.** No analytics, no telemetry, no crash reporting without explicit opt-in. Anonymous mode must fully support browse, search, playback and SponsorBlock.
12. Do not add features outside `docs/ROADMAP.md` without asking.
13. New module, new major dependency, or a change to `minSdk` requires an ADR in `docs/adr/` before code.

## Reference sources (permissively licensed) — read them, don't paste blindly

| Clone into `reference/` | License | Use it for |
|---|---|---|
| `yuliskov/MediaServiceCore` | check its LICENSE first (MIT expected); if it is not on the allow-list, delete the clone and use yt-dlp and YouTube.js instead | how a TV client talks to InnerTube: browse, search, player, TV-code sign-in, remote pairing |
| `yt-dlp/yt-dlp` (`yt_dlp/extractor/youtube/`) | Unlicense | the best-maintained InnerTube client definitions, stream selection, signature/n-parameter handling |
| `LuanRT/YouTube.js` | MIT | endpoint shapes, response parsing, DASH manifest generation logic |
| `yuliskov/SmartTube` | MIT | device quirks (Fire TV, cheap boxes), HDR/AFR handling. Its UI is not a model for ours. |

When copying anything non-trivial from a permissive source, keep its license header and add a line to `NOTICE`.

## How to work

- **Plan first.** For anything bigger than a bug fix, write a short plan (files to touch, tests to add, risks) and wait for approval.
- **Vertical slices.** Each PR delivers something a user can see or a test can prove. No big-bang refactors.
- **Small PRs, conventional commits** (`feat:`, `fix:`, `chore:`, `docs:`, `test:`, `refactor:`), one concern per PR.
- **Git:** one branch and one PR per slice; never push to `main`. Commit with `git commit -s` under the maintainer's git identity (the maintainer reviews every diff, so the DCO sign-off is theirs) and keep the `Co-Authored-By: Claude` trailer.
- **Definition of done:** `./gradlew lint testDebugUnitTest assembleDebug` passes, the app was installed via adb on a real device (or, only when none is connected, on the Android TV emulator), the acceptance criteria were checked by hand, and anything surprising went into `docs/DEVICE-QUIRKS.md`. Label every measurement there as **physical** or **emulator**; emulator numbers never settle ADR-0006.
- When YouTube breaks something, the fix goes into `:core:innertube` with a fixture reproducing the new response and a dated entry in `docs/INNERTUBE-CHANGELOG.md`.
- **Logging:** a small logging interface in `:core:model` (pure-JVM modules cannot use `android.util.Log`), wired in `:app` to `android.util.Log` with tag `Backrow`, so `adb logcat -s Backrow` shows the app's logs. No Timber.
- **File headers:** every source file (Kotlin, Gradle scripts, XML resources where comments are allowed) starts with:
  ```
  // Copyright 2026 The Backrow contributors
  // SPDX-License-Identifier: Apache-2.0
  ```
  (use the comment syntax of the file type).

## Commands

```
./gradlew assembleDebug                      # build
./gradlew testDebugUnitTest                  # unit tests
./gradlew lint                               # Android lint
./gradlew licensee                           # dependency license check (task name may vary by plugin version)
adb connect <tv-ip>:5555                     # once per TV
./gradlew :app:installDebug                  # install on the connected TV
adb shell am start -n dk.backrow.debug/dk.backrow.MainActivity
adb logcat -s Backrow
```

## Fixed identifiers (ADR-0001)

- applicationId and Kotlin package prefix: `dk.backrow` (domain: backrow.dk). Never changes. Debug builds add the `.debug` suffix.
- Copyright line for file headers and `NOTICE`: `Copyright 2026 The Backrow contributors`.
- Repository: `github.com/backrow-tv/backrow`.
