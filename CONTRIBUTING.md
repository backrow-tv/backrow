# Contributing to Backrow

Thanks for considering it. A few things to know before you start.

## What this project is, plainly

Backrow is a media client for YouTube on Android TV, Google TV and Fire OS. It uses YouTube's internal endpoints, which YouTube's terms of service do not permit. Google has sent takedown notices to similar projects and actively blocks unofficial clients. The project may have to change course or stop. Contribute with that in mind.

## How the code gets written

Most of Backrow's code is written with Claude Code, directed and reviewed by the maintainer, under the rules in `CLAUDE.md` and the decisions in `docs/adr/`. Human-written contributions are welcome and held to the same rules. If you use an AI tool, say so in the PR; if you don't, say that too. Either is fine; hiding it is not.

## Ground rules

- Read `CLAUDE.md` and `docs/adr/` first. Decisions recorded there are not reopened in PR comments; open an issue proposing a new ADR instead.
- **Licensing:** Apache-2.0 only. No GPL/AGPL/LGPL dependencies and no code copied or adapted from such projects (NewPipe, NewPipeExtractor, Invidious, Piped, and others). CI enforces the dependency side; reviewers enforce the copied-code side.
- **Sign your commits** with the Developer Certificate of Origin: `git commit -s`. No CLA.
- **Scope:** features outside `docs/ROADMAP.md` need an issue and agreement before code.
- **Small PRs**, one concern each, conventional commit messages (`feat:`, `fix:`, `chore:`, `docs:`, `test:`, `refactor:`).
- **Tests** are required for anything touching `:core:innertube`, the DASH manifest builder, token storage or the updater. Unit tests must not use the network.
- **Remote-only UI:** every screen must work with D-pad, Select and Back. Say which device you tested on.
- Never include tokens, cookies, keystores or personal video history in fixtures, screenshots or logs.

## Working on YouTube internals

Do not write InnerTube request code from memory or from a model's memory. Base it on the permissively licensed references listed in `CLAUDE.md` and cite the file in the PR. When YouTube changes something, add a sanitised fixture that reproduces the new response and a dated note in `docs/INNERTUBE-CHANGELOG.md`.

## Building

See the Commands section of `CLAUDE.md`. Requirements: Android Studio (latest stable), JDK 21, adb access to a TV device or the Android TV emulator.

## Reviews and releases

The maintainer reviews every PR. Reviews focus on behaviour, tests and the rules above. Releases are built only by CI (see `docs/adr/0007-release-signing-canary-pipeline.md`); no contributor, including the maintainer, builds release artifacts locally.

## Conduct

Be decent. The project follows the Contributor Covenant (https://www.contributor-covenant.org/). Report problems privately to the maintainer via the contact in `SECURITY.md`.
