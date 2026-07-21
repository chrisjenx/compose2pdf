---
title: Changelog
nav_order: 7
---

# Changelog

All notable changes to compose2pdf.

The format is based on [Keep a Changelog](https://keepachangelog.com/).

---

## Unreleased

### Fixed

- **Any font now renders with correct letter spacing — automatically.** Text laid out with fonts that previously couldn't be embedded (custom `Font(file)`/`Font(resource)` families, platform defaults like `.SF NS` on macOS or Roboto/DejaVu on Linux servers) was drawn with the non-embedded standard-14 Helvetica, whose different glyph widths made letters randomly squash or collide (e.g. `2.5% ($34.69)` rendering as `2.5%($34.69)`). The renderer now embeds the exact typefaces Compose shaped the text with: fonts loaded during composition are captured from Compose's font stack, system fonts resolve through Skia's own font manager (the same lookup the text shaper uses), and the font bytes are reconstructed from Skia's font-table API for PDFBox subsetting. No API change — existing `InterFontFamily`/bundled behavior is byte-identical.
- Safety net: if a font still can't be embedded (e.g. a bold instance of a variable-only system font), glyphs wider than the space the layout reserved are horizontally compressed so they can never collide, and `FontResolver` logs a warning naming the family.
- **Bold text now embeds the bold face.** Skia's SVG writer emits `font-weight="600"` for typefaces whose OS/2 weight is 700 (e.g. Inter-Bold), which the resolver previously classified as non-bold — bold text silently embedded the regular face. Weights >= 600 now select the bold variant, matching Compose's own font matching.

### Added

- New runnable example [`14_CustomFonts.kt`](https://github.com/chrisjenx/compose2pdf/blob/main/examples/src/main/kotlin/com/chrisjenx/compose2pdf/examples/14_CustomFonts.kt) demonstrating custom `Font(resource)` families (Montserrat), bundled Inter, and generic families — rendered output embedded in the docs.

## 1.3.0

### Added

- **Per-page headers and footers** -- new `header`/`footer` slots on the auto-pagination `renderToPdf` overloads. Slots receive `PdfPageInfo` (`pageIndex`, `pageCount`, `pageNumber`) for "Page X of Y" numbering; slot height is measured once and is stable on every page. Bands render within the page margins (~0.25in / 18pt from the page edge), leaving body content position unchanged.
  - Note: the new `header`/`footer` parameters change the `renderToPdf` JVM method signatures — consumers compiled against 1.1.x must recompile against this release.

### Fixed

- Raster auto-pagination no longer stretches the last partial page slice to the full content height.

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
