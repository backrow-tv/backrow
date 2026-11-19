# ADR-0004: Tech stack
2026-09-22 · Accepted

## Context
SmartTube is locked to a 2019-era ExoPlayer fork, Leanback 1.0, Java, and a JDK 14 build requirement, all vendored into the repo. The goal is a modern toolchain that still runs on 2016-era sticks. Toolchain age and device age are independent: a current JDK, Kotlin and AGP can compile for API 22.

## Decision
| Area | Choice | Why |
|---|---|---|
| Language | Kotlin 2.x, JDK 21 toolchain | current, coroutines, the ecosystem default |
| Build | latest stable AGP and Gradle, Kotlin DSL, version catalog, Renovate for updates | reproducible, no hand-maintained versions |
| UI | Jetpack Compose + Compose for TV (`androidx.tv:tv-material`) | full design control, the only way to escape the Leanback template look; Google's current TV guidance |
| Playback | Media3 ExoPlayer, DASH | maintained, adaptive track selection built in, published artifacts |
| DI | Koin | lightweight, no annotation processing, works in pure-JVM modules |
| Async | Kotlin coroutines + Flow | standard |
| Network | OkHttp + kotlinx.serialization | small, no reflection, JSON models are explicit |
| Images | Coil 3 | Compose-native, memory limits configurable per device |
| Persistence | Room + DataStore | standard; Room for lists, DataStore for settings |
| Tests | JUnit 4, kotlinx-coroutines-test, MockWebServer, Compose UI tests on the Android TV emulator | JVM-fast unit tests, fixtures instead of network |
| Licenses | Licensee (`app.cash.licensee`) allow-list in CI | enforces ADR-0002 mechanically |
| SDK levels | `minSdk` 22 provisional (ADR-0006), `targetSdk`/`compileSdk` latest stable, core library desugaring | old devices, new APIs |

Explicitly not used: Leanback, XML layouts, Hilt/Dagger, Retrofit, Gson, RxJava, Firebase, any Google Play Services, any vendored library fork.

## Consequences
- Compose on 1 GB devices needs a performance budget (CLAUDE.md) and R8 full-mode shrinking; baseline profiles only help on API 24+.
- Everything in `:core:innertube`, `:core:sponsorblock` and `:core:model` is testable on the JVM without an emulator.
- Dependency updates arrive as small Renovate PRs instead of a painful yearly migration.

## Alternatives considered
- Leanback (Views): lighter on old hardware, but produces the template UI the project exists to replace.
- Hilt: the Android default, but annotation processing slows builds and it cannot be used in pure-JVM modules.
- Retrofit/Gson: fine, but OkHttp + kotlinx.serialization is smaller and keeps JSON models explicit, which matters when YouTube responses change shape.
