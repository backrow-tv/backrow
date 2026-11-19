# ADR-0011: Licence exception for desugar_jdk_libs
2026-11-19 · Accepted

## Context
ADR-0004 and ADR-0006 require core library desugaring, so `java.time` and other newer JDK APIs work on Android 7.1–7.x (API 25), below the API 26 where they appeared natively. Desugaring needs `com.android.tools:desugar_jdk_libs`, which Google publishes under **"GNU General Public License, version 2, with the Classpath Exception"** (stated in its POM, checked 2026-11-19 for version 2.1.5). That code is OpenJDK's class library, re-packaged.

ADR-0002 and CLAUDE.md hard rule 1 forbid GPL dependencies, and the Licensee allow-list (Apache-2.0, MIT, BSD-2/3-Clause, ISC, Unlicense) would reject it. The Classpath Exception is what lets every Java and Android program link the JDK's class library without becoming GPL. It allows linking the library with independent modules and distributing the result under the terms of our choice, provided the library itself stays under GPL-2.0-with-Classpath-Exception. This is the same licence under which every Android app already ships pieces of OpenJDK-derived `java.*` code.

## Decision
- **Allow exactly one artifact, `com.android.tools:desugar_jdk_libs`,** as an exception in the Licensee configuration: an `allowDependency` entry pinned to the version in use, with a `because` note pointing to this ADR. The licence id `GPL-2.0-with-classpath-exception` is **not** added to the allow-list. A version bump (for example by Renovate) re-triggers the check and needs the pinned entry updated in the same PR, so every bump gets a human look.
- The exception covers only this artifact, used only through the `coreLibraryDesugaring` configuration. Backrow code does not copy, modify or adapt any of its source.
- **Attribution and source:** `NOTICE` names the artifact, its licence and its upstream source (https://github.com/google/desugar_jdk_libs). The About screen lists it with the other third-party licences. The release process (ADR-0007, Session 3 proposal) must make the exact corresponding source available with each release, by attaching the upstream source archive for the shipped version or linking to its exact tag. GPLv2 expects the source to come with binary distribution.
- Any other GPL, AGPL or LGPL artifact, including one "with an exception", still needs its own ADR. This ADR is not a precedent for widening the allow-list.

## Consequences
- Desugaring stays on as ADR-0004 intended; screens and data code can use `java.time` on every supported device.
- One more release-time obligation: corresponding source for desugar_jdk_libs ships alongside each release.
- If Licensee does not inspect the `coreLibraryDesugaring` configuration, the exception is still recorded in the build file so it is visible to reviewers; the scaffold PR states which is the case.
- This is the maintainer's licensing judgement, not legal advice; revisit it if the artifact's licence changes or a lawyer advises otherwise.

## Alternatives considered
- No desugaring: avoid `java.time` and friends, or use a separate backport library; adds friction to every date/time call for API 25 only. Rejected.
- `minSdk` 26: removes the need for most of desugaring but drops Fire OS 6 (Fire TV Stick 4K 2018, Cube 1st gen), contradicting ADR-0006. Rejected.
- Adding `GPL-2.0-with-classpath-exception` to the allow-list by licence id: would silently admit any future artifact under it. Rejected in favour of a per-artifact entry.
