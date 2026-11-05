---
title: Changelog
nav_order: 7
---

# Changelog

All notable changes to compose2pdf.

The format is based on [Keep a Changelog](https://keepachangelog.com/).

---

## Unreleased

## 1.5.0

### Added

- **Compose Multiplatform 1.13 support** (tested on 1.13.0-beta02 / Skiko 0.156.1). The library is now built against CMP 1.13.0-beta02; the same single binary is CI-tested on CMP 1.11.1, 1.12.2 and 1.13.0-beta02 (and was also smoke-tested locally on 1.10.3 and 1.12.0-beta01).

### Fixed

- **Every render threw `Compose2PdfException` on CMP 1.13** ("No Compose UI graphics implementation is registered" / "Registered implementation is null, but required is SkikoGraphics"). CMP 1.13 stopped registering its Skiko graphics/text implementations through `ServiceLoader`; only `ImageComposeScene`/`ComposeContainer` register them now, and the vector renderer builds its scene directly. compose2pdf now registers them (idempotently, by reflection — a no-op before 1.13) before every render, in vector, raster, auto-paginated, header/footer and manual-page modes.
- **Custom-font and system-font embedding on CMP 1.13.** Skiko 0.156 turned Skia's `FontStyle` into an inline value class (changing every JVM signature that uses it), Compose's internal font-cache members were renamed, and all renders now share one process-wide font resolver. Font capture handles all of these, so `Font(file)`/`Font(resource)` and system families keep embedding the exact fonts Compose laid text out with, with no Helvetica fallback.
- **Raster mode threw `NoSuchMethodError` (`Image.encodeToData`) on CMP 1.12.** This was a binary mismatch with Skiko 0.150+ that also affected 1.4.0. Raster pages now convert bitmaps through Compose's stable `toAwtImage()` interop, which works on every supported version and skips a PNG encode/decode round-trip.

### Changed

- The published POM now depends on Compose `desktop` **1.13.0-beta02** (previously 1.11.1). Gradle conflict resolution will upgrade consumers on an older Compose to this pre-release unless they pin their Compose version (e.g. with a `strictly` constraint); compose2pdf itself runs on 1.10+ either way.

## 1.4.0

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
