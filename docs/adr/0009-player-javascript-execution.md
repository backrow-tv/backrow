# ADR-0009: Player JavaScript execution (signature / n-parameter)
2026-11-24 · Proposed · Accepted 2026-11-24

## Context
ADR-0005 left this open until the Session 2 spike showed whether stream URLs need YouTube's player JavaScript. Reading the references (clones of 2026-11-17/19) settles it before any code:

- **Every TV client needs the player JS.** In `reference/yt-dlp/yt_dlp/extractor/youtube/_base.py` (`INNERTUBE_CLIENTS`), `tv`, `tv_downgraded`, `tv_samsung` and `tv_simply` do not set `REQUIRE_JS_PLAYER: False`, so `build_innertube_clients()` defaults them to `True`. Only `android`, `android_vr`, `ios` and `visionos` are JS-free, and of those `android`, `android_vr` and `ios` need a GVS PO token for anonymous DASH/HTTPS formats (`android_vr` is fully 403'd since 2026-08-17, per the comment there).
- **What the JS is needed for.** `_video.py` (`_extract_formats_and_subtitles`, around lines 3300–3590): formats arrive either with a `signatureCipher` (`s` must be transformed by a player function) or with an `n` query parameter (must be transformed, or the format is skipped). The player request also sends `signatureTimestamp`, extracted from the player JS (`_extract_signature_timestamp`, around line 2235).
- **yt-dlp no longer interprets the JS itself.** Its own `jsinterp.py` is not used by the YouTube extractor any more. It runs the "EJS" solver (`jsc/_builtin/vendor/yt.solver.core.js`, SPDX Unlicense, v0.8.0, from github.com/yt-dlp/ejs) inside an external JS runtime: deno, node, bun or QuickJS (`jsc/_builtin/quickjs.py`). Without a runtime it falls back to `_DEFAULT_JSLESS_CLIENTS = ('visionos',)` and warns that JS-less extraction "has been deprecated, and some formats may be missing" (`_video.py` lines 144–145, 2998–3003).
- **MediaServiceCore does the same.** `app/nsigsolver/impl/V8ChallengeProvider.kt` runs the same EJS `yt.solver.core.js` plus `meriyah-6.1.4.min.js` (parser, ~140 KB) and `astring-1.9.0.min.js` (~16 KB) from `src/main/assets/nsigsolver/` in J2V8. Its default TV client is `TV_DOWNGRADED` (`videoinfo/V2/VideoInfoService.java`); it uses `VISIONOS` only for HLS.

So the spike cannot play a video through a TV client without executing player JS. ADR-0005's "purpose-built interpreter as yt-dlp uses" is out of date: yt-dlp abandoned that path for YouTube.

Constraints: pure-JVM `:core:innertube` (CLAUDE.md), licence allow-list (rule 1), no vendored libraries (rule 3), `minSdk = 25`, 1 GB RAM devices, select-to-first-frame under 2 s.

Candidate engines checked on Maven on 2026-11-24 (POM licence, AAR `minSdkVersion`):

| Engine | Latest stable | Licence | minSdk | Notes |
|---|---|---|---|---|
| QuickJS via `app.cash.zipline:zipline-android` | 1.28.0 | Apache-2.0 | 23 | Cash App; also `zipline-jvm` for JVM tests. Plain `evaluate` API and native size to confirm. |
| QuickJS via `io.github.dokar3:quickjs-kt-android` | 1.0.18 | Apache-2.0 | 23 | `libquickjs.so` ~0.66 MB (armeabi-v7a) – 0.93 MB (arm64); `quickjs-kt-jvm` exists. Single maintainer. |
| `app.cash.quickjs:quickjs-android` | 0.9.2 (2021) | Apache-2.0 | — | Unmaintained; excluded. |
| `androidx.javascriptengine` | 1.1.1 | Apache-2.0 | **26** | Above our minSdk, needs WebView sandbox support; excluded. |
| Rhino `org.mozilla:rhino` | 1.9.2 | **MPL-2.0** | JVM | Not on the allow-list; would need a licence ADR. |
| J2V8 (MediaServiceCore's choice) | — | EPL | — | Not on the allow-list; SmartTube vendors a fork. Excluded. |

## Options considered
1. **Embedded QuickJS running the EJS solver** (what both references do, with QuickJS instead of V8). `:core:innertube` defines a tiny `JsRuntime` interface (evaluate a script, return a string) and owns everything YouTube-specific: fetching the player JS, assembling the solver input, applying results to URLs, caching per player id. An Android module implements `JsRuntime` with QuickJS. JVM tests use the `-jvm` artifact or a fake.
   + Same mechanism the best-maintained references use; solver updates are a pinned-file bump. + ~1 MB native per ABI, no WebView.
   − New native dependency; the solver parses the multi-megabyte player JS with meriyah, which costs RAM and seconds on a weak stick. Results must be cached per player version to meet the 2 s budget.
2. **Android System WebView as the JS runtime.** No new dependency, present on API 25 and Fire OS.
   − Tens of MB of RAM on a 1 GB device, slow to spin up, behaviour varies by WebView build, untestable on the JVM.
3. **Write a Kotlin JS interpreter for the player functions.** No native code.
   − The path yt-dlp abandoned; breaks with every player change; large ongoing cost. Rejected.
4. **Avoid JS: lead with the `visionos` client.** JS-free and, per yt-dlp, no PO token.
   − Not a TV client; "made for kids" videos fail; yt-dlp calls JS-less extraction deprecated with missing formats; whether it returns 1080p DASH anonymously is unverified. Acceptable as a fallback tier, not as the foundation.
5. Rhino (MPL-2.0) or J2V8 (EPL): licence change needed. Not proposed.

## Spike results
2026-11-24, live requests from the maintainer's network (exploration scripts, not tests), video `aqz-KE-bpKQ` plus three music videos:

- Without visitor data every client (`tv`, `tv_downgraded`, `tv_samsung`, `visionos`) gets `LOGIN_REQUIRED` "Sign in to confirm you're not a bot". Visitor data comes from the `ytcfg` on `https://www.youtube.com/tv` (`INNERTUBE_CONTEXT.client.visitorData`), the page yt-dlp's `_download_ytcfg` uses for its `tv` client (`_base.py`). The same page names the current player id.
- With visitor data: `tv` and `tv_downgraded` get `UNPLAYABLE` "The page needs to be reloaded"; **`tv_samsung` is `OK`** (41 adaptive formats up to 2160p, H.264/VP9/AV1, AAC/Opus); `visionos` is `OK` (32 formats up to 2160p, plus a SABR URL). This matches yt-dlp's `_DEFAULT_CLIENTS = ('tv_samsung', 'visionos', 'web')`.
- `tv_samsung` URLs carry an `n` parameter and return 403 until it is solved; music videos also come with `signatureCipher` (`s`, `sp=sig`). `visionos` URLs return 200 as they are.
- The EJS 0.8.0 solver (run in node on the host, as a check only) solved `n` and `sig` against the main player `player_ias.vflset/en_US/base.js` (3.07 MB); the solved URLs returned 200 for 2160p VP9/AV1 and AAC. Full solve 0.95 s, solve from the preprocessed player (4.2 MB) 0.3 s, on a desktop CPU with V8. QuickJS on a TV will be slower; measured numbers go into docs/DEVICE-QUIRKS.md.

## Decision
- Option 1: embedded QuickJS running the EJS solver. Playback fallback chain: **`tv_samsung` → `visionos`**, kept in one file in `:core:innertube` (ADR-0005 §2).
- QuickJS binding: **`io.github.dokar3:quickjs-kt-android` 1.0.18** (Apache-2.0, minSdk 23). Zipline 1.28.0 was the first choice and does have a plain `QuickJs.evaluate`, but it ships `libquickjs.so` of 1.77–2.43 MB per ABI (quickjs-kt: 0.66–0.93 MB) and pulls in okio and kotlinx-serialization-json. quickjs-kt's single maintainer is the accepted risk; the engine sits behind `JsRuntime`, so swapping it is one class.
- `JsRuntime` (evaluate a script, return a string) lives in `:core:innertube`; its Android implementation lives in the new module **`:core:jsengine`** (Android library, no YouTube knowledge: it executes strings it is handed). This ADR is the module's justification (CLAUDE.md rule 13). Unit tests in `:core:innertube` use a fake.
- **Solver scripts are bundled** (maintainer decision, 2026-11-24): `yt.solver.lib.min.js` and `yt.solver.core.min.js` from the yt-dlp/ejs 0.8.0 GitHub release, unmodified, in `:core:innertube` resources. A unit test checks their SHA3-512 against the hashes yt-dlp pins in `jsc/_builtin/vendor/_info.py`. Bumping them is a reviewed PR, like a dependency bump. This is a narrow exception to CLAUDE.md rule 3 for these two generated release files, not a precedent for vendoring libraries.
- Licences: EJS is Unlicense; the lib file bundles meriyah 6.1.4 (ISC) and astring 1.9.0 (MIT) with their licence texts in its header. All on the allow-list; listed in `NOTICE`.
- The native library on Fire TV is accepted without a physical-device check (none available); that check stays open in docs/DEVICE-QUIRKS.md.
- Cache per player id: `signatureTimestamp` and the preprocessed player; solved `n`/`sig` values per challenge. Solve off the main thread.


## Consequences
- The spike grows: player JS fetch, a solver, a cache, a new module. Expect select-to-first-frame on the first video after a player update to exceed 2 s on weak devices; later videos hit the cache.
- Breakage now has two sources (InnerTube shapes and the player JS); the nightly canary (ADR-0007) must cover solving too.
- `NOTICE` gains entries for the solver scripts and QuickJS.

## Alternatives considered
Options 2–5 above.
