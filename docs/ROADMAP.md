# Roadmap

Scope discipline is the main lever a small project has. Anything not listed under a milestone is out of scope until an ADR or a roadmap change says otherwise.

## v1 — "better than SmartTube on the things that matter"

Must ship, in roughly this order (one KICKOFF session each):

1. Anonymous Home: trending and regional rows, channel pages, related videos.
2. Search with an in-app on-screen keyboard and suggestions; voice search only where the platform provides it for free.
3. Player: auto quality on by default, quality and codec picker, subtitles, playback speed without frame drops, seek-back without rebuffering, chapters, HDR pass-through where the device supports it.
4. SponsorBlock: skip, mute, highlight, chapters, vote toast, category settings, custom server URL.
5. Local subscriptions and watch history without an account; Google Takeout import.
6. TV-code sign-in; account subscriptions feed, history and playlists; sign-out that wipes tokens.
7. In-app updater with beta and stable channels; About screen with attributions and the "not affiliated" statement.
8. Settings: video presets per device, buffer policy, region and language, SponsorBlock categories, privacy toggles.

Quality gates for v1: the performance budget in CLAUDE.md on the weak reference device; every screen usable with D-pad only; unit tests on the InnerTube, manifest and SponsorBlock layers; nightly canary green for two weeks before the first stable tag.

## v1.x

- DeArrow (crowdsourced titles and thumbnails).
- Return YouTube Dislike.
- Profiles (multiple local users) and a kid mode with allow-listed channels.
- Companion phone/web app: typing, subscription management, SponsorBlock submissions, settings sync over the local network.
- Casting from the YouTube phone app (Lounge API; investigate DIAL discovery so phones find the TV automatically).

## v2 (only after v1.x is stable)

- Additional sources behind a plugin interface (PeerTube, Twitch, podcasts).
- Web build for Tizen/webOS, reusing `:core:*` models and the InnerTube knowledge.
- Optional proxy backend support (Invidious/Piped-style) as a fallback.

## Non-goals (do not open issues for these)

- Phones and tablets, touch input, portrait mode.
- Vega OS Fire TV, Roku, Apple TV, Vizio, VIDAA.
- Downloading videos.
- Comments and live chat (revisit after v1.x if there is a maintainer for it).
- Showing ads, or whitelisting channels for ads.
- Any telemetry that is not explicit opt-in.
