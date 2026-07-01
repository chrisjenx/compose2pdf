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
- `kotlin`: `2.3.20` → `2.4.0` (latest stable; the matrix already exercised
  `1.11.1` + `2.4.0` at the library layer, so it is validated for the base. The
  base bump also runs `:fidelity-test:test` locally to confirm 2.4.0 is fine for
  the fidelity module; fall back to `2.3.21` only if that surfaces a regression.)
- `1.11.1` (minor 11 < 12) keeps selecting the **cmpLegacy** scene driver, so the
  published jar targets the ≤1.11 internal scene API.

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
  `1.1.4-SNAPSHOT`).
- A smoke check as an `application` `main()` (no test-framework wiring): call
  `renderToPdf { Text(...) }`, assert the returned bytes are non-empty and begin
  with the `%PDF-` magic header (`check(...)` → non-zero exit on failure). This
  exercises the published binary being *called from code compiled by the target
  version's Compose compiler* against the target runtime — the real downstream
  scenario, including Skiko rendering.

**Why a force is still needed.** compose2pdf's POM declares Compose `1.11.1` (its
build base) as a transitive dependency. A consumer that merely applied an *older*
Compose plugin would have that transitive `1.11.1` **win** conflict resolution —
the smoke check would silently run on 1.11.1, not the target. The consumer
therefore forces the whole `org.jetbrains.compose*` group to the target version
via `resolutionStrategy.eachDependency`. **Skiko cascades correctly with no
per-row bookkeeping**: once a Compose module is forced to version X, Gradle
resolves *that* module's POM, which declares X's Skiko — the base version's Skiko
edge leaves the graph. Forcing an exact target also models the strongest form of
the guarantee: "does the one binary run on *exactly* this Compose version." A
`skikoVersion` property is the documented fallback if any version fails to cascade.

Dependency-direction note: because compose2pdf itself requires Compose 1.11.1, a
real downstream on an *older* version only reaches that runtime by force-pinning
Compose below our floor; the realistic, higher-value cell is the *forward* one
(1.12.0-beta01), which — see §3 — is expected to fail against a cmpLegacy jar.

### 3. Rewritten `compatibility.yml`

Matrix stays `{os: [ubuntu-latest, macos-latest]} × {version}` (Skiko is native,
so both OSes matter). Per cell:

1. `./gradlew :compose2pdf:publishToMavenLocal` — builds the one artifact against
   pinned 1.11.1.
2. Resolve the published version from `gradle.properties`.
3. Run the consumer against the cell's target:
   `./gradlew -p compat-consumer run -PcomposeVersion=<v> -PkotlinVersion=<k> -Pcompose2pdfVersion=<lib>`
   (using the root Gradle wrapper; distribution is independent of the target
   build's `settings.gradle.kts`).

**Pre-release cells are non-blocking:**
`continue-on-error: ${{ contains(matrix.version.compose-version, '-') }}` — a
version string containing `-` (e.g. `1.12.0-beta01`) is a pre-release and is
informational, so the cmpLegacy→cmpNext boundary does not paint the badge red.
Stable cells gate the workflow.

Keep `xvfb-run` on Linux. **Delete** the `perl` "Override versions" step.

### 4. `compose-versions.json` + auto-update

The matrix version set is **already** the current support window —
`{1.10.3, 1.11.1, 1.12.0-beta01}`, all at Kotlin `2.4.0` (previous stable /
current stable / upcoming beta). **No JSON edit is required**; the per-row
Kotlin now configures the *consumer* build for that version, and `2.4.0`
currently works for every row (validated in §2's local run). Per-row Kotlin
*pinning* (older minors → older Kotlin) is **deferred** — introduce it only if a
row's smoke check fails on `2.4.0`.

`update-compose-versions.yml` change — **also bump the build base**: after
rewriting `compose-versions.json`, the weekly job sets
`gradle/libs.versions.toml`'s `compose-multiplatform` to the highest *stable*
detected version and `kotlin` to the latest stable Kotlin, then runs
`render-compat-tables.py` so the PR carries the regenerated docs. The PR-gate
condition widens to fire when **either** `compose-versions.json` **or**
`gradle/libs.versions.toml` changed. Keeps "ship against currently released"
automatic; the human reviews/merges each bump.

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

- No fidelity/raster comparison in the consumer — a `%PDF-` + non-empty assertion
  is enough to prove the binary loads and renders against the target runtime.
- No new module inside the root build; the consumer stays a separate build.
- No `compose-versions.json` change — the matrix set is already correct.
- No per-row Kotlin pinning yet — deferred until a row fails on 2.4.0.

## Files touched

- `gradle/libs.versions.toml` — bump base CMP (1.11.1) + Kotlin (2.4.0).
- `docs/compatibility.md`, `README.md` — regenerated by `render-compat-tables.py`.
- `.github/workflows/compatibility.yml` — consumer-based flow; drop recompile.
- `.github/workflows/update-compose-versions.yml` — also bump the build base + regen docs.
- `compat-consumer/settings.gradle.kts` — new; version-parameterized plugins + mavenLocal.
- `compat-consumer/build.gradle.kts` — new; mavenLocal dep + `resolutionStrategy` force + `application`.
- `compat-consumer/gradle.properties` — new; JVM args + default property values.
- `compat-consumer/src/main/kotlin/com/chrisjenx/compat/Smoke.kt` — new; `main()` `%PDF-` check.
- `.gitignore` — ensure `compat-consumer/build/` is ignored (if not already covered).
