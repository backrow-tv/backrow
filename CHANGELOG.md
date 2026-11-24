<!--
Copyright 2026 The Backrow contributors
SPDX-License-Identifier: Apache-2.0
-->

# Changelog

Every user-visible change, newest first, in the format of [Keep a Changelog](https://keepachangelog.com/en/1.1.0/). Release notes are generated from this file (ADR-0007): `release.yml` publishes the section whose heading is the tag's version and fails if there is none.

To release: rename `Unreleased` to the version (`## [0.1.0-beta.1] - 2026-11-25`), add a new empty `Unreleased` above it, merge, then tag (docs/RELEASE-PROCESS.md).

## [Unreleased]

### Added
- Play one YouTube video full screen, anonymously, with play/pause and ±10 s seek from the remote (spike).
- Home screen with placeholder rows and a Settings entry reachable with the D-pad.
- Nightly canary that checks the YouTube player path and reports breakage as a GitHub issue.
