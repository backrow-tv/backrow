<!--
Copyright 2026 The Backrow contributors
SPDX-License-Identifier: Apache-2.0
-->

# Release process (proposal)

**Status: implemented** (`.github/workflows/release.yml`). What is left is the maintainer's: the
repository settings in §3.2 and the keystore in §4. No release is tagged before both are done
(KICKOFF.md §6). Decisions on the review's open questions are in §6.

## 1. Why this is strict

In 2025, SmartTube's releases were built and signed on the maintainer's personal machine. That
machine was infected, the official builds shipped with malware, and the signing key was
compromised. A sideloaded app has no store vouching for it, so users can only trust Backrow if
three things hold. The release key never exists on a development machine. Releases are built
only by CI from a tagged commit. Anyone can check that a downloaded APK came from that CI run.

What this design protects against:

| Threat | Control |
|---|---|
| Maintainer's laptop is compromised | Key never on the laptop (§4); builds only in CI (§2) |
| A malicious PR tries to read the key | Secrets live in the `release` environment, which only tag-triggered jobs on protected tags can use, after a manual approval |
| A poisoned Gradle cache from a PR build | Release jobs run with the Gradle cache disabled |
| A tampered third-party action | Every `uses:` pinned to a commit SHA |
| A tampered download or mirror | SHA-256 checksums and build-provenance attestations; the updater checks the SHA-256; Android checks signature continuity |
| GitHub account takeover | Hardware-key 2FA on the maintainer account and the org; tag protection; required approval |

What it does not protect against: a malicious change that the maintainer merges, or a
compromise of GitHub itself. Code review and reproducible builds (§3.3) are the answer to the
first. The second is out of scope.

## 2. The release workflow, step by step

A new file, `.github/workflows/release.yml`, is triggered by `push: tags: ["v*"]`. Tags matching
`v*` are protected: only the maintainer can create them (repository rules → tag rulesets).

```
tag vX.Y.Z[-beta.N]
  └─ job build   (no secrets, read-only token)
       checkout tag → JDK 21 → ./gradlew lint testDebugUnitTest licensee assembleRelease
       → unsigned APK uploaded as a workflow artifact, with its SHA-256
  └─ job rebuild (no secrets, in parallel)
       same build from a clean checkout → compares the unsigned APK byte for byte (§3.3)
  └─ job sign    (environment: release → waits for the maintainer's approval)
       needs build + rebuild
       decode keystore secret into $RUNNER_TEMP → zipalign check → apksigner sign
       → apksigner verify; certificate digest must equal vars.RELEASE_CERT_SHA256
       → delete keystore → SHA256SUMS → build-provenance attestation
       → GitHub Release (prerelease for -beta.N)
```

### 2.1 Job `build`
- `actions/checkout` at the tag, `persist-credentials: false`.
- `gradle/actions/setup-gradle` with `cache-disabled: true`. A release must never read a cache
  that a PR build could have written.
- The same checks as CI: `lint testDebugUnitTest licensee`, then `assembleRelease`. The release
  build type has **no signing config**, so Gradle produces an unsigned APK (R8 full mode, as
  ADR-0004 asks).
- `versionName` and `versionCode` come from the tag. Gradle reads the tag through a property the
  workflow passes in (`-Pbackrow.version=1.4.0-beta.2`). The build fails if the property is
  missing on a release build, or if it doesn't match `^\d+\.\d+\.\d+(-beta\.\d+)?$`.
- `versionCode = major·1 000 000 + minor·10 000 + patch·100 + n`, where `n` is the beta number
  (1–98) and 99 for stable. So `1.4.0-beta.2` = 1 040 002 and `1.4.0` = 1 040 099. Codes only
  ever go up, and a stable release always updates the betas before it.

### 2.2 Job `sign` (the only job that sees secrets)
- `environment: release`. The environment has the maintainer as required reviewer, and its
  deployment policy allows only the protected `v*` tags and the `main` branch (`main` for the dry
  run, §4 step 10). Its secrets:

  | Secret | Content |
  |---|---|
  | `RELEASE_KEYSTORE_BASE64` | the PKCS#12 keystore, base64 |
  | `RELEASE_KEYSTORE_PASSWORD` | its password (PKCS#12: also the key password) |
  | `RELEASE_KEY_ALIAS` | `backrow` |

  Plus one environment **variable** (not secret, it is public): `RELEASE_CERT_SHA256`, the
  signing certificate's SHA-256 digest from §4 step 6.
- The keystore is written to `$RUNNER_TEMP/release.p12` with `umask 077` and removed in an
  `if: always()` step. GitHub-hosted runners are single-use VMs, so this is belt and braces.
- Passwords reach `apksigner` through environment variables (`--ks-pass env:...`), never on the
  command line. Nothing in the job echoes them, and GitHub masks them in logs anyway.
- Sign with `apksigner sign --v1-signing-enabled false --v2-signing-enabled true
  --v3-signing-enabled true`. `minSdk = 25` is above API 24, so the old JAR signature (v1) is not
  needed. v3 makes key rotation possible later (§5).
- `apksigner verify --print-certs` must show exactly one signer, whose SHA-256 equals
  `RELEASE_CERT_SHA256`. Otherwise the job fails. This catches a swapped or wrong secret.
- `sha256sum backrow-<version>.apk > SHA256SUMS`.
- `actions/attest-build-provenance` (v4.2.2 at the time of writing, pinned by SHA) on the signed
  APK. Permissions for this job only: `contents: write` (create the release),
  `id-token: write` and `attestations: write`.
- `gh release create vX.Y.Z --verify-tag --notes-file <notes>` with the APK and `SHA256SUMS`.
  `--prerelease` for `-beta.N` tags. The notes are the tag's section of `CHANGELOG.md`, extracted
  by a script, and the job fails if that section is missing (ADR-0007: notes come from the repo).

### 2.3 Channels
- **Beta**: a prerelease, tagged `vX.Y.Z-beta.N`. ADR-0007 says every merged slice that passes CI
  goes to beta. Decision: the maintainer tags a beta by hand after merging a slice
  (`git tag -a vX.Y.Z-beta.N -m "Backrow X.Y.Z-beta.N"`, `git push origin vX.Y.Z-beta.N`).
  Tagging automatically from `main` is possible (a `workflow_run` on CI success), but it would
  make the approval in §2.2 a daily chore.
- **Stable**: a plain `vX.Y.Z` tag on a commit that already shipped as a beta and was
  hand-tested on the device matrix (docs/COMPATIBILITY.md).
- Both channels use one applicationId (`dk.backrow`) and one key, so users can switch channels
  without reinstalling. The in-app updater (Session 4+ step 7) reads
  `https://api.github.com/repos/backrow-tv/backrow/releases`. The stable channel takes the newest
  non-prerelease; beta takes the newest of either. It downloads the asset over HTTPS from
  github.com only, checks it against `SHA256SUMS` from the same release, and relies on Android's
  signature check at install.

### 2.4 How a user verifies a download
To be published in the README once the first release exists:

```sh
sha256sum -c SHA256SUMS                                     # file matches what CI published
gh attestation verify backrow-1.4.0.apk -R backrow-tv/backrow   # built by this repo's release.yml from the tag
apksigner verify --print-certs backrow-1.4.0.apk            # SHA-256 digest equals the one in the README
```

## 3. Supporting changes the implementation PR includes
1. **Action pinning** (ADR-0007). Every `uses:` in `ci.yml`, `canary.yml` and `release.yml` is
   pinned to a commit SHA, with a version comment. The canary already does this. Renovate keeps
   the pins current.
2. **Repository settings**, done by the maintainer and listed in the PR: tag ruleset for `v*`,
   the `release` environment with a required reviewer and a tag-only deployment policy, branch
   protection on `main`, and hardware-key 2FA required for the org.
3. **Reproducible builds.** AGP release builds are close to deterministic already. The PR sets
   `isPreserveFileTimestamps = false` and `isReproducibleFileOrder = true` on archive tasks,
   pins the JDK distribution and version in the workflow, and adds the `rebuild` job, which
   builds the tag twice and fails on any difference in the unsigned APK. Third parties (F-Droid)
   can then rebuild and compare with `apksigcopier`, which copies our signature onto their build.
4. **`CHANGELOG.md`** ("Keep a Changelog" layout) and a small script that extracts one version's
   section.
5. **Compromise playbook** in `SECURITY.md`, written before the first release (ADR-0007), see §5.

## 4. Creating the release keystore (maintainer, once)

Goal: the private key exists only (a) in the repository's `release` environment secrets and (b)
in your password manager. It is created on a system that runs entirely from RAM and is switched
off afterwards, so it never touches the disk of your laptop or any other machine.

You need:
- a USB stick (8 GB or more) for a live Linux system;
- your password manager's account and its 2FA;
- your GitHub hardware security key;
- about 30 minutes.

**Step 1 — make the live USB (on any machine).**
Download the current Ubuntu Desktop LTS ISO from ubuntu.com. Verify it against Ubuntu's
`SHA256SUMS` *and* that file's GPG signature (ubuntu.com → "How to verify your Ubuntu download").
Write it to the USB stick (balenaEtcher, Rufus or `dd`). This step handles no secrets.

**Step 2 — boot it, without installing and without your disks.**
A spare computer is best. Your laptop is acceptable if you boot it from the USB stick (firmware
boot menu) and choose **"Try Ubuntu"**, never "Install". Don't open or mount the internal disk;
Ubuntu's live session doesn't mount it unless you click it in the file manager. Everything you
do from here lives in RAM and is gone when the machine powers off. Leave networking **off** for
steps 3–6 (no Wi-Fi password, cable unplugged).

**Step 3 — open a terminal and work in RAM only.**
```sh
cd /dev/shm            # tmpfs: RAM, never written to a disk
umask 077
```

**Step 4 — choose the keystore password.** Generate it rather than inventing it:
```sh
openssl rand -base64 32
```
Write it on paper for the next 20 minutes. It goes into the password manager in step 8, and the
paper is destroyed in step 11.

**Step 5 — create the key and certificate.** OpenSSL is preinstalled on Ubuntu, so no JDK is
needed. RSA 4096, valid for about 27 years (Android only checks validity at install time, but
expiry would still block updates):
```sh
openssl req -x509 -newkey rsa:4096 -sha256 -days 10000 -noenc \
  -subj "/CN=Backrow/O=The Backrow contributors" \
  -keyout backrow-release.key -out backrow-release.crt

openssl pkcs12 -export -name backrow \
  -inkey backrow-release.key -in backrow-release.crt \
  -out backrow-release.p12
# enter the password from step 4 twice

shred -u backrow-release.key      # the unencrypted key is no longer needed
```
OpenSSL 3 writes PKCS#12 with AES-256 and PBKDF2, which `apksigner` on JDK 21 reads. Checked on
2026-11-24 with a throwaway key made with exactly these commands (OpenSSL 3.5): signing and the
digest check passed. No `-legacy` needed.

**Step 6 — record the certificate digest (public).**
```sh
openssl x509 -in backrow-release.crt -noout -fingerprint -sha256
```
Note the `SHA256 Fingerprint=…` value. It is public: it goes into the `RELEASE_CERT_SHA256`
variable, the README and every release note. Remove the colons for the variable.

**Step 7 — prepare the secret value.**
```sh
base64 -w0 backrow-release.p12 > backrow-release.p12.b64
sha256sum backrow-release.p12   # note the first 8 characters, to check the backup in step 8
```

**Step 8 — connect to the network and store the backup in your password manager.**
Turn on networking. Open Firefox in the live session and sign in to your password manager's web
vault. Create an item "Backrow release keystore" containing:
- the password from step 4;
- the alias `backrow`;
- the certificate SHA-256 from step 6;
- the file `backrow-release.p12` as an attachment (Firefox's upload dialog can browse `/dev/shm`
  via "Other Locations → Computer → dev → shm");
- the date and the 8 hash characters from step 7.

**Step 9 — store the secrets in GitHub.**
In the same Firefox, sign in to GitHub with your hardware key. Go to
`backrow-tv/backrow` → Settings → Environments → `release` (create it first if the
implementation PR's checklist hasn't). Add:
- secret `RELEASE_KEYSTORE_BASE64`: open `backrow-release.p12.b64` with
  `gnome-text-editor /dev/shm/backrow-release.p12.b64`, select all, copy, paste;
- secret `RELEASE_KEYSTORE_PASSWORD`: the password from step 4;
- secret `RELEASE_KEY_ALIAS`: `backrow`;
- variable `RELEASE_CERT_SHA256`: the digest from step 6, without colons.

Then sign out of GitHub and the password manager in Firefox.

**Step 10 — verify before you power off.** From another device (your laptop, normally booted),
start the **Release** workflow by hand on `main` (Actions → Release → Run workflow, or
`gh workflow run release.yml --ref main`) and approve it. Run by hand it is a dry run: it builds
a release APK twice, signs it in the `release` environment with the real secrets, checks the
certificate digest against `RELEASE_CERT_SHA256`, and uploads and publishes nothing. If it fails, you still have everything in
RAM to fix the secrets. If the live machine has already been switched off, the password manager
backup is enough to redo step 9 from another live session.

**Step 11 — destroy the working copies.**
```sh
shred -u /dev/shm/backrow-release.*
```
Power the machine off (not suspend). RAM is cleared, nothing was written to a disk. Destroy the
paper from step 4.

What exists afterwards:
- the GitHub environment secrets, which can't be read back, only used by the `release`
  environment;
- the password manager item.

Nothing else.

## 5. Compromise playbook (to go into SECURITY.md)

If the key, the maintainer's GitHub account or a release might be compromised:

1. **Within 1 hour:** revoke access. Delete the `release` environment secrets, rotate the
   maintainer's GitHub credentials, and review the org's members and tokens.
2. **Within 24 hours:** announce on the README, a pinned issue and the release page. Say which
   releases are affected, the SHA-256 of every known-bad APK, and what users should do.
3. **Mark bad releases.** Edit them to say so, delete their assets, and keep the notes for the
   record.
4. **New key.** If the key itself leaked, a new key means a new applicationId (ADR-0001). Users
   must uninstall and install the new app; Android won't update across keys. APK Signature
   Scheme v3 rotation (`apksigner rotate`) only helps if the *old* key is still trusted and not
   leaked, so it is for planned rotation, not for compromise.
5. Publish indicators (hashes, attestation IDs, dates) and a post-mortem.

## 6. Decisions on the review's open questions (2026-11-24)
1. **Beta tagging:** manual tags (§2.3). Automatic tagging can come later if the approval step
   turns out cheap enough.
2. **Password manager attachments:** assumed (Bitwarden, 1Password and KeePassXC support them).
   If yours does not, the fallback is a second USB stick holding an `age`-encrypted copy, kept
   offline. Say so before the ceremony; it changes step 8.
3. **Second approver for stable:** not now, with one maintainer. Add the second maintainer as a
   required reviewer of the `release` environment when there is one.
