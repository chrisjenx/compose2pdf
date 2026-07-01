# Single-binary Reflective Scene Driver — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the two build-time scene-driver source variants with one reflective `ComposeSceneRenderer` so a single published binary renders correctly on CMP 1.11 and 1.12+.

**Architecture:** The only Compose API that draws scene commands onto an arbitrary Skia canvas is the `@InternalComposeUiApi` `androidx.compose.ui.scene` package, which reshaped in 1.12 and has no stable equivalent. A single `ComposeSceneRenderer` in `src/main` keeps all stable calls typed and uses reflection for only the divergent construction/drive calls, detecting the API shape by structure (presence of `FrameRecomposer`, factory arity) rather than a version string. Unknown shapes fail fast.

**Tech Stack:** Kotlin 2.4.0, Compose Multiplatform 1.11.1 base (Desktop), Java reflection, Skiko, Gradle 8.14, GitHub Actions.

**Design doc:** `docs/superpowers/specs/2026-07-01-single-binary-reflective-scene-driver-design.md`

## Global Constraints

- Package: `com.chrisjenx.compose2pdf.internal`; the driver keeps `internal` visibility.
- `ComposeSceneRenderer.drawContent(canvas: org.jetbrains.skia.Canvas, widthPx: Int, heightPx: Int, density: Density, content: @Composable () -> Unit)` — signature MUST stay identical (called by `ComposeToSvg`).
- Detect the scene API by structure, never by a hardcoded CMP version string or a mangled method name.
- Stable APIs stay typed: `asComposeCanvas`, `IntSize`, `Density`, `LayoutDirection.Ltr`, `Dispatchers.Unconfined`, `org.jetbrains.skia.Canvas`. Only divergent scene calls are reflective.
- On an unrecognized API shape, throw `Compose2PdfException` with a descriptive message — no silent fallback.
- Base build: CMP `1.11.1` / Kotlin `2.4.0` (already pinned on this branch). 1.11.1 → the legacy shape.
- Work continues on the current worktree branch (PR #13). Commit after each task; do not push unless asked (the branch already has an open PR, so a later push updates it — only when asked).
- Local `publishToMavenLocal` in this sandbox needs a clean Gradle home to bypass a stray signing keyring: `GRADLE_RO_DEP_CACHE="$HOME/.gradle/caches" ./gradlew -g "$CLAUDE_JOB_DIR/tmp/ghome" :compose2pdf:publishToMavenLocal`. CI is unaffected.

---

## File Structure

- `compose2pdf/src/main/kotlin/com/chrisjenx/compose2pdf/internal/ComposeSceneRenderer.kt` — **create**: the single reflective driver. One responsibility: drive a Compose scene onto a Skia canvas across CMP API shapes.
- `compose2pdf/src/cmpLegacy/`, `compose2pdf/src/cmpNext/` — **delete**: superseded by the reflective driver.
- `compose2pdf/build.gradle.kts` — **modify**: drop `composeSceneVariant` selection + `srcDir`; keep toolchain, opt-in, and a lifecycle log line.
- `compat-consumer/src/main/kotlin/com/chrisjenx/compat/Smoke.kt` — **modify**: render text + a shape (real draw commands).
- `.github/workflows/compatibility.yml` — **modify**: drop the "pre-release expected to fail" framing (1.12 now passes); keep `continue-on-error` on pre-release only as an unrelated-breakage guard.
- `CLAUDE.md` — **modify**: rewrite the "Version-specific scene driver" gotcha to describe the reflective driver.

---

### Task 1: Replace the two variants with the reflective driver; prove base behaviour is unchanged

**Files:**
- Create: `compose2pdf/src/main/kotlin/com/chrisjenx/compose2pdf/internal/ComposeSceneRenderer.kt`
- Delete: `compose2pdf/src/cmpLegacy/`, `compose2pdf/src/cmpNext/`
- Modify: `compose2pdf/build.gradle.kts`

**Interfaces:**
- Consumes: `ComposeToSvg` calls `ComposeSceneRenderer.drawContent(canvas, widthPx, heightPx, density, content)` — unchanged.
- Produces: the same object+function; internally reflective. No new public surface.

- [ ] **Step 1: Confirm the variant dirs contain only the driver**

Run: `find compose2pdf/src/cmpLegacy compose2pdf/src/cmpNext -type f`
Expected: exactly two files, both `.../internal/ComposeSceneRenderer.kt`. (If anything else is present, stop — the deletion in Step 4 would lose it.)

- [ ] **Step 2: Write the reflective driver**

Create `compose2pdf/src/main/kotlin/com/chrisjenx/compose2pdf/internal/ComposeSceneRenderer.kt`:

```kotlin
@file:OptIn(InternalComposeUiApi::class)

package com.chrisjenx.compose2pdf.internal

import androidx.compose.runtime.Composable
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.graphics.asComposeCanvas
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import com.chrisjenx.compose2pdf.Compose2PdfException
import kotlinx.coroutines.Dispatchers
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import kotlin.coroutines.CoroutineContext
import org.jetbrains.skia.Canvas

/**
 * Compose scene driver that renders [content] onto a Skia [Canvas] for vector extraction.
 *
 * The only Compose API able to draw scene commands onto an arbitrary Skia canvas is the
 * `@InternalComposeUiApi` `androidx.compose.ui.scene` package. It has no binary-compatibility
 * guarantee and was reshaped in CMP 1.12 (a host-owned `FrameRecomposer` +
 * `measureAndLayout`/`draw`, replacing the pre-1.12 `coroutineContext`/`invalidate` +
 * `render(canvas, nanoTime)`). compose2pdf ships ONE binary, so this driver resolves the scene
 * API by reflection at runtime, detecting the shape by structure (presence of `FrameRecomposer`
 * and factory arity) rather than a version string. One jar therefore runs on 1.11 and 1.12+.
 *
 * Everything stable stays typed; only the divergent construction/drive calls are reflective. If
 * neither known shape resolves, [drawContent] fails fast with a descriptive [Compose2PdfException].
 */
internal object ComposeSceneRenderer {

    fun drawContent(
        canvas: Canvas,
        widthPx: Int,
        heightPx: Int,
        density: Density,
        content: @Composable () -> Unit,
    ) {
        val composeCanvas: Any = canvas.asComposeCanvas()
        val sizePacked: Long = IntSize(widthPx, heightPx).packedValue
        driver.render(density, sizePacked, composeCanvas, content)
    }

    // Detect + cache the strategy once. FrameRecomposer only exists on the >= 1.12 shape.
    private val driver: SceneDriver by lazy {
        if (classOrNull(FRAME_RECOMPOSER) != null) NextDriver else LegacyDriver
    }

    private const val FACTORY_CLASS = "androidx.compose.ui.scene.CanvasLayersComposeScene_skikoKt"
    private const val COMPOSE_SCENE = "androidx.compose.ui.scene.ComposeScene"
    private const val FRAME_RECOMPOSER = "androidx.compose.ui.platform.FrameRecomposer"
    private const val PLATFORM_CONTEXT_EMPTY = "androidx.compose.ui.platform.PlatformContext\$Empty"

    private val NO_OP: () -> Unit = {}

    private fun classOrNull(name: String): Class<*>? =
        try {
            Class.forName(name)
        } catch (e: ClassNotFoundException) {
            null
        }

    private fun fail(what: String, cause: Throwable? = null): Nothing =
        throw Compose2PdfException(
            "compose2pdf could not drive the Compose scene via reflection: $what. The installed " +
                "Compose Multiplatform version may have reshaped its internal " +
                "CanvasLayersComposeScene API. Please file an issue including your CMP version.",
            cause,
        )

    /** The single static factory on [FACTORY_CLASS] returning a ComposeScene with [paramCount] params. */
    private fun factory(paramCount: Int): Method {
        val cls = classOrNull(FACTORY_CLASS) ?: fail("$FACTORY_CLASS not found")
        return cls.declaredMethods.firstOrNull { m ->
            Modifier.isStatic(m.modifiers) &&
                m.name.startsWith("CanvasLayersComposeScene") &&
                !m.name.contains("\$default") &&
                m.returnType.name == COMPOSE_SCENE &&
                m.parameterCount == paramCount
        } ?: fail("no ${paramCount}-arg CanvasLayersComposeScene factory on $FACTORY_CLASS")
    }

    private fun platformContextEmpty(): Any {
        val cls = classOrNull(PLATFORM_CONTEXT_EMPTY) ?: fail("$PLATFORM_CONTEXT_EMPTY not found")
        return cls.getField("INSTANCE").get(null) ?: fail("PlatformContext.Empty.INSTANCE was null")
    }

    /** Public method (incl. inherited) by simple name + arity, skipping Kotlin `$default` bridges. */
    private fun method(target: Any, name: String, arity: Int): Method =
        target.javaClass.methods.firstOrNull { it.name == name && it.parameterCount == arity }
            ?: fail("no $name/$arity on ${target.javaClass.name}")

    /** setContent is (content) on <= 1.11 and (compositionContext, content) on >= 1.12. */
    private fun setContent(scene: Any, content: @Composable () -> Unit) {
        val m = scene.javaClass.methods.firstOrNull {
            it.name == "setContent" && it.parameterCount in 1..2
        } ?: fail("no setContent on ${scene.javaClass.name}")
        if (m.parameterCount == 1) {
            m.invoke(scene, content)
        } else {
            m.invoke(scene, null, content) // CompositionContext defaults to null
        }
    }

    private fun close(target: Any) {
        (target as? AutoCloseable)?.close() ?: method(target, "close", 0).invoke(target)
    }

    private sealed interface SceneDriver {
        fun render(density: Density, sizePacked: Long, composeCanvas: Any, content: @Composable () -> Unit)
    }

    /** CMP <= 1.11: factory(density, layoutDir, size, coroutineContext, platformContext, invalidate); render(canvas, nanoTime). */
    private object LegacyDriver : SceneDriver {
        override fun render(density: Density, sizePacked: Long, composeCanvas: Any, content: @Composable () -> Unit) {
            val scene = factory(paramCount = 6).invoke(
                null,
                density,
                LayoutDirection.Ltr,
                sizePacked,
                Dispatchers.Unconfined,
                platformContextEmpty(),
                NO_OP,
            ) ?: fail("CanvasLayersComposeScene factory returned null")
            try {
                setContent(scene, content)
                method(scene, "render", 2).invoke(scene, composeCanvas, 0L)
            } finally {
                close(scene)
            }
        }
    }

    /** CMP >= 1.12: FrameRecomposer(ctx, onError); factory(recomposer, density, layoutDir, size, platformContext, x, y); performFrame -> measureAndLayout -> draw(canvas). */
    private object NextDriver : SceneDriver {
        override fun render(density: Density, sizePacked: Long, composeCanvas: Any, content: @Composable () -> Unit) {
            val frClass = classOrNull(FRAME_RECOMPOSER) ?: fail("$FRAME_RECOMPOSER not found")
            val frameRecomposer = (frClass.constructors.firstOrNull { it.parameterCount == 2 }
                ?: fail("no 2-arg FrameRecomposer constructor"))
                .newInstance(Dispatchers.Unconfined, NO_OP)
            try {
                val scene = factory(paramCount = 7).invoke(
                    null,
                    frameRecomposer,
                    density,
                    LayoutDirection.Ltr,
                    sizePacked,
                    platformContextEmpty(),
                    NO_OP,
                    NO_OP,
                ) ?: fail("CanvasLayersComposeScene factory returned null")
                try {
                    setContent(scene, content)
                    method(frameRecomposer, "performFrame", 1).invoke(frameRecomposer, 0L)
                    method(scene, "measureAndLayout", 0).invoke(scene)
                    method(scene, "draw", 1).invoke(scene, composeCanvas)
                } finally {
                    close(scene)
                }
            } finally {
                close(frameRecomposer)
            }
        }
    }
}
```

- [ ] **Step 3: Simplify `build.gradle.kts` (remove variant selection)**

In `compose2pdf/build.gradle.kts`, replace the variant-selection block (the comment starting `// Compose Multiplatform reworked...` through the `logger.lifecycle(...)` line) with:

```kotlin
// The internal Compose scene API (androidx.compose.ui.scene) has no binary-compatibility
// guarantee and reshaped in 1.12. compose2pdf ships ONE binary, so ComposeSceneRenderer resolves
// the scene API by reflection at runtime (see its KDoc). Nothing version-specific in the build.
val composeVersion: String = libs.versions.compose.multiplatform.get()
logger.lifecycle("compose2pdf: reflective ComposeSceneRenderer, base Compose $composeVersion")
```

Then remove the `sourceSets.named("main") { kotlin.srcDir("src/$composeSceneVariant/kotlin") }` block from inside `kotlin { ... }`, leaving:

```kotlin
kotlin {
    jvmToolchain(17)
    compilerOptions {
        freeCompilerArgs.add("-opt-in=androidx.compose.ui.InternalComposeUiApi")
    }
}
```

- [ ] **Step 4: Delete the source variants**

Run: `git rm -r compose2pdf/src/cmpLegacy compose2pdf/src/cmpNext`
Expected: both `ComposeSceneRenderer.kt` files staged for deletion.

- [ ] **Step 5: Build + full test suite on the base (validates LegacyDriver, incl. the composable-through-`invoke` risk)**

Run: `./gradlew :compose2pdf:build :fidelity-test:test`
Expected: `BUILD SUCCESSFUL`, log line `compose2pdf: reflective ComposeSceneRenderer, base Compose 1.11.1`. On 1.11.1 the `FrameRecomposer` class is absent → `LegacyDriver` runs, so this exercises the reflective factory, `setContent(content)`, and `render(canvas, nanoTime)` end to end; the fidelity tests confirm byte-level output is unchanged.

**If compilation fails** on `m.invoke(scene, content)` (composable value rejected as an `Object` arg): change `setContent` to cast via `@Suppress("UNCHECKED_CAST") val fn = content as kotlin.jvm.functions.Function2<*, *, *>` and pass `fn` (and `null, fn` in the 2-arg branch). Re-run.
**If a fidelity test fails** (output differs), stop and diff the failing case — the reflective legacy path must reproduce the old `cmpLegacy` behaviour exactly.

- [ ] **Step 6: Commit**

```bash
git add compose2pdf/src/main/kotlin/com/chrisjenx/compose2pdf/internal/ComposeSceneRenderer.kt compose2pdf/build.gradle.kts compose2pdf/src/cmpLegacy compose2pdf/src/cmpNext
git commit -m "Replace scene-driver source variants with a reflective driver

One binary now resolves the internal CanvasLayersComposeScene API by reflection
at runtime (structure-based detection, fail-fast on unknown shapes) instead of
compiling one of two source variants. ComposeToSvg is unchanged; base (1.11.1)
build + fidelity tests stay green.

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 2: Prove one binary spans versions (the payoff)

**Files:** none changed (verification task; any fix lands in `ComposeSceneRenderer.kt`)

**Interfaces:**
- Consumes: `compat-consumer` harness + `renderToPdf` (from the earlier design), the published jar from mavenLocal.

- [ ] **Step 1: Publish the reflective jar to mavenLocal**

Run: `GRADLE_RO_DEP_CACHE="$HOME/.gradle/caches" ./gradlew -g "$CLAUDE_JOB_DIR/tmp/ghome" :compose2pdf:publishToMavenLocal`
Expected: `BUILD SUCCESSFUL`; `signMavenPublication SKIPPED`. Jar at `~/.m2/repository/com/chrisjenx/compose2pdf/1.1.4-SNAPSHOT/`.

- [ ] **Step 2: Run the consumer against the base (1.11.1) — must pass**

Run: `./gradlew -p compat-consumer run -PcomposeVersion=1.11.1 -PkotlinVersion=2.4.0 -Pcompose2pdfVersion=1.1.4-SNAPSHOT`
Expected: `compat-consumer OK: rendered <N>-byte PDF`, `BUILD SUCCESSFUL`.

- [ ] **Step 3: Run against previous stable (1.10.3) — must pass (LegacyDriver on an older runtime)**

Run: `./gradlew -p compat-consumer run -PcomposeVersion=1.10.3 -PkotlinVersion=2.4.0 -Pcompose2pdfVersion=1.1.4-SNAPSHOT`
Expected: `compat-consumer OK`, `BUILD SUCCESSFUL`.

- [ ] **Step 4: Run against 1.12.0-beta01 — must now PASS (was `NoSuchMethodError`)**

Run: `./gradlew -p compat-consumer run -PcomposeVersion=1.12.0-beta01 -PkotlinVersion=2.4.0 -Pcompose2pdfVersion=1.1.4-SNAPSHOT`
Expected: `compat-consumer OK: rendered <N>-byte PDF`, `BUILD SUCCESSFUL`. This is the whole point — the same jar that failed before now drives the 1.12 scene via `NextDriver`.

**If Step 4 fails:** read the exception. `NoSuchMethodException`/`ClassNotFoundException` from a reflective lookup means a param-shape assumption is off — inspect with `javap` on the 1.12.0-beta01 `ui-desktop` jar (`CanvasLayersComposeScene_skikoKt`, `ComposeScene`, `FrameRecomposer`), adjust arg order/arity in `NextDriver`, re-publish (Step 1), re-run. A null-check failure on the 2-arg `setContent` means switch that branch to `setContent$default` (invoke `setContent$default(scene, null, content, 1, null)` — mask bit 0 skips the CompositionContext).

- [ ] **Step 5: Commit any driver fix (skip if Steps 2–4 passed unchanged)**

```bash
git add compose2pdf/src/main/kotlin/com/chrisjenx/compose2pdf/internal/ComposeSceneRenderer.kt
git commit -m "Fix reflective NextDriver for CMP 1.12 scene API

Adjust <arg order / setContent> after validating the published binary against
1.12.0-beta01 via compat-consumer.

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 3: Flip the compat matrix to all-green + enrich the smoke

**Files:**
- Modify: `compat-consumer/src/main/kotlin/com/chrisjenx/compat/Smoke.kt`
- Modify: `.github/workflows/compatibility.yml`

**Interfaces:**
- Consumes: `renderToPdf { ... }` public API.
- Produces: a smoke check exercising text + a filled shape.

- [ ] **Step 1: Enrich the smoke to draw real content**

Replace the body of `compat-consumer/src/main/kotlin/com/chrisjenx/compat/Smoke.kt` with:

```kotlin
package com.chrisjenx.compat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.chrisjenx.compose2pdf.renderToPdf

/**
 * Renders a small PDF (text + a filled shape) using the PUBLISHED compose2pdf jar against whatever
 * Compose runtime this build resolved. Exits non-zero (via check) on failure, failing the CI job.
 */
fun main() {
    val pdf = renderToPdf {
        Column {
            Text("compose2pdf compatibility smoke test")
            Box(Modifier.size(48.dp).background(Color.Red))
        }
    }
    check(pdf.size > 100) { "PDF suspiciously small: ${pdf.size} bytes" }
    val header = pdf.copyOfRange(0, 5).toString(Charsets.US_ASCII)
    check(header == "%PDF-") { "Not a PDF - header was '$header'" }
    println("compat-consumer OK: rendered ${pdf.size}-byte PDF")
}
```

- [ ] **Step 2: Re-publish and re-run all three targets with the enriched smoke**

Run:
```bash
GRADLE_RO_DEP_CACHE="$HOME/.gradle/caches" ./gradlew -g "$CLAUDE_JOB_DIR/tmp/ghome" :compose2pdf:publishToMavenLocal
for V in 1.11.1 1.10.3 1.12.0-beta01; do
  ./gradlew -p compat-consumer run -PcomposeVersion=$V -PkotlinVersion=2.4.0 -Pcompose2pdfVersion=1.1.4-SNAPSHOT \
    2>&1 | grep -iE "compat-consumer OK|BUILD (SUCCESSFUL|FAILED)" | sed "s/^/[$V] /"
done
```
Expected: `[1.11.1] ... OK`, `[1.10.3] ... OK`, `[1.12.0-beta01] ... OK`, each `BUILD SUCCESSFUL`.

- [ ] **Step 3: Update the workflow framing (1.12 now passes)**

In `.github/workflows/compatibility.yml`, replace the comment above the two smoke-test steps:

```yaml
      # Pre-release targets (version contains '-') are informational: the shipped
      # cmpLegacy binary is not expected to run on a cmpNext runtime.
```

with:

```yaml
      # The one published binary is expected to run on EVERY matrix version (the scene
      # driver resolves the CMP API by reflection). Pre-release cells (version contains '-')
      # stay non-blocking only as a guard against unrelated beta breakage, not the API boundary.
```

Leave the two `continue-on-error: ${{ contains(matrix.version.compose-version, '-') }}` lines as-is.

- [ ] **Step 4: Verify YAML still parses**

Run: `python3 -c "import yaml; yaml.safe_load(open('.github/workflows/compatibility.yml')); print('yaml ok')"`
Expected: `yaml ok`.

- [ ] **Step 5: Commit**

```bash
git add compat-consumer/src/main/kotlin/com/chrisjenx/compat/Smoke.kt .github/workflows/compatibility.yml
git commit -m "Expect all matrix versions green; enrich compat smoke

The reflective driver makes the one binary run on every tested CMP version, so
1.12.0-beta01 now passes. Smoke renders text + a filled shape to exercise real
draw commands. Pre-release cells stay non-blocking only for unrelated breakage.

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 4: Update the CLAUDE.md gotcha

**Files:**
- Modify: `CLAUDE.md`

**Interfaces:** none (docs).

- [ ] **Step 1: Rewrite the "Version-specific scene driver" gotcha**

In `CLAUDE.md` (under Gotchas → Rendering), replace the `**Version-specific scene driver**` bullet (the one describing `cmpLegacy`/`cmpNext` source variants and keeping their `drawContent` signatures in sync) with:

```markdown
- **Reflective scene driver** — `CanvasLayersComposeScene` is `@InternalComposeUiApi` ("subject to change without notice") and was reshaped in CMP 1.12 (`coroutineContext`/`invalidate` + `render(canvas, nanoTime)` → `FrameRecomposer` + `measureAndLayout`/`draw(canvas)`). Because compose2pdf ships **one** binary and there is no stable API for drawing scene commands onto a Skia canvas, `internal ComposeSceneRenderer` resolves the scene API by **reflection** at runtime — detecting the shape by structure (presence of `FrameRecomposer`, factory arity), not a version string — so the single jar runs on 1.11 and 1.12+. Stable calls stay typed; only construction/drive are reflective; an unrecognized future shape throws `Compose2PdfException`. Cross-version behaviour is proven by the `compat-consumer` matrix.
```

- [ ] **Step 2: Confirm the stale variant references are gone**

Run: `grep -n "cmpLegacy\|cmpNext" CLAUDE.md || echo "no stale variant references (correct)"`
Expected: `no stale variant references (correct)`. (The `feedback_version_agnostic_naming` memory referenced these names historically — that's fine; the codebase no longer uses them.)

- [ ] **Step 3: Commit**

```bash
git add CLAUDE.md
git commit -m "Document the reflective scene driver in CLAUDE.md

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Self-Review

**Spec coverage:**
- "Replace two variants with one reflective driver" → Task 1 (create driver, delete variants, build change). ✅
- Structure-based detection (FrameRecomposer + factory arity), stable-typed vs reflective split, fail-fast → encoded in the Task 1 code. ✅
- Empirical composable-through-`invoke` risk → front-loaded in Task 1 Step 5 (compile + base fidelity) with a stated cast fallback. ✅
- One-binary-spans-versions payoff → Task 2 (consumer on 1.11.1/1.10.3/1.12.0-beta01, 1.12 now passes) with a `javap`/`setContent$default` fallback. ✅
- Matrix flips to all-green + enriched smoke → Task 3. ✅
- CLAUDE.md gotcha update → Task 4. ✅
- Folds into PR #13 (same branch, no push unless asked) → Global Constraints. ✅

**Placeholder scan:** No TBD/TODO. `<N>` appears only in expected console output (a rendered byte count), and `<arg order / setContent>` only inside a conditional commit message that is skipped unless a fix was made — both are legitimately variable, not unfilled plan content. All code steps carry complete code. ✅

**Type/contract consistency:** `drawContent(canvas, widthPx, heightPx, density, content)` matches the current `ComposeToSvg` call site and both deleted variants. `SceneDriver.render(density, sizePacked, composeCanvas, content)` is used identically by both `LegacyDriver` and `NextDriver`. Helper names (`classOrNull`, `factory`, `platformContextEmpty`, `method`, `setContent`, `close`, `fail`, `NO_OP`) are defined once and referenced consistently. Constants (`FACTORY_CLASS`, `FRAME_RECOMPOSER`, `PLATFORM_CONTEXT_EMPTY`, `COMPOSE_SCENE`) match the `javap`-verified names in the spec. ✅
