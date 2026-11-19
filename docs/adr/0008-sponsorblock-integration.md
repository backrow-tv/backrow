# ADR-0008: SponsorBlock integration
2026-09-22 · Accepted

## Context
SponsorBlock (https://sponsor.ajay.app) is a crowdsourced database of video segments (sponsors, intros, outros, self-promotion, interaction reminders, previews, non-music sections, filler, highlights, chapters) with a public API. Its API docs (https://wiki.sponsor.ajay.app/w/API_Docs) define a privacy-preserving lookup by hashed video id, a view-reporting endpoint, voting, and submission rules. The API and database are licensed for attribution and non-commercial use; the maintainer asks to be told when the API is used in a new client.

## Decision
- **Module:** `:core:sponsorblock`, pure Kotlin/JVM, one `SponsorBlockClient` interface, OkHttp implementation, fixtures for tests.
- **Lookup:** `GET /api/skipSegments/{sha256HashPrefix}` with the first 4 characters of the SHA-256 of the video id, so the server cannot tell exactly which video is being watched. Categories and action types (`skip`, `mute`, `full`, `poi`, `chapter`) come from user settings. A 404 means "no segments", not an error. Results are cached per video id for the session so seeking never refetches.
- **Playback behaviour:** skip segments trigger a seek with a small toast ("Skipped sponsor"); mute segments mute audio for the segment; `poi` highlight is exposed as "jump to highlight"; chapters appear in the seek bar. Per-category defaults mirror the official extension (sponsor on by default, others off or "show only").
- **View reporting:** after a skip, `POST /api/viewedVideoSponsorTime` with the segment UUID, so the project's usage counts in SponsorBlock's stats.
- **Voting (v1):** the skip toast offers upvote/downvote via `POST /api/voteOnSponsorTime`. The local user id is a random string of at least 32 characters generated on first use, stored in `:core:data`, never exported, never logged. The public id derived from it is only shown in a stats screen if the user asks.
- **Submission (later, via the companion app):** requires precise start/end times, which a TV remote cannot provide comfortably. The API requires a client identifier of the form `Backrow/<version>` on submissions; the client sends that identifier on every call.
- **Server:** default `https://sponsor.ajay.app`, user-configurable for mirrors or self-hosting. Database mirrors do not serve the API; the settings screen says so.
- **Attribution:** the About screen and README credit SponsorBlock and link to it; the maintainer opens a GitHub issue on the SponsorBlock repo to announce the client, as the API docs request.
- **DeArrow** (same author, same API style) is planned for v1.x, not v1.

## Consequences
- SponsorBlock works in anonymous mode by design; nothing about it depends on a Google account.
- Category settings, the toast design and the seek-bar markers are v1 UI work (KICKOFF.md session 4 of the feature list).
- A commercial fork would need its own arrangement with SponsorBlock; not Backrow's problem to solve.

## Alternatives considered
- Downloading the full database for offline lookup: too large for a TV device; a mirror URL setting covers the self-hosting use case.
- Unhashed `GET /api/skipSegments?videoID=`: simpler, leaks the exact video to the server; rejected.
