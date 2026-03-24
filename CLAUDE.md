# compose2pdf

## Project Overview

**compose2pdf** is a Kotlin JVM library that renders Compose Desktop content to PDF.

## Module Map

```
├── compose2pdf/          # Library: public API + SVG→PDF converter + font resolver
└── fidelity-test/        # Visual regression tests (not published)
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
open fidelity-test/build/reports/fidelity/index.html  # View fidelity report (macOS)
```

## Fidelity Tests

Visual regression suite comparing Compose reference renders against rasterized PDFs.
- Report: `fidelity-test/build/reports/fidelity/index.html`
- Diff uses neighborhood-minimum comparison (radius=3, tolerance=8) to ignore anti-aliasing
- Gradle caches test results aggressively — use `--rerun-tasks` after changing `ImageMetrics` or `FidelityReport`

## CI

GitHub Actions (`.github/workflows/ci.yml`): build + unit tests + fidelity tests on ubuntu/macos, JDK 17. Uses `xvfb-run` for headless Compose on Linux.

Compose Multiplatform compatibility matrix (`.github/workflows/compatibility.yml`): tests against the 3 most recent CMP versions (defined in `.github/compose-versions.json`). Auto-updated weekly by `.github/workflows/update-compose-versions.yml`.

## Public API

```kotlin
renderToPdf(config, density, mode, defaultFontFamily, pagination) { content } → ByteArray  // auto-paginates by default
renderToPdf(pages, config, density, mode, defaultFontFamily) { pageIndex → content } → ByteArray  // manual pages
PdfLink(href) { content }
PdfRoundedCornerShape(topStart, topEnd, bottomEnd, bottomStart)
Shape.asPdfSafe()
```

Types: `PdfPageConfig` (A4/A4WithMargins/Letter/LetterWithMargins/A3/A3WithMargins + `landscape()`), `PdfMargins` (None/Narrow/Normal + `symmetric()`), `PdfPagination` (AUTO/SINGLE_PAGE), `Density`, `RenderMode` (VECTOR/RASTER), `InterFontFamily`, `Compose2PdfException`.

## Architecture

```
Compose content → ComposeToSvg.render() → SVG string
  → SvgToPdfConverter (orchestrator)
    ├── SvgPathParser (SVG path data → PDFBox paths)
    ├── SvgColorParser + SvgColor (CSS/SVG color parsing)
    ├── SvgShapeRenderer (ellipse, rounded rect geometry)
    └── CoordinateTransform (SVG Y-down ↔ PDF Y-up)
  → PDFBox vector drawing commands → ByteArray (PDF)

Raster fallback: ImageComposeScene → bitmap → PDFBox embedded image
```

## Gotchas

- **`@InternalComposeUiApi` opt-in required** — `CanvasLayersComposeScene` is internal Compose API
- **Variable fonts excluded** — `FontResolver.isVariableFont()` skips fonts with `fvar` table
- **SVGCanvas bezier approximation** — non-uniform rounded rects become complex bezier paths; use `PdfRoundedCornerShape`
- **Bundled fonts loaded from classpath** — `FontResolver` loads Inter fonts directly from `InputStream`, no temp files
- **`Compose2PdfException` wraps rendering errors** — `IllegalArgumentException` (precondition failures) passes through unwrapped
- **Auto-pagination measures in tall scene** — `PdfRenderer` uses a 200K px max scene height for measurement; Compose `Constraints` limit is ~262K px
- **Auto-pagination fallback** — If measured height ≤ page height or ≥ max height (fillMaxHeight detected), falls back to original single-page rendering path for identical output
- **PaginatedColumn keeps children together** — Inserts padding at page boundaries so no direct child is split; oversized children (taller than a page) flow across pages

## Code Conventions

- Package: `com.chrisjenx.compose2pdf`
- Internal implementation in `com.chrisjenx.compose2pdf.internal`
- `internal` visibility by default for implementation classes
- Only `renderToPdf()`, `PdfLink()`, config types, `PdfRoundedCornerShape`, and `Shape.asPdfSafe()` are public — link collectors, annotations, and rendering internals are `internal`
- Tests use `kotlin-test`

## Publishing

Maven Central via Sonatype (`s01.oss.sonatype.org`), group ID `com.chrisjenx`. Requires `ossrhUsername`, `ossrhPassword`, and `signing.keyId` in `local.properties` or environment variables.
