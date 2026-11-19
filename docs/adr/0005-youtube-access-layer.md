# ADR-0005: YouTube access layer
2026-09-22 · Accepted

## Context
YouTube offers no official API that permits a client like Backrow. Clients use the internal "InnerTube" endpoints that YouTube's own apps use, and stream URLs that may need deciphering steps derived from YouTube's player JavaScript. YouTube changes these often and actively blocks unofficial clients (2024–2026: bot checks, client-specific stream blocks, "content unavailable" errors, action against background playback).

Existing libraries: NewPipeExtractor (GPLv3, no account support), YouTube.js (MIT, TypeScript), yt-dlp (Unlicense, Python, the best-maintained reference), MediaServiceCore (MIT, Java, TV-oriented, single maintainer, used by SmartTube).

## Decision
1. Backrow owns a Kotlin/JVM InnerTube module, **`:core:innertube`**, written against the current behaviour documented in permissively licensed references (yt-dlp, YouTube.js, MediaServiceCore). Nothing is derived from GPL/AGPL code (ADR-0002).
2. **Client identity is data, not architecture.** Which InnerTube client Backrow presents as (name, version, headers, context) lives in one file as a list of profiles with an ordered fallback chain for playback. Adding or reordering a profile is a small change with a fixture-backed test, because this is what breaks most often.
3. **Anonymous mode is first-class.** Browse, search, playback and SponsorBlock work without an account. Account features (`AccountRepository`: TV-code sign-in via the device-code flow, subscriptions feed, history, playlists) live in a separate package. Tokens are stored in Keystore-backed encrypted storage and never logged.
4. **Every response shape Backrow depends on has a sanitised fixture** in `src/test/resources` and a parser test. Live checks run only in the nightly canary (ADR-0007).
5. **Breakage is handled with hotfix releases** through the updater channel, not by silently degrading.
6. Backrow contains no code that fetches, renders or counts YouTube ads. If YouTube deploys server-inserted ad segments broadly, handling them needs its own ADR.

## Consequences
- More up-front work than pulling in a library; in exchange, license consistency, TV-client parity, and no external bus factor.
- If stream URLs require executing YouTube's player JavaScript (signature or n-parameter deciphering), a follow-up ADR (0009) chooses between a JVM JS engine (QuickJS via JNI, Rhino) and a purpose-built interpreter as yt-dlp uses. Do not decide this before the Session 2 spike shows what is needed.
- Users who sign in accept a risk of enforcement against their Google account; the app says so once, plainly, at sign-in.
- When Claude Code writes InnerTube code, it must cite the reference file it used (CLAUDE.md hard rule 4).

## Alternatives considered
- NewPipeExtractor: mature, but GPLv3 and account-less.
- YouTube.js inside a JS engine on Android: viable and would share one extractor with a later web build; rejected for v1 as heavier on 1 GB devices and harder to debug. Revisit if a web build happens.
- Proxy backends (Invidious/Piped): possible optional fallback later; never a dependency. Instances get blocked, and it moves the arms race to servers Backrow doesn't run.
