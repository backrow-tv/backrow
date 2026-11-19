# Backrow — kickoff guide

Read this once, then follow it top to bottom. Everything under "Before Claude Code" you do by hand. Everything after that is a prompt you paste into Claude Code, one session at a time.

## 0. Before Claude Code (one-time, by hand)

1. **Domain and applicationId.** Done: the domain is `backrow.dk`, so the applicationId is `dk.backrow` (ADR-0001). It can never change after the first public release without every user reinstalling. Keep the domain renewed.
2. **GitHub.** Create an organisation (`backrow-tv`) and an empty repository `backrow` inside it. If the organisation already exists under an older name, rename both under Settings; GitHub redirects the old links. When creating it, choose the **Apache License 2.0** template so `LICENSE` is correct from the first commit. Turn on the Issues, Discussions and Security tabs.
3. **Copy this kit** into the cloned repo root: `CLAUDE.md`, `KICKOFF.md`, `docs/`, `.github/`, `CONTRIBUTING.md`, `SECURITY.md`, `NOTICE`, `.gitignore`. The org name, copyright line and applicationId are already filled in. Commit: `chore: add project charter and ADRs`.
4. **Tools on your computer.** Android Studio (latest stable; it installs the Android SDK, `adb` and a JDK 21 runtime), Git, and Claude Desktop (Code tab) or the Claude Code CLI. Claude Code docs: https://docs.claude.com/en/docs/claude-code/overview
5. **Test devices.** At least one weak and one strong device. Ideal pair: a Fire TV Stick 2nd gen or Basic Edition (1 GB RAM, Fire OS 5) and a Fire TV Stick 4K / 4K Max or a Chromecast with Google TV.
   - Fire TV: Settings → My Fire TV → Developer options → turn on **ADB debugging** and **Apps from Unknown Sources**. The IP is under Settings → My Fire TV → About → Network.
   - Android TV / Google TV: Settings → System → About → tap "Android TV OS build" repeatedly, then Developer options → enable USB debugging (and network debugging where offered).
   - From your computer: `adb connect <ip>:5555`, then accept the prompt on the TV.
6. **Signing key: not yet.** Do not create a release keystore on your laptop. Session 3 sets up the CI-only signing described in ADR-0007.

## 1. Open the project in Claude Code

Claude Desktop → **Code** tab → new session → choose the `backrow` folder. (CLI: `cd backrow && claude`.) Claude Code reads `CLAUDE.md` from the repo root automatically at the start of every session.

First prompt. Make it prove it read the rules; no code yet:

> Read CLAUDE.md, docs/ROADMAP.md, docs/COMPATIBILITY.md and every file in docs/adr/. In your own words: which hard rules are you most likely to break in this codebase, which module is the only one allowed to talk to YouTube, and what is still undecided? Then list the questions you need answered before scaffolding. Do not write any code.

Answer its questions (mainly which test devices you own).

## 2. Session 1 — scaffold

> Scaffold the Backrow Android project exactly as described in CLAUDE.md and ADR-0004. Plan first and wait for my approval before touching files. Requirements:
> - Kotlin DSL, Gradle version catalog, JDK 21 toolchain, latest stable AGP/Gradle/Kotlin, minSdk 22 (provisional), targetSdk and compileSdk latest stable, core library desugaring.
> - Modules: :app, :core:model, :core:innertube, :core:player, :core:sponsorblock, :core:data, each with a placeholder class and one passing unit test. :core:innertube, :core:sponsorblock and :core:model must be plain Kotlin/JVM modules with no Android dependency.
> - :app: Compose for TV. A Home screen with three horizontal rows of twelve placeholder cards, a visible focus state, focus restored to the previous card when returning to Home, a Settings screen opened by the Menu key, the LEANBACK_LAUNCHER intent filter and a TV banner so the app appears in the Fire TV and Google TV launchers. Debug builds use the .debug applicationId suffix.
> - The Licensee Gradle plugin (app.cash.licensee) with an allow-list of Apache-2.0, MIT, BSD-2-Clause, BSD-3-Clause, ISC, EPL-1.0, EPL-2.0, MPL-2.0 and Unlicense, failing the build on anything else. Wire it into .github/workflows/ci.yml.
> - A proper Android .gitignore that also ignores reference/.
> - Clone the four reference repositories listed in CLAUDE.md into reference/ and report each one's LICENSE file.
> When done, install the app on both test devices with adb, measure cold start on the weak device, and write the numbers into docs/DEVICE-QUIRKS.md.

Acceptance: CI is green on GitHub; the app shows in both launchers; focus is never lost; cold-start numbers are in `docs/DEVICE-QUIRKS.md`.

## 3. Session 2 — the spike: play one video

> Implement the minimum path to play a public YouTube video, following ADR-0005. Plan first. Use reference/yt-dlp (Unlicense) and reference/MediaServiceCore (MIT) as the sources for InnerTube request shapes, never memory, and tell me which files you used. Requirements:
> - :core:innertube: a PlayerRepository that, given a video id, returns stream metadata (adaptive formats with itag, mime type and codec, resolution, bitrate, URL) for an anonymous TV client, with an ordered fallback chain of client profiles kept in one file.
> - :core:player: a DASH manifest builder that turns those formats into a manifest Media3 plays; prefer hardware decoders, exclude software VP9/AV1 on devices without hardware support, choose buffer sizes from the device's memory class.
> - :app: selecting any placeholder card plays a hardcoded video id full screen; play/pause and ±10 s seek with the remote; Back returns to Home with focus restored.
> - Unit tests for the manifest builder and for response parsing, using sanitised fixtures recorded into src/test/resources. No network in unit tests.
> Done means: plays at 1080p with audio on the strong device and at 720p or better on the weak one, seeking works, time from selection to first frame measured on both and written into docs/DEVICE-QUIRKS.md.

If the spike shows that stream URLs need YouTube's player JavaScript executed (signature or n-parameter deciphering), stop and ask Claude Code to write ADR-0009 with options before implementing anything.

## 4. Session 3 — CI hardening, canary, release design

> Implement ADR-0007 in two parts. Part A now: a nightly canary.yml workflow that runs the live-YouTube integration test (skipped locally) and opens or updates a GitHub issue labelled youtube-breakage when it fails. Part B as a written proposal only, in docs/RELEASE-PROCESS.md: the release workflow (tag → CI build → signing from repository secrets → SHA-256 checksums → GitHub Release with build provenance attestations → beta and stable channels), plus the exact manual steps for me to create the keystore on a clean machine and store it so it never sits on my laptop's disk after upload. I will review the proposal before you implement it.

## 5. Sessions 4+ — v1 features, in this order

One session each, with a plan, tests and a device check:

1. Anonymous Home (trending and regional rows) and channel pages.
2. Search with an in-app on-screen keyboard (not the system keyboard) and suggestions.
3. Player polish: quality and codec picker, subtitles, playback speed without frame drops, auto-quality on by default, seek-back without rebuffering.
4. SponsorBlock (ADR-0008): skip, mute, highlight, chapters; toast with vote buttons; settings screen.
5. Local subscriptions and history without an account, Google Takeout import.
6. TV-code sign-in and account feeds (subscriptions, history, playlists); tokens in Keystore-backed encrypted storage.
7. In-app updater (ADR-0007), beta and stable channels.

## 6. Rules for you, the human in the loop

- One slice per session. If a session drifts, stop and start a new one with a tighter prompt.
- Read every diff. If you can't follow one, ask: "Explain this change to a reviewer who reads code slowly. What could break?"
- For anything touching `:core:innertube`, the manifest builder, tokens or the updater, ask: "Which reference file did you base this on?" and check that the file exists and says what it claims.
- Refuse any suggestion to add a GPL/AGPL dependency or Google Play Services, however convenient.
- Never paste tokens, cookies or a keystore into a prompt.
- Commit at the end of every green slice. Tag no release until Session 3 is complete.
