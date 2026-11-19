# ADR-0010: Navigation with navigation-compose
2026-11-19 · Accepted

## Context
ADR-0004 fixes the UI stack (Compose + Compose for TV) but not how screens are navigated. Backrow needs a back stack that the remote's Back key unwinds predictably, per-screen state that survives returning to a screen (so focus can be restored to the card the user left, CLAUDE.md rule 8), and deep entry points later (player from search, channel from player). CLAUDE.md rule 13 requires an ADR for a new major dependency.

## Decision
- Use **`androidx.navigation:navigation-compose`** (Apache-2.0, published AndroidX artifact, latest stable) for all screen navigation in `:app`.
- Routes are type-safe (`@Serializable` route classes with kotlinx.serialization, already in the stack), not string routes.
- Navigation lives only in `:app`. `:core:*` modules know nothing about screens or routes; screen state stays in plain Kotlin classes (ADR-0003).
- Back is handled by the navigation back stack; screens do not intercept Back except to close an overlay they own (a dialog, a menu, the player controls).
- Focus restoration on return is implemented per screen (remembered focus target in saveable state), not by the navigation library.

## Consequences
- One more AndroidX dependency, versioned in the catalog and updated by Renovate like the rest.
- `navigation-compose` brings `lifecycle-viewmodel-compose` and `SavedStateHandle`; screen ViewModels may use them. Koin's Compose integration provides the ViewModels.
- Moving to a different navigation approach later means touching every screen's entry point; that cost is accepted.

## Alternatives considered
- Hand-written back stack (a `SnapshotStateList` of routes): no dependency, but re-implements saved state, process-death restoration and transitions.
- Navigation 3 (`androidx.navigation3`): designed for Compose-owned back stacks, but newer; reconsider if it becomes the AndroidX default and navigation-compose stalls.
- Voyager / Decompose / other third-party routers: extra third-party surface and licence review for no TV-specific benefit.
