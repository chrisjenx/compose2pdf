# Single-binary CMP Compatibility Testing — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the recompile-per-version compatibility matrix with a test that proves the *one* published binary (built against the currently-released CMP) runs on each supported CMP runtime, via an independent consumer build.

**Architecture:** Bump the shipped build base to CMP 1.11.1 / Kotlin 2.4.0. Add an independent `compat-consumer/` Gradle build that pulls the published jar from mavenLocal, applies the Compose toolchain at a target version, forces the Compose group to that target (Skiko cascades), and runs a `main()` smoke check that renders a PDF. Rewrite `compatibility.yml` to publish-then-consume; extend the weekly auto-update workflow to also bump the base.

**Tech Stack:** Kotlin 2.4.0, Compose Multiplatform 1.11.1 (Desktop), Gradle 8.14, Apache PDFBox, GitHub Actions, Python 3 (docs generator).

**Design doc:** `docs/superpowers/specs/2026-06-30-cmp-compatibility-binary-testing-design.md`

## Global Constraints

- Package for consumer code: `com.chrisjenx.compat`. Library package is `com.chrisjenx.compose2pdf`.
- `compat-consumer/` MUST NOT be `include`d in the root `settings.gradle.kts` — it is a standalone build so it can apply a different Compose plugin version.
- The published library version is read from the root `gradle.properties` `version=` line (currently `1.1.4-SNAPSHOT`) — never hard-code it in CI.
- `.github/compose-versions.json` is the single source of truth for the tested matrix; the docs tables are generated from it + the pins by `.github/scripts/render-compat-tables.py`. Never hand-edit the tables.
- CMP ≤ 1.11 → `cmpLegacy` scene driver; ≥ 1.12 → `cmpNext`. Base 1.11.1 selects `cmpLegacy`.
- Pre-release matrix cells (version string contains `-`) are non-blocking in CI.
- Work happens on the current worktree branch; commit after each task. Do not push or open a PR unless asked.

---

## File Structure

- `gradle/libs.versions.toml` — modify: bump `compose-multiplatform` and `kotlin` pins.
- `docs/compatibility.md`, `README.md` — modify: regenerated (never by hand) from the pins + JSON.
- `compat-consumer/settings.gradle.kts` — create: standalone build; version-parameterized plugins; `mavenLocal()`.
- `compat-consumer/build.gradle.kts` — create: mavenLocal dependency on the published jar; `resolutionStrategy` force of the Compose group; `application` `main()` wiring.
- `compat-consumer/gradle.properties` — create: JVM args + fallback property defaults.
- `compat-consumer/src/main/kotlin/com/chrisjenx/compat/Smoke.kt` — create: `main()` that renders and asserts `%PDF-`.
- `.gitignore` — modify only if `compat-consumer/build/` is not already covered.
- `.github/workflows/compatibility.yml` — rewrite: publish-then-consume; drop the `perl` override.
- `.github/workflows/update-compose-versions.yml` — modify: also bump the build base + regenerate docs; widen the change gate.

---

### Task 1: Bump the shipped build base to currently-released CMP

**Files:**
- Modify: `gradle/libs.versions.toml:2-3`
- Modify (generated): `docs/compatibility.md`, `README.md`
- Verify: `.github/scripts/render-compat-tables.py`

**Interfaces:**
- Produces: the pinned build base (`compose-multiplatform = "1.11.1"`, `kotlin = "2.4.0"`) that Task 2's `publishToMavenLocal` compiles against, and that the consumer's `-Pcompose2pdfVersion` jar embeds.

- [ ] **Step 1: Read the current pins**

Run: `grep -nE '^(kotlin|compose-multiplatform) =' gradle/libs.versions.toml`
Expected: `kotlin = "2.3.20"` and `compose-multiplatform = "1.10.3"`.

- [ ] **Step 2: Bump both pins**

In `gradle/libs.versions.toml`, change the two version lines:

```toml
kotlin = "2.4.0"
compose-multiplatform = "1.11.1"
```

(These are the two `[versions]` entries near the top; leave every other line untouched.)

- [ ] **Step 3: Confirm the library still builds against the new base**

Run: `./gradlew :compose2pdf:build`
Expected: `BUILD SUCCESSFUL`, and the log line `compose2pdf: using 'cmpLegacy' ComposeSceneRenderer for Compose 1.11.1` (1.11 < 1.12 ⇒ cmpLegacy). If the build fails to *compile*, the base is not viable — stop and report, do not force a variant.

- [ ] **Step 4: Confirm the fidelity module is fine on Kotlin 2.4.0**

Run: `./gradlew :fidelity-test:test`
Expected: `BUILD SUCCESSFUL`. If this regresses *only* under 2.4.0, fall back to `kotlin = "2.3.21"` in `gradle/libs.versions.toml`, re-run Steps 3–4, and note the fallback in the commit message.

- [ ] **Step 5: Regenerate the compatibility tables**

Run: `python3 .github/scripts/render-compat-tables.py`
Expected: `compat tables updated: docs/compatibility.md, README.md`. The bolded "current" row moves to `**1.11.1** | 2.4.0 | CI tested (current)`.

- [ ] **Step 6: Verify the tables now match (the CI gate)**

Run: `python3 .github/scripts/render-compat-tables.py --check`
Expected: `compat tables up to date` and exit code 0.

- [ ] **Step 7: Commit**

```bash
git add gradle/libs.versions.toml docs/compatibility.md README.md
git commit -m "Build against currently-released CMP 1.11.1 / Kotlin 2.4.0

The published library is one binary; pin it to the latest stable CMP rather
than a legacy version. Regenerate the compatibility tables from the new pin.

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 2: Create the `compat-consumer` harness and validate locally

**Files:**
- Create: `compat-consumer/settings.gradle.kts`
- Create: `compat-consumer/gradle.properties`
- Create: `compat-consumer/build.gradle.kts`
- Create: `compat-consumer/src/main/kotlin/com/chrisjenx/compat/Smoke.kt`
- Modify (if needed): `.gitignore`

**Interfaces:**
- Consumes: the published `com.chrisjenx:compose2pdf` jar from `mavenLocal()` (produced by `:compose2pdf:publishToMavenLocal` in Task 1's build base), and the `renderToPdf(config, density, mode, defaultFontFamily, pagination) { content }` public API (all args defaulted; only the trailing `content` lambda is required).
- Produces: the invocation contract Task 3's CI uses, verbatim:
  `./gradlew -p compat-consumer run -PcomposeVersion=<v> -PkotlinVersion=<k> -Pcompose2pdfVersion=<lib>`

- [ ] **Step 1: Publish the library to mavenLocal (the artifact under test)**

Run: `./gradlew :compose2pdf:publishToMavenLocal`
Expected: `BUILD SUCCESSFUL`; the jar appears under `~/.m2/repository/com/chrisjenx/compose2pdf/1.1.4-SNAPSHOT/`.

Confirm the version string to pass later:
Run: `grep '^version=' gradle.properties`
Expected: `version=1.1.4-SNAPSHOT` (use whatever it prints as `<lib>` below).

- [ ] **Step 2: Write the standalone settings file**

Create `compat-consumer/settings.gradle.kts`:

```kotlin
// Standalone build (NOT included in the root settings.gradle.kts) so it can apply a
// different Compose Multiplatform version than the library it consumes. Plugin versions
// come from -PcomposeVersion / -PkotlinVersion (CI passes them; defaults are a fallback).
pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }
    val composeVersion = providers.gradleProperty("composeVersion").orElse("1.11.1").get()
    val kotlinVersion = providers.gradleProperty("kotlinVersion").orElse("2.4.0").get()
    plugins {
        id("org.jetbrains.kotlin.jvm") version kotlinVersion
        id("org.jetbrains.compose") version composeVersion
        id("org.jetbrains.kotlin.plugin.compose") version kotlinVersion
    }
}

dependencyResolutionManagement {
    repositories {
        mavenLocal() // the compose2pdf snapshot under test
        google()
        mavenCentral()
    }
}

rootProject.name = "compat-consumer"
```

- [ ] **Step 3: Write the consumer gradle.properties (fallback defaults + JVM args)**

Create `compat-consumer/gradle.properties`:

```properties
org.gradle.jvmargs=-Xmx2g -XX:MaxMetaspaceSize=512m
# Fallback defaults; CI always overrides these with -P flags.
composeVersion=1.11.1
kotlinVersion=2.4.0
compose2pdfVersion=1.1.4-SNAPSHOT
```

- [ ] **Step 4: Write the consumer build file**

Create `compat-consumer/build.gradle.kts`:

```kotlin
plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
    application
}

val composeVersion = providers.gradleProperty("composeVersion").orElse("1.11.1").get()
val compose2pdfVersion = providers.gradleProperty("compose2pdfVersion").orElse("1.1.4-SNAPSHOT").get()
val skikoVersion = providers.gradleProperty("skikoVersion").orNull // fallback override; normally unset

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation("com.chrisjenx:compose2pdf:$compose2pdfVersion")
    implementation(compose.desktop.currentOs)
}

application {
    mainClass.set("com.chrisjenx.compat.SmokeKt")
}

// compose2pdf is published against its pinned base Compose version and requests it
// transitively; without this, conflict resolution would UPGRADE the target back to the
// base and the smoke check would run on the wrong runtime. Forcing the whole Compose
// group to the target makes each module resolve its own POM, so Skiko cascades to the
// version that Compose version declares — no per-row Skiko bookkeeping. If some version
// ever fails to cascade Skiko, pass -PskikoVersion=<v> to pin it explicitly.
configurations.all {
    resolutionStrategy.eachDependency {
        if (requested.group.startsWith("org.jetbrains.compose")) {
            useVersion(composeVersion)
            because("compat runtime swap: exercise the published binary on Compose $composeVersion")
        }
        if (skikoVersion != null && requested.group == "org.jetbrains.skiko") {
            useVersion(skikoVersion)
        }
    }
}
```

- [ ] **Step 5: Write the smoke check**

Create `compat-consumer/src/main/kotlin/com/chrisjenx/compat/Smoke.kt`:

```kotlin
package com.chrisjenx.compat

import androidx.compose.material.Text
import com.chrisjenx.compose2pdf.renderToPdf

/**
 * Renders a trivial PDF using the PUBLISHED compose2pdf jar against whatever Compose
 * runtime this build resolved. Exits non-zero (via check) on any failure, which fails
 * the Gradle `run` task and the CI job.
 */
fun main() {
    val pdf = renderToPdf {
        Text("compose2pdf compatibility smoke test")
    }
    check(pdf.size > 100) { "PDF suspiciously small: ${pdf.size} bytes" }
    val header = pdf.copyOfRange(0, 5).toString(Charsets.US_ASCII)
    check(header == "%PDF-") { "Not a PDF - header was '$header'" }
    println("compat-consumer OK: rendered ${pdf.size}-byte PDF")
}
```

- [ ] **Step 6: Run the smoke check against the base version (must PASS)**

Run: `./gradlew -p compat-consumer run -PcomposeVersion=1.11.1 -PkotlinVersion=2.4.0 -Pcompose2pdfVersion=1.1.4-SNAPSHOT`
Expected: `BUILD SUCCESSFUL` and `compat-consumer OK: rendered <N>-byte PDF`. This proves the harness itself works and validates the `-p` invocation from the root wrapper.

- [ ] **Step 7: Run against the previous stable (back-compat; expect PASS, and confirm Skiko cascaded)**

Run: `./gradlew -p compat-consumer run -PcomposeVersion=1.10.3 -PkotlinVersion=2.4.0 -Pcompose2pdfVersion=1.1.4-SNAPSHOT --info | grep -iE 'skiko|BUILD (SUCCESSFUL|FAILED)|compat-consumer OK'`
Expected: `BUILD SUCCESSFUL`, `compat-consumer OK`, and a `skiko-awt...0.9.22.2` on the classpath (proving the force cascaded Skiko down, not left it at 1.11.1's `0.144.6`). If it fails with a Skiko/linkage error, re-run adding `-PskikoVersion=0.9.22.2` and record that the fallback is needed for 1.10.3.

- [ ] **Step 8: Run against the upcoming beta (forward boundary; failure is EXPECTED and OK)**

Run: `./gradlew -p compat-consumer run -PcomposeVersion=1.12.0-beta01 -PkotlinVersion=2.4.0 -Pcompose2pdfVersion=1.1.4-SNAPSHOT || echo "EXPECTED-FAILURE (cmpLegacy jar vs cmpNext runtime)"`
Expected: either a render failure (the ≤1.11 internal scene API the jar was built against was reshaped in 1.12) followed by `EXPECTED-FAILURE`, or — if the internal API happens to still resolve — a pass. Record which occurred. Either way this is the informational cell; do not try to "fix" a failure here.

- [ ] **Step 9: Ensure the consumer build output is git-ignored**

Run: `git status --porcelain compat-consumer/build 2>/dev/null | head`
Expected: no output (already ignored by a global `build/` rule). If `compat-consumer/build/` shows as untracked, append this line to `.gitignore`:

```gitignore
compat-consumer/build/
```

- [ ] **Step 10: Commit**

```bash
git add compat-consumer/settings.gradle.kts compat-consumer/gradle.properties compat-consumer/build.gradle.kts compat-consumer/src .gitignore
git commit -m "Add compat-consumer harness for single-binary runtime-swap testing

Standalone Gradle build that resolves the published compose2pdf jar from
mavenLocal, forces the Compose group to a target version (Skiko cascades),
and renders a PDF via main(). Locally verified: 1.11.1 and 1.10.3 pass;
1.12.0-beta01 is the informational forward boundary.

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 3: Rewrite `compatibility.yml` to publish-then-consume

**Files:**
- Rewrite: `.github/workflows/compatibility.yml`

**Interfaces:**
- Consumes: the matrix from `load-versions` (each entry has `compose-version` and `kotlin-version`), the published version from `gradle.properties`, and Task 2's invocation contract.

- [ ] **Step 1: Replace the workflow file**

Overwrite `.github/workflows/compatibility.yml` with:

```yaml
name: Compose Compatibility

on:
  push:
    branches: [main]
  pull_request:
  schedule:
    - cron: '0 9 * * 1' # Weekly Monday 9am UTC
  workflow_dispatch:

permissions:
  contents: read

jobs:
  load-versions:
    runs-on: ubuntu-latest
    outputs:
      matrix: ${{ steps.load.outputs.matrix }}
    steps:
      - uses: actions/checkout@v5
      - id: load
        run: echo "matrix=$(jq -c '.versions' .github/compose-versions.json)" >> "$GITHUB_OUTPUT"

  # Publishes the ONE library artifact (built against the pinned base) to mavenLocal,
  # then runs a standalone consumer against each target Compose runtime. This tests the
  # binary we actually ship, not a per-version recompile.
  test:
    needs: load-versions
    strategy:
      fail-fast: false
      matrix:
        os: [ubuntu-latest, macos-latest]
        version: ${{ fromJson(needs.load-versions.outputs.matrix) }}
    runs-on: ${{ matrix.os }}
    name: CMP ${{ matrix.version.compose-version }} / ${{ matrix.os }}
    steps:
      - uses: actions/checkout@v5
        with:
          lfs: true

      - uses: actions/setup-java@v5
        with:
          distribution: temurin
          java-version: 17

      - uses: gradle/actions/setup-gradle@v5

      - name: Publish library to Maven Local (built against pinned base)
        run: ./gradlew :compose2pdf:publishToMavenLocal

      - name: Resolve published library version
        id: lib
        run: echo "version=$(grep '^version=' gradle.properties | cut -d= -f2)" >> "$GITHUB_OUTPUT"

      # Pre-release targets (version contains '-') are informational: the shipped
      # cmpLegacy binary is not expected to run on a cmpNext runtime.
      - name: Compatibility smoke test (Linux)
        if: runner.os == 'Linux'
        continue-on-error: ${{ contains(matrix.version.compose-version, '-') }}
        run: >
          xvfb-run ./gradlew -p compat-consumer run
          -PcomposeVersion=${{ matrix.version.compose-version }}
          -PkotlinVersion=${{ matrix.version.kotlin-version }}
          -Pcompose2pdfVersion=${{ steps.lib.outputs.version }}

      - name: Compatibility smoke test (macOS)
        if: runner.os == 'macOS'
        continue-on-error: ${{ contains(matrix.version.compose-version, '-') }}
        run: >
          ./gradlew -p compat-consumer run
          -PcomposeVersion=${{ matrix.version.compose-version }}
          -PkotlinVersion=${{ matrix.version.kotlin-version }}
          -Pcompose2pdfVersion=${{ steps.lib.outputs.version }}
```

- [ ] **Step 2: Verify the YAML parses**

Run: `python3 -c "import yaml,sys; yaml.safe_load(open('.github/workflows/compatibility.yml')); print('yaml ok')"`
Expected: `yaml ok`.

- [ ] **Step 3: Confirm the old recompile step is gone**

Run: `grep -n 'perl\|Override versions\|libs.versions.toml' .github/workflows/compatibility.yml || echo "no override step (correct)"`
Expected: `no override step (correct)`.

- [ ] **Step 4: Sanity-check the command matches the locally-validated contract**

Run: `grep -n 'compat-consumer run' .github/workflows/compatibility.yml`
Expected: two matches (Linux + macOS), each passing `-PcomposeVersion`, `-PkotlinVersion`, `-Pcompose2pdfVersion` — identical in shape to the commands validated in Task 2 Steps 6–8.

- [ ] **Step 5: Commit**

```bash
git add .github/workflows/compatibility.yml
git commit -m "Rewrite compatibility CI to publish-then-consume the shipped binary

Each matrix cell publishes the one artifact to mavenLocal, then runs the
compat-consumer against the target Compose runtime. Drops the per-version
recompile (perl override). Pre-release cells are non-blocking.

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 4: Extend the weekly workflow to also bump the build base

**Files:**
- Modify: `.github/workflows/update-compose-versions.yml`

**Interfaces:**
- Consumes: `LATEST_STABLE` (newline list of the recent stable versions) and `LATEST_KOTLIN` computed earlier in the same `detect` step.
- Produces: a weekly PR that updates `compose-versions.json`, `gradle/libs.versions.toml` (base bump), and the regenerated docs together.

- [ ] **Step 1: Add the base-bump + docs-regen block**

In `.github/workflows/update-compose-versions.yml`, immediately after the line `cat .github/compose-versions.json` (end of JSON generation, before the `# Check if anything changed` block), insert:

```bash
          # The published library is one binary — pin its build base to the highest
          # STABLE detected Compose version (never a pre-release) + the latest Kotlin.
          BASE_COMPOSE=$(echo "$LATEST_STABLE" | sort -t. -k1,1n -k2,2n -k3,3n | tail -1)
          echo "Build base -> Compose $BASE_COMPOSE + Kotlin $LATEST_KOTLIN"
          perl -i -pe "s/^compose-multiplatform = .*/compose-multiplatform = \"$BASE_COMPOSE\"/" gradle/libs.versions.toml
          perl -i -pe "s/^kotlin = .*/kotlin = \"$LATEST_KOTLIN\"/" gradle/libs.versions.toml

          # Regenerate the human-facing compatibility tables from the new pin + JSON.
          python3 .github/scripts/render-compat-tables.py
```

- [ ] **Step 2: Widen the change-detection gate**

In the same step, replace the existing change check:

```bash
          # Check if anything changed
          if git diff --quiet .github/compose-versions.json; then
```

with a version that also fires on a base bump or docs regen:

```bash
          # Check if anything changed (matrix, build-base pin, or generated docs)
          if git diff --quiet .github/compose-versions.json gradle/libs.versions.toml docs/compatibility.md README.md; then
```

(Leave the `else` branch — the `changed=true` + `matrix<<EOF` heredoc — exactly as it is.)

- [ ] **Step 3: Mention the base bump in the PR body**

In the `Create Pull Request` step's `body:` block, after the line ``The compatibility CI will run on this PR to verify all versions build and pass tests.``, add a line:

```yaml
            This PR also bumps the shipped build base in `gradle/libs.versions.toml` to the
            latest stable Compose/Kotlin and regenerates the compatibility tables.
```

- [ ] **Step 4: Verify the YAML still parses**

Run: `python3 -c "import yaml; yaml.safe_load(open('.github/workflows/update-compose-versions.yml')); print('yaml ok')"`
Expected: `yaml ok`.

- [ ] **Step 5: Dry-run the base-selection logic in isolation**

Run:
```bash
LATEST_STABLE=$'1.10.3\n1.11.1'
BASE_COMPOSE=$(echo "$LATEST_STABLE" | sort -t. -k1,1n -k2,2n -k3,3n | tail -1)
echo "selected base: $BASE_COMPOSE"
```
Expected: `selected base: 1.11.1` (the highest stable, never the beta — the beta is not in `LATEST_STABLE`).

- [ ] **Step 6: Commit**

```bash
git add .github/workflows/update-compose-versions.yml
git commit -m "Auto-bump the build base to latest stable in the weekly update PR

The weekly workflow now pins gradle/libs.versions.toml to the highest stable
detected Compose version + latest Kotlin and regenerates the compat tables,
so 'ship against currently released' stays automatic (human-reviewed via PR).

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Self-Review

**Spec coverage:**
- §1 build base bump → Task 1. §2 consumer harness (settings/build/props/main + force + Skiko cascade + fallback) → Task 2. §3 rewritten `compatibility.yml` (publish-then-consume, non-blocking pre-release, drop override) → Task 3. §4 no-JSON-change (asserted) + auto-bump base + regen docs + widened gate → Task 4 (JSON unchanged is a no-op, correctly reflected by "no compose-versions.json edit"). "What each layer proves" is exercised by Task 2 Steps 6–8 and Task 3's matrix. ✅
- Docs-sync CI gate (`render-compat-tables.py --check`) is satisfied by Task 1 Steps 5–6. ✅

**Placeholder scan:** All version strings, file paths, code blocks, and commands are concrete. `<v>/<k>/<lib>` appear only in the Interfaces contract prose and are instantiated with real values in every runnable step. No TODO/TBD. ✅

**Type/contract consistency:** `mainClass` `com.chrisjenx.compat.SmokeKt` matches `Smoke.kt` in package `com.chrisjenx.compat` (Kotlin file-class suffix `Kt`). Property names `composeVersion` / `kotlinVersion` / `compose2pdfVersion` / `skikoVersion` are identical across `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, and both workflows. The `renderToPdf { Text(...) }` call uses the all-defaults public overload. ✅
