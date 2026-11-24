# Architecture decision records

An ADR records one decision, the context that forced it, and its consequences, so nobody (human or Claude Code) relitigates it every month. Status flows Proposed → Accepted → Superseded by ADR-xxxx. Never edit an accepted ADR's decision; write a new one that supersedes it.

| # | Title | Status |
|---|-------|--------|
| [0001](0001-name-identity-application-id.md) | Name, identity and applicationId | Accepted |
| [0002](0002-license-apache-2.md) | License: Apache-2.0 | Accepted |
| [0003](0003-platform-scope-v1.md) | Platform scope for v1 | Accepted |
| [0004](0004-tech-stack.md) | Tech stack | Accepted |
| [0005](0005-youtube-access-layer.md) | YouTube access layer | Accepted |
| [0006](0006-minsdk-hardware-floor.md) | minSdk and hardware floor | Accepted (minSdk 25) |
| [0007](0007-release-signing-canary-pipeline.md) | Release, signing and canary pipeline | Accepted |
| [0008](0008-sponsorblock-integration.md) | SponsorBlock integration | Accepted |
| [0009](0009-player-javascript-execution.md) | Player JavaScript execution (signature / n-parameter) | Accepted |
| [0010](0010-navigation-compose.md) | Navigation with navigation-compose | Accepted |
| [0011](0011-desugar-jdk-libs-license-exception.md) | Licence exception for desugar_jdk_libs | Accepted |

## Template

```markdown
# ADR-XXXX: Title
YYYY-MM-DD · Proposed | Accepted | Superseded by ADR-YYYY

## Context
What forces the decision. Facts, constraints, numbers.

## Decision
What we do. Short, imperative.

## Consequences
What becomes easier, what becomes harder, what we must now do.

## Alternatives considered
What we rejected and why, one line each.
```
