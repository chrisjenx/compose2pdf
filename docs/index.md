---
title: Home
layout: home
nav_order: 1
description: "Kotlin Multiplatform library for PDF generation from Compose — vector text, embedded fonts, and auto-pagination on JVM, Android, and iOS."
---

# compose2pdf — Kotlin Multiplatform PDF Library

**Generate production-quality PDFs from Compose content on JVM/Desktop, Android, and iOS** — vector text, embedded fonts, and auto-pagination. A Kotlin Multiplatform library that turns your `@Composable` functions into PDF documents.
{: .fs-6 .fw-300 }

[Get Started]({{ site.baseurl }}/getting-started){: .btn .btn-primary .fs-5 .mb-4 .mb-md-0 .mr-2 }
[View Examples]({{ site.baseurl }}/examples/){: .btn .fs-5 .mb-4 .mb-md-0 }

---

## Compose in, PDF out — pixel-perfect

Write standard `@Composable` functions. Get production-quality PDFs.

| Compose (reference render) | PDF (vector output) |
|:---:|:---:|
| ![Compose render]({{ site.baseurl }}/assets/images/fidelity-report-compose.png){: .rounded .shadow } | ![PDF render]({{ site.baseurl }}/assets/images/fidelity-report-pdf.png){: .rounded .shadow } |

*A performance report with KPI cards, bar charts, and data tables — the PDF output is virtually identical to the Compose reference.*

---

## Three lines of code

```kotlin
val pdfBytes = renderToPdf {
    Text("Hello, PDF!")
}
File("hello.pdf").writeBytes(pdfBytes)
```

That's it. `renderToPdf` takes a `@Composable` lambda and returns a `ByteArray`.

---

## Real-world output

| Detailed Invoice | Professional Invoice |
|:---:|:---:|
| ![Invoice comparison]({{ site.baseurl }}/assets/images/fidelity-invoice-pdf.png){: .rounded .shadow } | ![Example invoice]({{ site.baseurl }}/assets/images/10-invoice.png){: .rounded .shadow } |
| Fidelity test fixture ([PDF]({{ site.baseurl }}/assets/pdfs/10-invoice.pdf)) | [Example walkthrough]({{ site.baseurl }}/examples/invoice) |

---

## Why compose2pdf?

Use the same Compose layout primitives you already know -- `Column`, `Row`, `Box`, `Text`, `Canvas` -- and get a production-quality PDF.

### Multiplatform

Works on JVM/Desktop, Android, and iOS. Each platform uses a native PDF pipeline for best performance and output quality.

### Vector PDFs

Text is selectable, scales to any zoom level, and produces small files. Powered by platform-native PDF engines (PDFBox on JVM, PdfDocument on Android, Core Graphics on iOS).

### Raster Fallback (JVM)

When you need pixel-perfect rendering -- complex gradients, visual effects, or exact bitmap output -- switch to raster mode with one parameter.

### Font Embedding (JVM)

Ships with bundled Inter fonts (Regular, Bold, Italic, BoldItalic). Fonts are automatically subsetted and embedded so PDFs look the same everywhere.

### Compose-Native API

No new DSL to learn. Write `@Composable` functions, pass them to `renderToPdf()`, get a `ByteArray`. Works with all standard Compose layout, text, shape, and drawing APIs.

---

## Features at a Glance

| Feature | JVM | Android | iOS |
|:--------|:----|:--------|:----|
| **Vector output** | Yes (VECTOR/RASTER modes) | Yes (always vector) | Yes (always vector) |
| **Auto-pagination** | Yes | Yes | Yes |
| **Multi-page (manual)** | Yes | -- | -- |
| **Font embedding** | Bundled Inter + system fonts | System fonts | System fonts |
| **Link annotations** | Yes (`PdfLink`) | -- | -- |
| **Streaming output** | `OutputStream` | `OutputStream` | -- |
| **Page presets** | A4, Letter, A3 + margins + landscape | Same | Same |
| **Shapes & images** | Full support | Full support | Full support |

---

## How it works

Each platform uses a native PDF pipeline:

- **JVM**: Compose content is rendered through Skia SVGCanvas, producing SVG that is converted to PDF vector commands via Apache PDFBox with embedded fonts and link annotations.
- **Android**: Compose content is rendered off-screen via a headless virtual display, drawn directly onto `android.graphics.pdf.PdfDocument`'s Skia-backed Canvas.
- **iOS**: Compose content is rendered through Skia SVGCanvas, with SVG parsed via NSXMLParser and converted to PDF via Core Graphics (`CGPDFContext`).

{: .note }
**Want native PDF output from Skia?** [Skiko PR #775](https://github.com/JetBrains/skiko/pull/775) proposes adding a direct PDF backend to Skia/Skiko. This would eliminate the SVG intermediary — faster rendering, smaller files, and full gradient/effect support in vector mode. If this matters to you, upvote the PR!

---

## Requirements

- **JVM/Desktop**: JDK 17+, Kotlin 2.x, Compose Multiplatform 1.9+
- **Android**: minSdk 24, Compose Multiplatform 1.9+
- **iOS**: Compose Multiplatform 1.9+
