# Juice

Juice is an overlay-driven productivity and memory aid built around tactile feedback, liquid shader physics and fast AI text classification. Capture is instant and the overlay is one swipe away from anywhere. Completing items earns Juice (XP), and completing them in quick succession fills the Thirst Meter.

The current scope is the **Android MVP** (Kotlin, Jetpack Compose, min SDK 33). iOS and desktop are post-MVP.

## Modules

- `app` — the Android application (`com.juice`): overlay UI, services, persistence and the Groq-backed AI intake.
- `core` — the core engine (economy, parsing, gestures), kept free of Android types so it can move to other platforms later.

## Build

```sh
./gradlew build
```

To build only the debug APK: `./gradlew :app:assembleDebug`. The optional `GROQ_API_KEY` is read from `local.properties`.

## Documentation

See the [technical design doc (v1.1)](Juice%20Technical%20Design%20v1.1.md) for the vision, MVP scope, stack and architecture.
