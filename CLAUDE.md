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
renderToPdf(config, density, mode, defaultFontFamily, pagination, header, footer) { content } → ByteArray  // auto-paginates by default
renderToPdf(outputStream, config, density, mode, defaultFontFamily, pagination, header, footer) { content }  // streaming variant
renderToPdf(pages, config, density, mode, defaultFontFamily) { pageIndex → content } → ByteArray  // manual pages
renderToPdf(outputStream, pages, config, density, mode, defaultFontFamily) { pageIndex → content }  // streaming variant
PdfLink(href) { content }
PaginatedColumn(modifier) { content }  // page-break-aware Column, reads page config automatically
PdfRoundedCornerShape(topStart, topEnd, bottomEnd, bottomStart)
Shape.asPdfSafe()
```

Types: `PdfPageConfig` (A4/A4WithMargins/Letter/LetterWithMargins/A3/A3WithMargins + `landscape()`), `PdfMargins` (None/Narrow/Normal + `symmetric()`), `PdfPagination` (AUTO/SINGLE_PAGE), `Density`, `RenderMode` (VECTOR/RASTER), `InterFontFamily`, `Compose2PdfException`, `LocalPdfPageConfig`, `PdfPageInfo` (pageIndex, pageCount, pageNumber).

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
- **Shaping fonts are embedded automatically** — the PDF must draw with the same font Skia shaped the text with, or glyph-width mismatches squash letters. `ComposeFontStack` (reflective, best-effort — same philosophy as the scene driver) walks `LocalFontFamilyResolver → SkiaFontLoader → FontCache` to harvest every typeface Compose loaded (custom `Font(file/resource)` families live ONLY there — Skia registers them under opaque cache keys, not family names) and to query the composition's Skia `FontCollection`; `SkiaTypefaceEmbedder` rebuilds embeddable TTF bytes from `Typeface.getTableTags/getTableData`. Resolution order: bundled Inter → captured typefaces → FontCollection → `FontMgr.default` → filesystem → standard-14 (+ per-glyph Tz compression so substituted glyphs can't collide). Reflective member names are verified against CMP 1.10/1.11/1.12 jars.
- **Variable fonts excluded from filesystem search** — `FontResolver.isVariableFont()` skips font files with `fvar` table. Skia-sourced typefaces embed at their default instance instead; only instances styled away from default on `wght`/`wdth`/`slnt`/`ital` axes are rejected (macOS `.SF NS` regular reports non-default `opsz` and must still embed)
- **SVGCanvas bezier approximation** — non-uniform rounded rects become complex bezier paths; use `PdfRoundedCornerShape`
- **Bundled fonts loaded from classpath** — `FontResolver` loads Inter fonts directly from `InputStream`, no temp files
- **`Compose2PdfException` wraps rendering errors** — `IllegalArgumentException` (precondition failures) passes through unwrapped
- **OutputStream overloads don't close the stream** — caller owns the stream lifecycle; `PDDocument.use { it.save(outputStream) }` is called internally
- **Raster slices draw top-aligned at proportional height** — `addBitmapPage(topPt, heightPt)`; a partial last slice must never be stretched to the full content rect.

### Auto-pagination
- **Measures in tall scene** — `PdfRenderer` uses a 200K px max scene height for measurement; Compose `Constraints` limit is ~262K px
- **Falls back for single-page content** — If measured height ≤ page height or ≥ max height (fillMaxHeight detected), falls back to original single-page rendering path for identical output
- **PaginatedColumn keeps children together** — Inserts padding at page boundaries so no direct child is split; oversized children (taller than a page) flow across pages
- **Header/footer slots render inside the page margins, edge-anchored** — bands sit `SLOT_EDGE_INSET_PT` (18pt, ~0.25in) from the physical page edge, like a browser print header/footer. Body content keeps the configured margins untouched unless a band + inset + a 10pt gap exceeds the configured margin, in which case that side's inset grows (`max()`) just enough to fit it. `LocalPdfPageConfig` exposes the effective content area — equal to the configured margins in the common case. Slot heights are measured once with a `PdfPageInfo(0, 2)` sentinel; height must be stable across pages (taller content is clipped). Null slots take the exact pre-slots code path (fidelity guarantee), pinned by `NullSlotRegressionTest` golden files — if an intentional render change breaks it, delete `compose2pdf/src/test/resources/golden/` and re-run twice.

### Testing
- **Fidelity tests assume identical render path** — Changing `renderToPdf` default behavior (e.g., wrapping content in extra layout layers or using a taller scene) can break fidelity comparisons; single-page content must fall back to the original render path
- **Compose `Placeable` is not fakeable** — `width`/`height` are final; test layout logic with raw `List<Int>` heights instead of mock `Placeable` objects

## Code Conventions

- Package: `com.chrisjenx.compose2pdf`
- Internal implementation in `com.chrisjenx.compose2pdf.internal`
- `internal` visibility by default for implementation classes
- Public API: `renderToPdf()`, `PdfLink()`, `PaginatedColumn()`, `LocalPdfPageConfig`, `PdfPageConfig`, `PdfMargins`, `PdfPagination`, `PdfPageInfo`, `RenderMode`, `Density`, `InterFontFamily`, `PdfRoundedCornerShape`, `Shape.asPdfSafe()`, `Compose2PdfException` — everything else is `internal`
- Tests use `kotlin-test`

## Publishing & Releasing

Published to **Maven Central** (Central Portal) under group ID `com.chrisjenx` via the **vanniktech maven-publish plugin** (`publishToMavenCentral()` + `signAllPublications()` in `compose2pdf/build.gradle.kts`). Version lives in `gradle.properties` (`version=`); `main` sits on a `-SNAPSHOT` between releases.

**Cutting a release** — fully automated by the **Release** workflow (`.github/workflows/release.yml`), a manual `workflow_dispatch`:

```bash
gh workflow run release.yml -f version=X.Y.Z   # e.g. 1.3.0 — no leading "v"
```

It validates the version (semver format + tag not already present), builds and runs unit + fidelity tests on ubuntu & macOS (JDK 17), runs `publishAndReleaseToMavenCentral`, creates the `vX.Y.Z` tag and a GitHub Release (`--generate-notes`), then bumps `gradle.properties` to the next patch `-SNAPSHOT` and pushes to `main`. **Before triggering**, roll the changelog's `## Unreleased` heading to `## X.Y.Z` (entries carry no date). Version follows semver from the last `vX.Y.Z` tag: new feature → minor, fix-only → patch.

**Snapshots** — every push to `main` touching library sources auto-publishes a `-SNAPSHOT` via `snapshot.yml` (`publishAllPublicationsToMavenCentralRepository`).

**Credentials** — publishing auth lives in GitHub Actions secrets (`MAVEN_CENTRAL_USERNAME`/`MAVEN_CENTRAL_PASSWORD` + in-memory signing key `SIGNING_KEY_ID`/`SIGNING_KEY`/`SIGNING_KEY_PASSWORD`), passed to Gradle as `ORG_GRADLE_PROJECT_*`. For a local `publishToMavenLocal`, set the matching `mavenCentral*` / `signingInMemory*` Gradle properties in `local.properties`.
