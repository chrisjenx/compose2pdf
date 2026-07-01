# compose2pdf

## Project Overview

**compose2pdf** is a Kotlin JVM library that renders Compose Desktop content to PDF.

## Module Map

```
├── compose2pdf/          # Library: public API + SVG→PDF converter + font resolver
├── examples/             # Runnable examples (not published)
├── fidelity-test/        # Visual regression tests (not published)
└── docs/                 # Jekyll docs site (GitHub Pages, just-the-docs theme)
```

## Tech Stack

- **Kotlin** 2.3.20, JVM target only
- **Compose Multiplatform** 1.10.3 (Desktop)
- **Apache PDFBox** 3.0.7 (SVG→PDF conversion, image embedding, font subsetting)
- **Gradle** 8.14 — versions centralized in `gradle/libs.versions.toml`

## Build Commands

```bash
./gradlew :compose2pdf:build           # Build library
./gradlew :compose2pdf:test            # Run unit tests
./gradlew :fidelity-test:test          # Run fidelity tests (vector + raster PDF)
./gradlew :fidelity-test:test --rerun-tasks  # Force re-run (bypass Gradle cache)
./gradlew :compose2pdf:publishToMavenLocal  # Publish to ~/.m2
./gradlew :compose2pdf:compileKotlin        # Quick compile check (no tests)
./gradlew :examples:run                     # Run examples, output to examples/build/output/
open fidelity-test/build/reports/fidelity/index.html  # View fidelity report (macOS)
cd docs && bundle exec jekyll serve             # Preview docs site locally (http://localhost:4000)
```

## Fidelity Tests

Visual regression suite comparing Compose reference renders against rasterized PDFs.
- Report: `fidelity-test/build/reports/fidelity/index.html`
- Diff uses neighborhood-minimum comparison (radius=3, tolerance=8) to ignore anti-aliasing
- Gradle caches test results aggressively — use `--rerun-tasks` after changing `ImageMetrics` or `FidelityReport`

## CI

GitHub Actions (`.github/workflows/ci.yml`): build + unit tests + fidelity tests on ubuntu/macos, JDK 17. Uses `xvfb-run` for headless Compose on Linux.

Compose Multiplatform compatibility matrix (`.github/workflows/compatibility.yml`): verifies the **single published binary** (built against the pinned CMP in `libs.versions.toml`) runs on each of the 3 most recent CMP versions (defined in `.github/compose-versions.json`). Auto-updated weekly by `.github/workflows/update-compose-versions.yml`, which also bumps the pinned build base to the latest stable. Rather than recompiling per version, it `publishToMavenLocal`s the jar and runs the standalone `compat-consumer/` build against each target CMP runtime (the Compose plugin at that version resolves the matching Compose + Skiko). Version-incompatible internal Compose APIs are handled at **runtime by reflection** in `ComposeSceneRenderer` (see Gotchas → Rendering), so one binary spans all tested versions; pre-release cells are non-blocking.

`.github/compose-versions.json` is the single source of truth for supported versions. The human-facing tables in `docs/compatibility.md` and `README.md` are **generated** from it by `.github/scripts/render-compat-tables.py` (run in the update workflow) between `<!-- BEGIN cmp-matrix -->` / `<!-- END cmp-matrix -->` markers — edit the JSON and rerun the script, never the tables by hand. The **current** row is whichever matrix entry matches the pinned `compose-multiplatform` in `libs.versions.toml`, and shows the pinned `kotlin` (not the matrix's CI Kotlin override). CI's `docs-sync` job (`ci.yml`) runs the script with `--check` and fails the PR if the tables have drifted.

## Public API

```kotlin
renderToPdf(config, density, mode, defaultFontFamily, pagination) { content } → ByteArray  // auto-paginates by default
renderToPdf(outputStream, config, density, mode, defaultFontFamily, pagination) { content }  // streaming variant
renderToPdf(pages, config, density, mode, defaultFontFamily) { pageIndex → content } → ByteArray  // manual pages
renderToPdf(outputStream, pages, config, density, mode, defaultFontFamily) { pageIndex → content }  // streaming variant
PdfLink(href) { content }
PaginatedColumn(modifier) { content }  // page-break-aware Column, reads page config automatically
PdfRoundedCornerShape(topStart, topEnd, bottomEnd, bottomStart)
Shape.asPdfSafe()
```

Types: `PdfPageConfig` (A4/A4WithMargins/Letter/LetterWithMargins/A3/A3WithMargins + `landscape()`), `PdfMargins` (None/Narrow/Normal + `symmetric()`), `PdfPagination` (AUTO/SINGLE_PAGE), `Density`, `RenderMode` (VECTOR/RASTER), `InterFontFamily`, `Compose2PdfException`, `LocalPdfPageConfig`.

## Key Files

- `compose2pdf/src/main/kotlin/.../Compose2Pdf.kt` — Public API entry points
- `compose2pdf/src/main/kotlin/.../internal/PdfRenderer.kt` — Rendering orchestrator (vector, raster, auto-pagination)
- `compose2pdf/src/main/kotlin/.../internal/ComposeToSvg.kt` — Compose → SVG + measurement
- `compose2pdf/src/main/kotlin/.../internal/SvgToPdfConverter.kt` — SVG → PDF pages
- `compose2pdf/src/main/kotlin/.../internal/PaginatedColumn.kt` — Smart page-break layout
- `compose2pdf/src/main/kotlin/.../internal/FontResolver.kt` — Font resolution + subsetting
- `fidelity-test/src/test/.../FidelityFixtures.kt` — All fidelity test composables

## Architecture

```
Compose content → ComposeToSvg.render() → SVG string
  → SvgToPdfConverter (orchestrator)
    ├── SvgPathParser (SVG path data → PDFBox paths)
    ├── SvgColorParser + SvgColor (CSS/SVG color parsing)
    ├── SvgShapeRenderer (ellipse, rounded rect geometry)
    └── CoordinateTransform (SVG Y-down ↔ PDF Y-up)
  → PDFBox vector drawing commands → PDDocument

Callers: Compose2Pdf.kt saves PDDocument → OutputStream or ByteArray

Raster fallback: ImageComposeScene → bitmap → PDFBox embedded image

Auto-pagination: PaginatedColumn (smart page breaks)
  → ComposeToSvg.measureContentHeight() (lightweight measurement)
  → ComposeToSvg.renderWithMeasurement() (full SVG + height)
  → SvgToPdfConverter.addAutoPages() (clip + offset per page)
```

## Gotchas

### Docs Site
- **URL structure** — standalone pages (`getting-started.md`) produce `/page.html` URLs; directory index pages (`usage/index.md`) produce `/directory/` URLs. Don't use trailing slashes for standalone pages.

### Rendering
- **`@InternalComposeUiApi` opt-in required** — `CanvasLayersComposeScene` is internal Compose API
- **Reflective scene driver** — `CanvasLayersComposeScene` is `@InternalComposeUiApi` ("subject to change without notice in major/minor/patch") and was reshaped in CMP 1.12 (`coroutineContext`/`invalidate` + `render(canvas, nanoTime)` → `FrameRecomposer` + `measureAndLayout`/`draw(canvas)`). Because compose2pdf ships **one** binary and there is no stable API for drawing scene commands onto a Skia canvas, `internal ComposeSceneRenderer` (in `src/main`) resolves the scene API by **reflection** at runtime — detecting the shape by structure (presence of `FrameRecomposer`, factory arity), not a version string — so the single jar runs on 1.11 and 1.12+. Stable calls stay typed (`asComposeCanvas`, `IntSize`, `Density`, the Skia canvas); only construction/drive are reflective, and reflective `invoke`/`newInstance` unwrap `InvocationTargetException` so Compose's own exceptions propagate normally. An unrecognized future shape throws `Compose2PdfException` (fail-fast). Cross-version behaviour is proven by the `compat-consumer` matrix. **Do not reintroduce build-time `cmpLegacy`/`cmpNext` source variants** — add a new reflective branch instead if CMP reshapes the API again.
- **Variable fonts excluded** — `FontResolver.isVariableFont()` skips fonts with `fvar` table
- **SVGCanvas bezier approximation** — non-uniform rounded rects become complex bezier paths; use `PdfRoundedCornerShape`
- **Bundled fonts loaded from classpath** — `FontResolver` loads Inter fonts directly from `InputStream`, no temp files
- **`Compose2PdfException` wraps rendering errors** — `IllegalArgumentException` (precondition failures) passes through unwrapped
- **OutputStream overloads don't close the stream** — caller owns the stream lifecycle; `PDDocument.use { it.save(outputStream) }` is called internally

### Auto-pagination
- **Measures in tall scene** — `PdfRenderer` uses a 200K px max scene height for measurement; Compose `Constraints` limit is ~262K px
- **Falls back for single-page content** — If measured height ≤ page height or ≥ max height (fillMaxHeight detected), falls back to original single-page rendering path for identical output
- **PaginatedColumn keeps children together** — Inserts padding at page boundaries so no direct child is split; oversized children (taller than a page) flow across pages

### Testing
- **Fidelity tests assume identical render path** — Changing `renderToPdf` default behavior (e.g., wrapping content in extra layout layers or using a taller scene) can break fidelity comparisons; single-page content must fall back to the original render path
- **Compose `Placeable` is not fakeable** — `width`/`height` are final; test layout logic with raw `List<Int>` heights instead of mock `Placeable` objects

## Code Conventions

- Package: `com.chrisjenx.compose2pdf`
- Internal implementation in `com.chrisjenx.compose2pdf.internal`
- `internal` visibility by default for implementation classes
- Public API: `renderToPdf()`, `PdfLink()`, `PaginatedColumn()`, `LocalPdfPageConfig`, `PdfPageConfig`, `PdfMargins`, `PdfPagination`, `RenderMode`, `Density`, `InterFontFamily`, `PdfRoundedCornerShape`, `Shape.asPdfSafe()`, `Compose2PdfException` — everything else is `internal`
- Tests use `kotlin-test`

## Publishing

Maven Central via Sonatype (`s01.oss.sonatype.org`), group ID `com.chrisjenx`. Requires `ossrhUsername`, `ossrhPassword`, and `signing.keyId` in `local.properties` or environment variables.
