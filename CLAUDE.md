# compose2pdf

## Project Overview

**compose2pdf** is a Kotlin Multiplatform library that renders Compose content to PDF on JVM/Desktop, Android, and iOS.

## Module Map

```
├── compose2pdf/          # Library: multiplatform (commonMain, jvmMain, androidMain, iosMain)
├── test-fixtures/        # Multiplatform test utilities shared across JVM, Android, iOS
├── examples/             # Runnable JVM examples (not published)
├── fidelity-test/        # Visual regression tests, JVM only (not published)
└── docs/                 # Jekyll docs site (GitHub Pages, just-the-docs theme)
```

## Tech Stack

- **Kotlin** 2.3.20 — targets: JVM, Android (minSdk 24), iOS (arm64, x64, simulatorArm64)
- **Compose Multiplatform** 1.10.3 (Desktop, Android, iOS)
- **Android Gradle Plugin** 8.9.3
- **Apache PDFBox** 3.0.7 — JVM only (SVG→PDF conversion, image embedding, font subsetting)
- **android.graphics.pdf.PdfDocument** — Android native PDF (zero external dependencies)
- **Core Graphics (CGPDFContext)** — iOS native PDF rendering
- **Gradle** 8.14 — versions centralized in `gradle/libs.versions.toml`

## Build Commands

```bash
# JVM
./gradlew :compose2pdf:build                 # Build all KMP targets
./gradlew :compose2pdf:test                  # Run JVM unit tests
./gradlew :compose2pdf:compileKotlin         # Quick compile check (no tests)
./gradlew :fidelity-test:test                # Run fidelity tests (vector + raster PDF)
./gradlew :fidelity-test:test --rerun-tasks  # Force re-run (bypass Gradle cache)
./gradlew :examples:run                      # Run JVM examples, output to examples/build/output/

# Android
./gradlew :compose2pdf:pixel2api30atdDebugAndroidTest  # Run Android instrumented tests (managed device)

# iOS
./gradlew :compose2pdf:iosSimulatorArm64Test  # Run iOS simulator tests

# Publishing
./gradlew :compose2pdf:publishToMavenLocal   # Publish all targets to ~/.m2

# Test fixtures
./gradlew :test-fixtures:build               # Build shared test utilities

# Docs
open fidelity-test/build/reports/fidelity/index.html  # View fidelity report (macOS)
cd docs && bundle exec jekyll serve                    # Preview docs site locally (http://localhost:4000)
```

## Fidelity Tests

Visual regression suite comparing Compose reference renders against rasterized PDFs.
- Report: `fidelity-test/build/reports/fidelity/index.html`
- Diff uses neighborhood-minimum comparison (radius=3, tolerance=8) to ignore anti-aliasing
- Gradle caches test results aggressively — use `--rerun-tasks` after changing `ImageMetrics` or `FidelityReport`

## CI

GitHub Actions (`.github/workflows/ci.yml`): build + unit tests + fidelity tests + iOS simulator tests on ubuntu/macos, JDK 17. Uses `xvfb-run` for headless Compose on Linux. Android SDK setup via `android-actions/setup-android@v3`.

Compose Multiplatform compatibility matrix (`.github/workflows/compatibility.yml`): tests against the 3 most recent CMP versions (defined in `.github/compose-versions.json`). Auto-updated weekly by `.github/workflows/update-compose-versions.yml`.

## Public API

### JVM (full-featured)

```kotlin
renderToPdf(config, density, mode, defaultFontFamily, pagination) { content } → ByteArray
renderToPdf(outputStream, config, density, mode, defaultFontFamily, pagination) { content }
renderToPdf(pages, config, density, mode, defaultFontFamily) { pageIndex → content } → ByteArray
renderToPdf(outputStream, pages, config, density, mode, defaultFontFamily) { pageIndex → content }
```

### Android (suspend, requires Context)

```kotlin
suspend renderToPdf(context, config, density, defaultFontFamily, pagination) { content } → ByteArray
suspend renderToPdf(context, outputStream, config, density, defaultFontFamily, pagination) { content }
```

### iOS (synchronous, ByteArray only)

```kotlin
renderToPdf(config, density, defaultFontFamily, pagination) { content } → ByteArray
```

### Common (all platforms)

```kotlin
PdfLink(href) { content }
PdfRoundedCornerShape(topStart, topEnd, bottomEnd, bottomStart)
Shape.asPdfSafe()
```

### Platform availability

| Feature | JVM | Android | iOS |
|:--------|:---:|:-------:|:---:|
| `RenderMode` (VECTOR/RASTER) | Yes | No (always vector) | No (always vector) |
| `InterFontFamily` (bundled Inter) | Yes | No (system fonts) | No (system fonts) |
| `OutputStream` streaming | Yes | Yes | No |
| Multi-page manual API | Yes | No | No |
| `PdfLink` annotations | Yes | No | No |
| Auto-pagination | Yes | Yes | Yes |

Types: `PdfPageConfig`, `PdfMargins`, `PdfPagination`, `Density`, `RenderMode` (JVM), `InterFontFamily` (JVM), `Compose2PdfException`.

## Key Files

### commonMain
- `compose2pdf/src/commonMain/.../PdfPageConfig.kt` — Page size and margin configuration
- `compose2pdf/src/commonMain/.../PdfMargins.kt` — Margin presets
- `compose2pdf/src/commonMain/.../PdfLink.kt` — Link annotation composable + collector
- `compose2pdf/src/commonMain/.../internal/PaginatedColumn.kt` — Smart page-break layout
- `compose2pdf/src/commonMain/.../internal/PageLayout.kt` — Page layout utilities

### jvmMain
- `compose2pdf/src/jvmMain/.../Compose2Pdf.kt` — JVM public API entry points
- `compose2pdf/src/jvmMain/.../PdfFonts.kt` — InterFontFamily (bundled Inter fonts)
- `compose2pdf/src/jvmMain/.../internal/PdfRenderer.kt` — Rendering orchestrator (vector, raster, auto-pagination)
- `compose2pdf/src/jvmMain/.../internal/ComposeToSvg.kt` — Compose → SVG + measurement
- `compose2pdf/src/jvmMain/.../internal/SvgToPdfConverter.kt` — SVG → PDF pages via PDFBox
- `compose2pdf/src/jvmMain/.../internal/FontResolver.kt` — Font resolution + subsetting

### androidMain
- `compose2pdf/src/androidMain/.../Compose2Pdf.android.kt` — Android public API (suspend, Context)
- `compose2pdf/src/androidMain/.../internal/AndroidPdfRenderer.kt` — Rendering via android.graphics.pdf.PdfDocument
- `compose2pdf/src/androidMain/.../internal/OffScreenComposeRenderer.kt` — Headless Compose rendering via virtual display

### iosMain
- `compose2pdf/src/iosMain/.../Compose2Pdf.ios.kt` — iOS public API
- `compose2pdf/src/iosMain/.../internal/IosPdfRenderer.kt` — Rendering orchestrator
- `compose2pdf/src/iosMain/.../internal/ComposeToSvg.kt` — Compose → SVG via Skia (iOS)
- `compose2pdf/src/iosMain/.../internal/CoreGraphicsPdfConverter.kt` — SVG → PDF via CGPDFContext
- `compose2pdf/src/iosMain/.../internal/SvgDocument.kt` — SVG parsing via NSXMLParser
- `compose2pdf/src/iosMain/.../internal/CoreGraphicsPathParser.kt` — SVG path → Core Graphics paths

### Tests
- `fidelity-test/src/test/.../FidelityFixtures.kt` — All fidelity test composables (JVM)
- `compose2pdf/src/jvmTest/` — JVM unit tests
- `compose2pdf/src/androidInstrumentedTest/` — Android instrumented tests
- `compose2pdf/src/iosSimulatorArm64Test/` — iOS simulator tests

## Architecture

### JVM Pipeline

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

### Android Pipeline

```
Compose content → OffScreenComposeRenderer (headless virtual display)
  → View.draw() → android.graphics.pdf.PdfDocument Canvas (Skia-backed)
  → PDF bytes

Always vector output. PdfDocument's Canvas is backed by Skia, producing
resolution-independent paths and selectable text.
```

### iOS Pipeline

```
Compose content → CanvasLayersComposeScene → Skia SVGCanvas → SVG string
  → NSXMLParser → SvgElement tree
  → CoreGraphicsPdfConverter (CGPDFContext)
    ├── CoreGraphicsPathParser (SVG path → CGPath)
    └── CTFontDrawGlyphs (per-glyph text rendering)
  → PDF bytes (NSMutableData)
```

## Gotchas

### Docs Site
- **URL structure** — standalone pages (`getting-started.md`) produce `/page.html` URLs; directory index pages (`usage/index.md`) produce `/directory/` URLs. Don't use trailing slashes for standalone pages.

### Rendering (JVM)
- **`@InternalComposeUiApi` opt-in required** — `CanvasLayersComposeScene` is internal Compose API
- **Variable fonts excluded** — `FontResolver.isVariableFont()` skips fonts with `fvar` table
- **SVGCanvas bezier approximation** — non-uniform rounded rects become complex bezier paths; use `PdfRoundedCornerShape`
- **Bundled fonts loaded from classpath** — `FontResolver` loads Inter fonts directly from `InputStream`, no temp files
- **`Compose2PdfException` wraps rendering errors** — `IllegalArgumentException` (precondition failures) passes through unwrapped
- **OutputStream overloads don't close the stream** — caller owns the stream lifecycle; `PDDocument.use { it.save(outputStream) }` is called internally

### Rendering (Android)
- **`renderToPdf` is suspend-only** — requires main thread for off-screen Compose rendering via virtual display
- **Requires `Context` parameter** — any Context works, not just Activity
- **No `PdfLink` support** — `android.graphics.pdf.PdfDocument` has no annotation API; `PdfLink` is a no-op
- **No `RenderMode` parameter** — always produces vector output via PdfDocument's Skia-backed Canvas
- **No `InterFontFamily`** — uses Android's system font stack

### Rendering (iOS)
- **Uses `NSXMLParser` for SVG parsing** — not javax.xml (that's JVM-only)
- **`CTFontDrawGlyphs` for per-glyph text rendering** — fixes spacing issues with Core Text
- **ByteArray return only** — no `OutputStream` streaming overload
- **No multi-page manual API** — only auto-pagination
- **No `PdfLink` annotation support** — Core Graphics PDF context doesn't write link annotations

### Auto-pagination
- **Measures in tall scene** — `PdfRenderer` uses a 200K px max scene height for measurement; Compose `Constraints` limit is ~262K px
- **Falls back for single-page content** — If measured height ≤ page height or ≥ max height (fillMaxHeight detected), falls back to original single-page rendering path for identical output
- **PaginatedColumn keeps children together** — Inserts padding at page boundaries so no direct child is split; oversized children (taller than a page) flow across pages

### Testing
- **Fidelity tests assume identical render path** — Changing `renderToPdf` default behavior (e.g., wrapping content in extra layout layers or using a taller scene) can break fidelity comparisons; single-page content must fall back to the original render path
- **Compose `Placeable` is not fakeable** — `width`/`height` are final; test layout logic with raw `List<Int>` heights instead of mock `Placeable` objects
- **Android tests use `androidInstrumentedTest`** — requires AndroidX test runner and a managed device or emulator
- **iOS tests use `iosSimulatorArm64Test`** — runs on macOS only (requires Xcode simulator)

## Code Conventions

- Package: `com.chrisjenx.compose2pdf`
- Internal implementation in `com.chrisjenx.compose2pdf.internal`
- `internal` visibility by default for implementation classes
- Platform-specific implementations use file-level platform suffixes (e.g., `Compose2Pdf.android.kt`, `Compose2Pdf.ios.kt`)
- Public API (JVM): `renderToPdf()`, `PdfLink()`, `PdfPageConfig`, `PdfMargins`, `PdfPagination`, `RenderMode`, `Density`, `InterFontFamily`, `PdfRoundedCornerShape`, `Shape.asPdfSafe()`, `Compose2PdfException` — everything else is `internal`
- Public API (Android/iOS): `renderToPdf()`, `PdfLink()`, `PdfPageConfig`, `PdfMargins`, `PdfPagination`, `Density`, `PdfRoundedCornerShape`, `Shape.asPdfSafe()`, `Compose2PdfException`
- Tests use `kotlin-test`

## Publishing

Maven Central via Vanniktech Maven Publish plugin (`publishToMavenCentral()` + `signAllPublications()`), group ID `com.chrisjenx`.

KMP produces these artifacts:
- `com.chrisjenx:compose2pdf` — root metadata module (Gradle Module Metadata)
- `com.chrisjenx:compose2pdf-jvm` — JVM JAR
- `com.chrisjenx:compose2pdf-android` — Android AAR (release variant via `publishLibraryVariants("release")`)
- `com.chrisjenx:compose2pdf-iosarm64` / `iosx64` / `iossimulatorarm64` — iOS klibs

Existing consumers using `implementation("com.chrisjenx:compose2pdf:X.X.X")` continue to work via Gradle Module Metadata (Gradle 6.0+ automatically resolves to the correct platform artifact).

Requires `mavenCentralUsername`, `mavenCentralPassword`, and `signingInMemoryKey*` in environment variables or GitHub secrets. Release and snapshot workflows run on `macos-latest` to build all targets (iOS requires macOS).
