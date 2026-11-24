# InnerTube changelog

Dated notes on every YouTube-side change that required a fix in `:core:innertube` or `:core:player`: what broke, how it was noticed (canary, user report), what changed, which fixture reproduces it, which reference source documented it.

## 2026-11-24 · First player path (Session 2 spike)

- **What:** anonymous playback via `/youtubei/v1/player` with the client chain `tv_samsung` → `visionos` (`:core:innertube` `ClientProfiles.kt`). Every client needs visitor data, taken from the `ytcfg` on `https://www.youtube.com/tv`; without it all clients get `LOGIN_REQUIRED` "Sign in to confirm you're not a bot". `tv` and `tv_downgraded` get `UNPLAYABLE` "The page needs to be reloaded" and are not in the chain. `tv_samsung` URLs need the `n` challenge solved (and `signatureCipher` for some videos) by the bundled EJS solver in QuickJS (ADR-0009).
- **Noticed by:** live probes during the spike.
- **Fixtures:** `core/innertube/src/test/resources/player/`: `tv_samsung-ok-n.json`, `tv_samsung-ok-cipher.json`, `visionos-ok.json`, `tv-unplayable-reload.json`, `tv_samsung-login-required.json`.
- **Reference:** reference/yt-dlp `_base.py` (`INNERTUBE_CLIENTS`, `_download_ytcfg`, `generate_api_headers`), `_video.py` (`_DEFAULT_CLIENTS`, `_generate_player_context`, `_extract_signature_timestamp`), `jsc/_builtin/ejs.py`; reference/MediaServiceCore `innertube/utils/Constants.kt`.

## 2026-11-24 · New player `4ee1fa28` breaks EJS 0.8.0 on the main player build

- **What broke:** YouTube served a new player, `4ee1fa28` (alongside `7f755914`, apparently an A/B rollout). EJS 0.8.0 fails to preprocess its main build `player_ias.vflset/en_US/base.js` with `TypeError: a.addOnDisposeCallback is not a function` (same error in node, so not a QuickJS problem). `tv_samsung` failed and the app fell back to `visionos`.
- **Noticed by:** device check on the API 25 emulator, warm-up log.
- **Fix:** solve against the TV build of the player, `tv-player-ias.vflset/tv-player-ias.js` (yt-dlp `_PLAYER_JS_VARIANT_MAP['tv']`, the build YouTube's /tv page loads). EJS 0.8.0 preprocesses it for both players, and for `7f755914` its answers are identical to the main build's. See `ChallengeSolver.PLAYER_PATH`.
- **Fixture:** none can reproduce it (fixtures never contain YouTube's player JS); covered by the device check and, later, the canary.
- **Upstream:** no yt-dlp or yt-dlp/ejs issue existed at the time. Follow-up: report it to yt-dlp/ejs.

## 2026-11-24 · Open: solved `signatureCipher` URLs returned 403 (seen once)

- **What:** for a music video with `signatureCipher` formats, URLs with both `n` and `sig` solved returned 403 in the afternoon, with both player builds, while the same steps returned 200 two hours earlier and unciphered videos kept working. Cause unknown.
- **Impact:** such a video fails at playback with an HTTP error. The fallback chain does not catch this, because the failure happens after `streams()` succeeded. Follow-up: check one solved URL before returning streams, so a 403 falls back to the next profile.


## 2026-11-24 · Bot check on GitHub's runner IPs (canary inconclusive, not broken)

- **What:** the first canary run on GitHub Actions got `LOGIN_REQUIRED` "Sign in to confirm you're not a bot" for both `tv_samsung` and `visionos`, with valid visitor data from `/tv`. The same tests passed 6/6 from the maintainer's home network the same day. The `/tv` session, the player JS download and EJS solving passed on the runner. YouTube bot-checks datacenter IPs; Backrow's users are on home networks.
- **Noticed by:** the canary, issue backrow-tv/backrow#6 (a false alarm).
- **Change:** `PlaybackUnavailableException.botCheckOnEveryProfile` says when every profile in the chain got the bot check (unit test in `PlayerRepositoryTest`, fixture `tv_samsung-login-required.json`). The live playback tests report that as `INCONCLUSIVE:`, and `canary.yml` treats a run with no other failure as inconclusive: a warning in the run, no issue. Any other failure still files one.
- **Limit:** while the runner's IP is bot-checked, the canary does not test the player request or the stream fetch. Running it from a residential network (a self-hosted runner) would close that gap. If YouTube starts bot-checking home networks too, users will notice before the canary does.
