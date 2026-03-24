---
title: Changelog
nav_order: 7
---

# Changelog

All notable changes to compose2pdf.

The format is based on [Keep a Changelog](https://keepachangelog.com/).

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
