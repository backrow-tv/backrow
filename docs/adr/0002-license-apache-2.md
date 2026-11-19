# ADR-0002: License — Apache License 2.0
2026-09-22 · Accepted

## Context
The maintainer wants a permissive license: anyone may use, modify and redistribute the code as long as attribution is kept. Candidate reference codebases are MIT (SmartTube, MediaServiceCore, YouTube.js), Unlicense (yt-dlp), GPLv3 (NewPipe, NewPipeExtractor) and AGPLv3 (Invidious, Piped). Apache-2.0 adds a patent grant and a NOTICE mechanism that MIT lacks, which matters once there are several contributors.

## Decision
- All code, documentation and assets in this repository are licensed under the **Apache License 2.0**.
- `NOTICE` carries the Backrow attribution and any notices required by code copied from permissive sources.
- Contributors sign off commits under the Developer Certificate of Origin (DCO, `git commit -s`). No CLA.
- Attribution required from forks is exactly what the license says: keep the license text, copyright notices and NOTICE. Backrow does not demand an in-app banner from forks.

## Consequences
- **No GPL, AGPL or LGPL dependencies and no copied or "adapted" code from such projects.** Apache-2.0 cannot absorb copyleft code. NewPipeExtractor is therefore not an option; Backrow owns its InnerTube layer (ADR-0005). The Licensee Gradle plugin fails the build on any license outside the allow-list.
- Code copied from MIT/BSD/ISC/Unlicense sources keeps its header and gets a NOTICE entry.
- Closed-source forks are permitted; that is the accepted trade-off of a permissive license.
- SponsorBlock's data has its own license (attribution, non-commercial); the app credits SponsorBlock in-app (ADR-0008).

## Alternatives considered
- MIT: equally permissive, simpler, no patent grant or NOTICE mechanism. Acceptable, but Apache-2.0 is closer to the maintainer's "keep credit" intent.
- GPLv3: would prevent closed forks and allow NewPipeExtractor, but is not what the maintainer wants.
