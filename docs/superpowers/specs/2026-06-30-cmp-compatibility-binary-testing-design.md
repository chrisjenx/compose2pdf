# Single-binary CMP compatibility testing

**Date:** 2026-06-30
**Status:** Approved (design), pending implementation

## Problem

compose2pdf publishes **one** artifact. Consumers pull that single jar alongside
their own Compose Multiplatform (CMP) version. The compatibility CI must answer
one question: **does the one shipped binary run on each CMP version we claim to
support?**

The current `compatibility.yml` does not answer that. For each matrix cell it
rewrites `gradle/libs.versions.toml` — overriding **both** the Kotlin and CMP
versions — and *recompiles the whole library* against that combo
(`:compose2pdf:build`). That is a **source-compatibility** test of a binary we
never ship (Kotlin 2.3.21 against CMP 1.9.3, etc.). It tells us nothing about
whether the artifact we actually publish — bytecode from one Kotlin compiler
against one CMP API — runs on a different CMP runtime.

Two secondary problems:

- The pinned build base (`compose-multiplatform = 1.10.3`) is **legacy**. Latest
  stable is **1.11.1**; 1.12.0 is in beta. We should build against the currently
  released CMP.
- `compose-versions.json` is stale (`1.9.3, 1.10.3, 1.11.0-beta03`).

## Key constraint: Skiko is decoupled from the CMP version

Skiko's version has no relationship to the CMP version string:

| CMP     | requires `skiko-awt` |
|---------|----------------------|
| 1.9.3   | 0.9.22.2             |
| 1.11.1  | 0.144.6              |

So a naive "force the test runtime's `org.jetbrains.compose.*` to version X"
would leave Skiko resolved by conflict resolution (highest wins) — mismatched
with the target Compose runtime, producing failures for the *wrong* reason.
compose2pdf renders through Skiko, so this matters.

**Implication:** let the Compose Gradle plugin do the version wiring. Applying
the plugin at version X pulls the correct Compose + Skiko + stdlib for X — exactly
what a real downstream project gets.

## Design

Principle: build the one artifact against the currently-released CMP, then verify
it runs on each supported runtime by resolving it the way a downstream project would.

### 1. Build base (what ships)

Bump `gradle/libs.versions.toml`:

- `compose-multiplatform`: `1.10.3` → `1.11.1` (latest stable)
- `kotlin`: `2.3.20` → `2.3.21` (latest known-good 2.3.x for CMP 1.11.x;
  2.4.0 held back until the matrix confirms it)

Main CI already builds+tests this pinned combo, covering "base runs on base."

### 2. Consumer harness (new) — `compat-consumer/`

An **independent** Gradle build (its own `settings.gradle.kts`, deliberately
**not** `include`d in the root build — that isolation is what lets it apply a
different Compose plugin version without disturbing the library build).

- `settings.gradle.kts`: `pluginManagement` resolves the Kotlin, Compose, and
  Compose-compiler plugin versions from `-PcomposeVersion` / `-PkotlinVersion`
  Gradle properties.
- `build.gradle.kts`: depends on `com.chrisjenx:compose2pdf:<version>` resolved
  from **`mavenLocal()`** (the jar built against 1.11.1). The version is passed
  as `-Pcompose2pdfVersion` (sourced from the root `gradle.properties`, currently
  `1.1.2-SNAPSHOT`).
- One smoke test: call `renderToPdf { /* a trivial composable, e.g. Text */ }`,
  assert the returned bytes are non-empty and begin with the `%PDF` magic header.
  This exercises the published binary being *called from code compiled by the
  target version's Compose compiler* against the target runtime — the real
  downstream scenario, including Skiko rendering.

### 3. Rewritten `compatibility.yml`

Matrix stays `{os: [ubuntu-latest, macos-latest]} × {version}` (Skiko is native,
so both OSes matter). Per cell:

1. `./gradlew :compose2pdf:publishToMavenLocal` — builds the one artifact against
   pinned 1.11.1.
2. Run the consumer against the cell's target:
   `./gradlew -p compat-consumer test -PcomposeVersion=<v> -PkotlinVersion=<k> -Pcompose2pdfVersion=<lib>`
   (using the root Gradle wrapper; distribution is independent of the target
   build's `settings.gradle.kts`).

Keep `xvfb-run` on Linux. **Delete** the `perl` "Override versions" step.

### 4. `compose-versions.json` + auto-update

Refresh the matrix to the current support window:

```json
{ "versions": [
  { "compose-version": "1.10.3",        "kotlin-version": "<compatible>" },
  { "compose-version": "1.11.1",        "kotlin-version": "<compatible>" },
  { "compose-version": "1.12.0-beta01", "kotlin-version": "<compatible>" }
] }
```

(previous stable / current stable / upcoming beta — what the existing
auto-update selection logic produces today; 1.9.3 dropped.)

`update-compose-versions.yml` changes:

- **Also bump the build base**: in the weekly PR, set
  `gradle/libs.versions.toml`'s `compose-multiplatform` (and `kotlin`) to the
  latest stable, in addition to refreshing `compose-versions.json`. Keeps "ship
  against currently released" automatic; the human reviews/merges each bump.
- **Pair each matrix row with a compatible Kotlin** rather than stamping every
  row with the single latest stable Kotlin (older CMP minors need older Kotlin).
  The per-row Kotlin now configures the *consumer* build for that version.

## What each layer proves

| Layer                        | Proves                                                        |
|------------------------------|--------------------------------------------------------------|
| Main CI (pinned 1.11.1)      | The shipped binary builds and its own tests pass.            |
| Consumer @ 1.10.3            | Shipped binary runs on the previous stable runtime (back-compat). |
| Consumer @ 1.11.1            | Consumer harness itself is sound; base-on-base via real resolution. |
| Consumer @ 1.12.0-beta01     | Forward-compat early warning against the next release.       |

A version that fails the consumer smoke test is one we cannot support with the
current build base — the signal to drop it from the window (or change the base).

## Non-goals / YAGNI

- No fidelity/raster comparison in the consumer — a `%PDF` + non-empty assertion
  is enough to prove the binary loads and renders against the target runtime.
- No new module inside the root build; the consumer stays a separate build.
- Exact per-row Kotlin pairings are refined by what the matrix actually passes;
  the design fixes the mechanism, not a frozen version table.

## Files touched

- `gradle/libs.versions.toml` — bump base CMP + Kotlin.
- `.github/compose-versions.json` — refresh matrix.
- `.github/workflows/compatibility.yml` — consumer-based flow; drop recompile.
- `.github/workflows/update-compose-versions.yml` — bump base + per-row Kotlin.
- `compat-consumer/settings.gradle.kts` — new; version-parameterized plugins.
- `compat-consumer/build.gradle.kts` — new; mavenLocal dep + smoke test wiring.
- `compat-consumer/src/test/.../SmokeTest.kt` — new; `renderToPdf` `%PDF` assertion.
