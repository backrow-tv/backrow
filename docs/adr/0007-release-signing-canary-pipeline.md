# ADR-0007: Release, signing and canary pipeline
2026-09-22 · Accepted

## Context
In late 2025 SmartTube's maintainer built and signed releases on a personal machine that had been infected; official builds shipped with malware, the signing key was compromised, and Google Play Protect and Amazon removed the app from users' devices. A sideloaded app has no store to vouch for it, so the project must make its own integrity verifiable. Separately, YouTube breaks unofficial clients without notice, and users should not be the ones who discover it.

## Decision
**Builds**
- Release APKs are built **only** by GitHub Actions from a tagged commit. No release artifact is ever produced on a maintainer's machine.
- Builds are reproducible (deterministic APKs from the tag) so F-Droid and anyone else can verify that the published APK matches the source.
- CI publishes SHA-256 checksums and build-provenance attestations with every release.
- Third-party GitHub Actions are pinned to commit SHAs before the first release.

**Signing**
- The release keystore is generated once on a clean machine, stored as a repository secret and in the maintainer's password manager, and deleted from the machine that generated it. It never lives on a development machine.
- The maintainer's GitHub account and the organisation require hardware-key two-factor authentication; release workflows use a protected environment that requires a manual approval.
- Compromise playbook (written before the first release): revoke, rotate, announce within 24 hours, publish indicators, and accept that a compromised key means a new applicationId (ADR-0001).

**Channels and updates**
- Two channels: **beta** (every merged slice that passes CI) and **stable** (tagged, hand-tested on the device matrix).
- The in-app updater downloads only from the project's GitHub Releases over HTTPS, verifies the SHA-256 published with the release, and relies on Android's signature-continuity check for installation. It never installs anything from a redirect or a mirror.
- `CHANGELOG.md` lives in the repo; release notes are generated from it, not from a chat channel.

**Canary**
- A nightly `canary.yml` workflow runs the live-YouTube integration tests (browse, search, resolve and start playing one video per client profile) and opens or updates a `youtube-breakage` issue on failure. This is how breakage is discovered, before user reports.

## Consequences
- Release cadence is limited by CI and the device matrix, not by the maintainer's laptop.
- Users and F-Droid can verify releases; the README explains how.
- The updater is security-critical code and follows CLAUDE.md hard rule 7 (tests) and the human review rule in KICKOFF.md.

## Alternatives considered
- Building locally and scanning with VirusTotal: scanning is not a control; it is what SmartTube added after the fact.
- Publishing through app stores: not available to a client of this kind.
