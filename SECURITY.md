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

## Verifying a release

Every release has `SHA256SUMS` and a build-provenance attestation. With the APK and `SHA256SUMS` downloaded from the release page:

```sh
sha256sum -c SHA256SUMS
gh attestation verify backrow-<version>.apk -R backrow-tv/backrow
apksigner verify --print-certs backrow-<version>.apk
```

The `SHA-256 digest` that `apksigner` prints must equal the release certificate digest in the README. Android also refuses an update signed by any other key.

## If the signing key, the maintainer's account or a release is ever compromised

We will not quietly rotate and hope. The playbook, written before the first release (ADR-0007):

1. **Within 1 hour:** revoke access. Delete the `release` environment's secrets, rotate the maintainer's GitHub credentials and hardware keys, and review the organisation's members, deploy keys and tokens.
2. **Within 24 hours:** announce on the README, in a pinned issue and on the releases page. Say which releases are affected, give the SHA-256 of every known-bad APK, and say what users should do.
3. **Mark bad releases.** Say so in their notes, delete their assets, and keep the notes as a record.
4. **New key.** If the key itself leaked, ship under a new applicationId (ADR-0001). Users must uninstall and install the new app, because Android does not update across signing keys. APK Signature Scheme v3 rotation only helps when the old key is still trusted, so it is for planned rotation, never for a compromise.
5. **Publish** indicators (hashes, attestation IDs, dates) and a post-mortem.
