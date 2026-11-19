# ADR-0001: Name, identity and applicationId
2026-09-22 · Accepted · amended 2026-11-19 (name changed from the working title "Vesper" before any release; applicationId fixed)

## Context
The project needs a name that cannot be confused with YouTube or with existing clients (SmartTube, FreeTube, LibreTube, NewPipe, Yattee), and an Android applicationId that never changes. SmartTube had to change its applicationId after its signing key was compromised in late 2025, forcing every user to reinstall.

## Decision
- The name is **Backrow**.
- No "Tube", "YT", "Smart" or any Google/YouTube trademark appears in the name, package, launcher label, banner or description.
- The project domain is **backrow.dk**, owned by the maintainer. The applicationId is **`dk.backrow`** and never changes. The Kotlin package prefix is the same. Debug builds add the `.debug` suffix so debug and release can coexist on one device.
- Backrow describes itself as "a media client for Android TV" and states in the README, the About screen and the repository description that it is not affiliated with or endorsed by Google or YouTube.
- Branding (icon, TV banner, palette) is original work under the project license. No red-and-white play-button imagery.

## Consequences
- A trademark check is done before the first public release. The domain must be kept renewed: losing backrow.dk would not break installed apps, but it would hand the reverse-domain identity to someone else.
- .dk whois shows the registrant publicly, so the project is tied to a named registrant.
- Changing the applicationId later equals shipping a new app, so the signing key is protected as described in ADR-0007.

## Alternatives considered
- "Vesper" (the working title until November 2026), "Tenfoot", "Recline", "Matinee": fine names, Backrow was preferred.
- A name containing "Tube": rejected, invites trademark trouble and confusion with SmartTube.
