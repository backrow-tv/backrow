# Security policy

## Reporting a vulnerability

Please do not open a public issue for security problems. Use GitHub's private vulnerability reporting on this repository (Security tab → "Report a vulnerability"): https://github.com/backrow-tv/backrow/security/advisories/new

You will get an acknowledgement within 72 hours and a plan within 14 days. Credit is given in the release notes unless you prefer otherwise.

## What Backrow promises about its releases

- Release APKs are built only by GitHub Actions from a tagged commit, never on a personal machine.
- Every release ships SHA-256 checksums and build-provenance attestations; builds are reproducible so anyone can verify the APK matches the source.
- The signing key is stored only as a repository secret and in the maintainer's password manager; it does not exist on any development machine.
- The in-app updater downloads only from this repository's GitHub Releases over HTTPS and verifies checksums before installing.
- Backrow sends no telemetry. It contacts YouTube, SponsorBlock (or the server you configure) and GitHub Releases, nothing else.

If any of these is ever untrue, that is a vulnerability; report it.

## Supported versions

Only the latest stable and the latest beta receive fixes.

## If the signing key is ever compromised

We will say so within 24 hours on the repository README and the releases page, publish indicators, and ship under a new applicationId if the key cannot be trusted (see `docs/adr/0007-release-signing-canary-pipeline.md`). We will not quietly rotate and hope.
