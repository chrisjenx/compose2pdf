# Single binary across CMP versions — reflective scene driver

**Date:** 2026-07-01
**Status:** Approved (design), pending implementation
**Builds on:** `2026-06-30-cmp-compatibility-binary-testing-design.md` (same branch / PR #13)

## Problem

compose2pdf ships **one** binary but currently bakes in **one** CMP scene-driver
variant chosen at build time (`src/cmpLegacy` for ≤1.11, `src/cmpNext` for ≥1.12).
So the published jar (built `cmpLegacy` against 1.11.1) throws
`NoSuchMethodError: CanvasLayersComposeScene-3tKcejY$default(...)` on a 1.12
runtime. The goal is now to **eliminate** that incompatibility — one jar that runs
on 1.11 *and* 1.12+ — the way androidx/Metro ship one artifact across many versions.

## Why the usual technique doesn't apply

androidx/Metro span versions by depending only on **stable public API** (+ binary
compatibility validation). That option is unavailable here:

- The vector path must draw *commands* onto an SVG-recording Skia `Canvas`.
- `ImageComposeScene` (the only stable-ish scene) exposes `render(nanoTime): Image`
  in both 1.11.1 and 1.12 — it **rasterizes to a bitmap**, so it can't produce
  vector output.
- The only canvas-drawing scene API is `androidx.compose.ui.scene.*`
  (`CanvasLayersComposeScene` / `ComposeScene`), which is `@InternalComposeUiApi`
  with no compatibility guarantee.

There is no stable API to migrate to. The only way to get a single binary spanning
the internal-API break is **runtime dispatch via reflection**.

## The exact divergence (verified via `javap`)

| | CMP 1.11.1 (legacy) | CMP 1.12.0-beta01 (next) |
|---|---|---|
| Factory (`CanvasLayersComposeScene_skikoKt`, static, returns `ComposeScene`) | `CanvasLayersComposeScene-3tKcejY(Density, LayoutDirection, IntSize, CoroutineContext, PlatformContext, Function0)` | `CanvasLayersComposeScene-QZifGB8(FrameRecomposer, Density, LayoutDirection, IntSize, PlatformContext, Function0, Function0)` |
| `ComposeScene.setContent` | `(Function2)` | `(CompositionContext, Function2)` + `$default` |
| Drive | `render(Canvas, long)` | `measureAndLayout()` + `draw(Canvas)` |
| Frame owner | (scene-internal, via `coroutineContext`) | `FrameRecomposer(CoroutineContext, Function0)` + `performFrame(long)` |
| Cleanup | `close()` (AutoCloseable) | `close()` (AutoCloseable) |

Note: the factory names are **mangled** (`-3tKcejY`, `-QZifGB8`) because `IntSize`
is an unboxed value class (`long` at the JVM level). Both factories require a
`PlatformContext` the old source obtained via defaults.

## Design

Replace the two source variants with a single `ComposeSceneRenderer` in `src/main`.
Public signature is unchanged, so `ComposeToSvg` is untouched:

```kotlin
internal object ComposeSceneRenderer {
    fun drawContent(canvas: Canvas, widthPx: Int, heightPx: Int, density: Density,
                    content: @Composable () -> Unit)
}
```

### What stays typed (stable, compiled against the 1.11.1 base)

`org.jetbrains.skia.Canvas`, `androidx.compose.ui.graphics.asComposeCanvas`
(identical signature both versions — verified), `IntSize`, `Density`,
`LayoutDirection.Ltr`, `kotlinx.coroutines.Dispatchers.Unconfined`, the
`ComposeScene` reference type, and the `Function0` / composable `content` values.
Only the divergent calls are reflective.

### Reflective core (resolved once, cached in the object)

- **Discriminator:** `Class.forName("androidx.compose.ui.platform.FrameRecomposer")`
  succeeds → *next* strategy; `ClassNotFoundException` → *legacy* strategy.
  (Presence of `FrameRecomposer` is the structural API marker — no version string.)
- **Factory lookup by structure:** among `CanvasLayersComposeScene_skikoKt`'s
  `public static` methods returning `ComposeScene`, excluding `$default`, select the
  single match. Never hardcode the mangled name.
- **Argument construction:**
  - `IntSize` → its unboxed `long` via `IntSize(w, h).packedValue` (value-class repr).
  - `PlatformContext` → `PlatformContext$Empty.INSTANCE` (reflective static field on
    the nested Kotlin `object`).
  - no-op `Function0<Unit>` lambdas written directly in Kotlin.
  - `FrameRecomposer` (next only) constructed reflectively:
    `getConstructor(CoroutineContext, Function0).newInstance(Dispatchers.Unconfined, {})`.
  - `density` passed through as received; `LayoutDirection.Ltr` direct.
- **Drive:**
  - legacy: `setContent(content)` → `render(composeCanvas, 0L)`
  - next: build `FrameRecomposer` → set content (the `CompositionContext` arg is
    optional — pass `null` to the 2-arg `setContent`, or invoke `setContent$default`
    with the skip mask if a null intrinsic check rejects it) → `performFrame(0L)`
    → `measureAndLayout()` → `draw(composeCanvas)`
- **Cleanup:** `scene.close()` and (next) `frameRecomposer.close()` via
  `java.lang.AutoCloseable`.

Resolved `Method`/`Constructor`/`Field` handles and the chosen strategy are cached
after first use (renders are repeated).

### Passing the composable through reflection

`content: @Composable () -> Unit` is a `Function2<Composer, Int, Unit>` at runtime.
It is passed to the reflective `setContent` as `Any`. **This is the one empirical
risk** — validated against both runtimes at the start of implementation. Fallback if
it resists: a minimal typed `setContent` bridge (the method common to both versions),
keeping only construction + drive reflective.

### Fail-fast on an unknown future API

If neither strategy resolves (a future CMP reshapes the API again), throw a
descriptive `Compose2PdfException` naming the unrecognized scene API — the compat
matrix catches this against new versions before release. No silent fallback.

### Build change

Remove the `composeSceneVariant` / `srcDir("src/$composeSceneVariant/kotlin")` logic
from `compose2pdf/build.gradle.kts`; delete `src/cmpLegacy` and `src/cmpNext`. Keep a
lifecycle log line (now "compose2pdf: reflective ComposeSceneRenderer, base Compose
<version>"). The `-opt-in=InternalComposeUiApi` flag stays (still referencing the
`ComposeScene` type / stable-ish interop).

## Testing

The fix flips the matrix from "expect 1.12 to fail" to **all-green**:

- The `compat-consumer` smoke runs the published jar against **1.10.3, 1.11.1,
  1.12.0-beta01** and **all must pass** — the proof that reflection dispatch works
  on each runtime.
- Enrich the smoke to render text **and** a shape (`Box` with background), exercising
  real draw commands, not just an empty scene.
- Drop the "pre-release expected failure" framing in `compatibility.yml`. Keep
  `continue-on-error` on pre-release cells **only** as a guard against genuine,
  unrelated beta breakage — 1.12.0-beta01 is now expected to pass.
- Validate all three runtimes locally (`publishToMavenLocal` + consumer run) before
  committing.
- Existing `:compose2pdf:test` + `:fidelity-test:test` continue to run against the
  base (1.11.1) and must stay green (the reflective driver must produce byte-identical
  behaviour on the base version).

## Non-goals / YAGNI

- No attempt to support CMP < 1.9 or unknown future shapes beyond fail-fast.
- No MethodHandle micro-optimization beyond caching resolved reflection handles.
- No change to `ComposeToSvg` or any public API.
- Raster path (`ImageComposeScene`) is already version-stable — untouched.

## Files touched

- Create: `compose2pdf/src/main/kotlin/com/chrisjenx/compose2pdf/internal/ComposeSceneRenderer.kt` (reflective).
- Delete: `compose2pdf/src/cmpLegacy/`, `compose2pdf/src/cmpNext/`.
- Modify: `compose2pdf/build.gradle.kts` (drop variant srcDir selection; keep log + opt-in).
- Modify: `.github/workflows/compatibility.yml` (drop "expected failure" framing; 1.12 now green).
- Modify: `compat-consumer/src/main/kotlin/com/chrisjenx/compat/Smoke.kt` (render text + shape).
- Update: CLAUDE.md "Version-specific scene driver" gotcha (reflective, not two variants).
