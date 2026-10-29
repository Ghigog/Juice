# AGENTS.md

Juice is an Android overlay productivity and memory aid (Kotlin, Jetpack Compose, min SDK 33). It is a Gradle build with two modules. See `README.md` and `Juice Technical Design v1.1.md` for the product and architecture, and `docs/design-system.md` for the UI.

## Modules

- `:core` — pure Kotlin/JVM (JDK 17 toolchain), no Android types. Holds the engine under `com.juice.core`: economy, progression, intake parsing, cadence/routines, recap, gestures, characters and personas, physics, audio synth. Source in `core/src/main`, tests in `core/src/test/kotlin` (kotlin.test + JUnit).
- `:app` — the Android application (`com.juice`): overlay UI in Compose, services, persistence and the Groq-backed AI intake. Depends on `:core`. Tests in `app/src/test` are JVM unit tests using Robolectric and Compose UI test; the optional `GROQ_API_KEY` is read from `local.properties`.

Put logic that does not need Android in `:core` so it stays easy to test.

## Running tests and coverage

Run from the repository root with the Gradle wrapper:

- All modules: `./gradlew test` (this is what the `Unit tests` workflow runs)
- `:core` only: `./gradlew :core:test`
- `:app` only: `./gradlew :app:testDebugUnitTest`
- One class: `./gradlew :core:test --tests 'com.juice.core.economy.EconomyTest'`
- Coverage rules: `./gradlew koverVerify` for the whole build, or per module `./gradlew :core:koverVerify` and `./gradlew :app:koverVerify`. Run it after the tests when you change code.

## Test rules

Tests must be deterministic. They never read the wall clock, sleep, or use unseeded randomness:

- Inject a fixed `java.time.Clock` (for example `Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC)`) into the code under test; production code takes a `Clock` parameter rather than calling `now()` itself.
- Never call `Thread.sleep`, `delay` against real time, or wait on timers. Advance a fixed or fake clock, or use coroutine test time.
- Pass a seeded `Random(seed)` wherever randomness is used.

Check your tests before finishing; this grep must print nothing for the tests you add or change (some older `:app` tests still match and should be moved to a fixed `Clock` when touched):

```sh
grep -rnE 'System\.(currentTimeMillis|nanoTime)|(Instant|LocalDate|LocalDateTime|ZonedDateTime|LocalTime)\.now\(\)|Clock\.system|Thread\.sleep|Random\(\)|Random\.Default|kotlin\.random\.Random\.next|Math\.random|UUID\.randomUUID' core/src/test app/src/test
```

## Formic

- `.github/workflows/formic-agent.yml` — the "Formic agent" GitHub Actions workflow (`workflow_dispatch`) that runs a coding agent for a Formic ticket and pushes to a `formic-staging/` branch.
- `.github/formic/loop-entry.mjs` — the bundled, generated loop entry the workflow runs. Do not edit it by hand; Formic replaces both files together when its version changes.

## Conventions

- Do not commit, push or create branches; Formic does that.
- Add checks (typecheck, lint, tests) only when a ticket asks for them, beyond the test and coverage commands above.
