# ADR-0003: Platform scope for v1
2026-09-22 · Accepted

## Context
"Every TV" is not reachable from one codebase:
- Fire TV devices released from October 2025 run **Vega OS** (Linux, React Native/web apps, no sideloading, installs limited to the Amazon Appstore). An ad-free YouTube client would not be approved there.
- Samsung **Tizen** and LG **webOS** run web apps installed through developer modes; existing projects on those platforms patch YouTube's own TV web app rather than building a client.
- **Apple TV** needs Xcode to sideload; **Roku, Vizio, VIDAA** are closed.
- **Android TV, Google TV and Fire OS** (Android-based, all Fire TV hardware released before October 2025) share one runtime and allow sideloading. This is where the users are.

## Decision
- v1 targets **Android TV / Google TV and Fire OS**, remote-control only.
- Phones and tablets may run the APK but are unsupported; no touch or portrait work.
- Vega OS, Tizen, webOS and tvOS are out of scope for v1 and are not promised for any later version.
- To keep a later web build possible without a rewrite: `:core:innertube`, `:core:sponsorblock` and `:core:model` are pure Kotlin/JVM with no Android imports, and screen state is modelled in plain Kotlin classes.

## Consequences
- No Google Play Services anywhere (absent on Fire OS).
- Fire TV specifics are first-class: remote key mapping (Menu, Rewind, Fast-forward), Fire OS 5/6/7/8 behaviour differences, Amazon launcher banner.
- The README states the exact hardware floor (docs/COMPATIBILITY.md) so nobody buys a Vega stick expecting Backrow to run.

## Alternatives considered
- Web-first single codebase (Tizen/webOS/Vega WebView/Android WebView): widest reach on paper, but Android WebView performance and codec access on cheap TVs would reproduce the "hacky" feel the project exists to fix.
- Kotlin Multiplatform from day one: premature for a single-platform v1; the module rules above keep the door open.
