---
title: Changelog
nav_order: 7
---

# Changelog

All notable changes to compose2pdf.

The format is based on [Keep a Changelog](https://keepachangelog.com/).

---

## 2.0.0

### Added

- **Kotlin Multiplatform** -- compose2pdf now targets JVM, Android, and iOS
- **Android support** -- renders PDFs via `android.graphics.pdf.PdfDocument` (zero external dependencies). Suspend API with `Context` parameter. Always produces vector output
- **iOS support** -- renders PDFs via Core Graphics (`CGPDFContext`). Supports iosArm64, iosX64, and iosSimulatorArm64 targets
- **Auto-pagination on all platforms** -- content automatically flows across pages on JVM, Android, and iOS
- **`test-fixtures` module** -- shared multiplatform test utilities for JVM, Android, and iOS

### Changed

- **Artifact structure** -- the single `compose2pdf` JVM artifact is now a KMP metadata module. Platform-specific artifacts are `compose2pdf-jvm`, `compose2pdf-android`, and `compose2pdf-iosarm64`/`iosx64`/`iossimulatorarm64`. Existing consumers using `implementation("com.chrisjenx:compose2pdf:...")` continue to work via Gradle Module Metadata (Gradle 6.0+)
- **CI** -- added Android SDK setup, iOS simulator tests on macOS, and updated publish workflows to run on macOS for full KMP target support
- **Security** -- added XXE/DTD prevention to DocumentBuilderFactory in SVG parser (defense-in-depth)

### Platform limitations

| Feature | JVM | Android | iOS |
|:--------|:---:|:-------:|:---:|
| `RenderMode` (VECTOR/RASTER) | Yes | -- (always vector) | -- (always vector) |
| `InterFontFamily` | Yes | -- | -- |
| `PdfLink` annotations | Yes | -- | -- |
| `OutputStream` streaming | Yes | Yes | -- |
| Multi-page (manual) | Yes | -- | -- |
| Synchronous API | Yes | -- (`suspend` only) | Yes |

---

## 1.0.0

Initial public release.

### Added

- **Compose-to-PDF rendering** -- render Compose Desktop content to production-quality vector PDFs with embedded fonts
- **Auto-pagination** -- content automatically splits across pages with smart page breaking that keeps children together
- **Manual multi-page** -- explicit page control via `renderToPdf(pages = N) { pageIndex -> ... }`
- **OutputStream streaming** -- stream PDFs directly to any `OutputStream` for server-side use (Ktor, Spring, Servlet)
- **Clickable links** -- `PdfLink(href)` composable adds PDF link annotations
- **Raster fallback** -- `RenderMode.RASTER` for pixel-perfect output when vector fidelity isn't needed
- **Bundled Inter font** -- `InterFontFamily` included for consistent cross-platform rendering
- **PDF-safe shapes** -- `PdfRoundedCornerShape` and `Shape.asPdfSafe()` for accurate non-uniform rounded corners
- **Page presets** -- A4, Letter, A3 with margin variants and `landscape()` support
- **Snapshot publishing** -- SNAPSHOT builds auto-publish to Maven Central on push to main

## 0.2.0

### Added

- **Auto-pagination** -- `renderToPdf` now automatically splits content across pages when it overflows (default behavior via `PdfPagination.AUTO`)
- **OutputStream overloads** -- `renderToPdf(outputStream)` variants stream PDFs directly to any `OutputStream`, avoiding extra `ByteArray` copies. Ideal for Ktor and server-side usage
- `PdfPagination` enum -- `AUTO` (default) or `SINGLE_PAGE`
- Smart page breaking -- direct children are treated as "keep-together" units
- Warning logs when auto-pagination truncates at 100-page limit
- Warning logs for malformed SVG elements (missing attributes on rect, circle, ellipse, image)
- Improved error messages -- `Compose2PdfException` used consistently for rendering failures

### Changed

- `renderToPdf` ByteArray variants now delegate to OutputStream variants internally
- `PdfRenderer` internals return `PDDocument` instead of `ByteArray` for better composability
- Cached `DocumentBuilderFactory` in SVG parser (performance improvement for multi-page documents)
- Cached inline style attribute parsing per element (performance improvement)

---

## 0.1.0

Initial release.

### Added

- `renderToPdf()` for single-page and multi-page PDF generation
- **Vector rendering** via Skia SVGCanvas -- selectable text, small files, embedded fonts
- **Raster rendering** via ImageComposeScene -- pixel-perfect bitmap output
- `PdfPageConfig` with presets: A4, A4WithMargins, Letter, LetterWithMargins, A3, A3WithMargins
- `PdfPageConfig.landscape()` for landscape orientation
- `PdfMargins` with presets: None, Narrow, Normal, and `symmetric()` factory
- `PdfLink` composable for clickable URL annotations
- `PdfRoundedCornerShape` for correct non-uniform corner rendering in vector mode
- `Shape.asPdfSafe()` extension
- `InterFontFamily` -- bundled Inter fonts (Regular, Bold, Italic, BoldItalic)
- Automatic font subsetting via PDFBox
- System font resolution (macOS, Linux, Windows)
- Variable font detection and exclusion
- `Compose2PdfException` for error handling
- Fidelity test suite with 30+ visual regression fixtures
- 10 runnable examples (hello world through professional invoice)
