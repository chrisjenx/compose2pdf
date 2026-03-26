---
title: Architecture
parent: Guides
nav_order: 3
---

# Architecture

How compose2pdf converts Compose content to PDF under the hood.

---

## Pipeline overview

```
┌─────────────────────────────────────────────────┐
│                  renderToPdf()                   │
│                                                  │
│  ┌─────────────┐    ┌──────────────────────┐    │
│  │  VECTOR mode │    │    RASTER mode        │    │
│  │              │    │                       │    │
│  │  Compose     │    │  Compose              │    │
│  │    ↓         │    │    ↓                  │    │
│  │  Skia        │    │  ImageComposeScene    │    │
│  │  Picture     │    │    ↓                  │    │
│  │  Recorder    │    │  BufferedImage        │    │
│  │    ↓         │    │    ↓                  │    │
│  │  SVGCanvas   │    │  PDFBox               │    │
│  │    ↓         │    │  LosslessFactory      │    │
│  │  SVG string  │    │    ↓                  │    │
│  │    ↓         │    │  Embedded image       │    │
│  │  SvgToPdf    │    │  in PDF               │    │
│  │  Converter   │    │                       │    │
│  │    ↓         │    │                       │    │
│  │  PDFBox      │    │                       │    │
│  │  vector cmds │    │                       │    │
│  └──────┬───────┘    └──────────┬────────────┘    │
│         │                       │                 │
│         └───────────┬───────────┘                 │
│                     ↓                             │
│              PDDocument → ByteArray               │
│              + Link annotations                   │
└─────────────────────────────────────────────────┘
```

---

## Vector mode in detail

### Step 1: Compose to SVG

`ComposeToSvg.render()` creates a `CanvasLayersComposeScene` (internal Compose API), renders the composable content, and records all draw commands via Skia's `PictureRecorder`. The recorded `Picture` is then replayed onto Skia's `SVGCanvas`, which produces an SVG string.

Key detail: text is emitted as positioned `<text>` elements (not converted to paths), preserving selectability.

### Step 2: SVG to PDF

`SvgToPdfConverter` parses the SVG XML and dispatches each element to corresponding PDFBox drawing commands:

| SVG element | PDF operation |
|:------------|:-------------|
| `<rect>` | Rectangle path (with optional rounded corners) |
| `<circle>`, `<ellipse>` | Approximated with 4 cubic Bezier curves |
| `<line>`, `<polyline>`, `<polygon>` | Line segments |
| `<path>` | Full SVG path parsing (M, L, C, S, Q, T, A, Z commands) |
| `<text>` | Font resolution + positioned glyphs |
| `<image>` | Base64 decode + PDFBox image embedding |
| `<g>` | Transform + opacity state management |
| `<use>` | Reference resolution from `<defs>` |
| `<clipPath>` | PDF clipping regions |

### Step 3: Coordinate transform

SVG uses a Y-down coordinate system (origin at top-left). PDF uses Y-up (origin at bottom-left). The converter applies a Y-flip matrix to the entire page, then counter-flips text and images individually so they render right-side up.

---

## Raster mode in detail

`ImageComposeScene` renders the composable to a Skia bitmap at the configured density. The bitmap is converted to a `BufferedImage` and embedded as a lossless PDF image via PDFBox's `LosslessFactory`.

This mode produces pixel-perfect output but text is not selectable.

---

## Font resolution

When the converter encounters a `<text>` element with `font-family`, `font-weight`, and `font-style` attributes, `FontResolver` resolves the font:

```
1. Bundled fonts (Inter Regular/Bold/Italic/BoldItalic)
       ↓ not found
2. System fonts (platform-specific directories)
   - Exact filename match first
   - Fuzzy search up to 3 directory levels
       ↓ not found
3. PDF Standard 14 fonts (Helvetica, Times, Courier)
```

Resolved fonts are cached per document (for embedding) and globally (for file path lookups) using `ConcurrentHashMap`.

Variable fonts (detected by scanning for the `fvar` OpenType table) are automatically excluded.

---

## Link annotations

1. During rendering, `PdfRenderer` provides a `PdfLinkCollector` via `CompositionLocal`
2. `PdfLink` composables use `onGloballyPositioned` to measure their bounds and record `PdfLinkAnnotation` objects
3. After page rendering, annotations are converted from SVG coordinates (Y-down) to PDF coordinates (Y-up) and added as `PDAnnotationLink` objects with invisible borders

---

## Font subsetting

PDFBox's `PDType0Font.load()` automatically subsets embedded fonts -- only the glyphs actually used on each page are included in the PDF, keeping file sizes small.

---

## Key implementation files

| File | Responsibility |
|:-----|:--------------|
| `PdfRenderer.kt` | Orchestrates vector/raster pipelines |
| `ComposeToSvg.kt` | Compose content -> SVG string |
| `SvgToPdfConverter.kt` | SVG -> PDFBox vector commands |
| `SvgPathParser.kt` | Full SVG path data parser |
| `SvgShapeRenderer.kt` | Ellipse and rounded rect geometry |
| `SvgColorParser.kt` | CSS/SVG color parsing |
| `CoordinateTransform.kt` | SVG Y-down <-> PDF Y-up conversion |
| `FontResolver.kt` | Font family/weight/style -> PDFBox font |

---

## Future: native Skia PDF backend

The current vector pipeline goes through an SVG intermediary: **Compose → Skia Picture → SVGCanvas → SVG string → XML parse → PDFBox commands**. This works well but introduces inherent limitations — gradients and some visual effects don't survive the SVG round-trip.

[**Skiko PR #775**](https://github.com/JetBrains/skiko/pull/775) proposes adding a thin wrapper around Skia's native PDF backend (`SkDocument`). If merged, this would enable a much simpler pipeline:

```
Current:  Compose → Skia → SVG → parse → PDFBox → PDF
Future:   Compose → Skia → PDF (direct)
```

Benefits of the native PDF backend:
- **Full visual fidelity** — gradients, shadows, blur, and all Skia effects preserved
- **Faster rendering** — no SVG serialization/parsing overhead
- **Smaller file sizes** — Skia's PDF backend is optimized for compactness
- **Simpler codebase** — eliminates SvgToPdfConverter, SvgPathParser, SvgColorParser, CoordinateTransform

If native PDF rendering in Compose Desktop matters to you, please upvote [JetBrains/skiko#775](https://github.com/JetBrains/skiko/pull/775).

---

## See also

- [Vector vs Raster]({{ site.baseurl }}/usage/vector-vs-raster) -- User-facing comparison
- [Supported Features]({{ site.baseurl }}/guides/supported-features) -- What works in each mode
