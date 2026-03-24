---
title: Home
layout: home
nav_order: 1
---

# compose2pdf

**Render Compose Desktop content directly to PDF** -- vector text, embedded fonts, and page-perfect layout.
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

### Vector PDFs

Text is selectable, scales to any zoom level, and produces small files. Powered by Skia's SVGCanvas and Apache PDFBox.

### Raster Fallback

When you need pixel-perfect rendering -- complex gradients, visual effects, or exact bitmap output -- switch to raster mode with one parameter.

### Font Embedding

Ships with bundled Inter fonts (Regular, Bold, Italic, BoldItalic). Fonts are automatically subsetted and embedded so PDFs look the same everywhere.

### Compose-Native API

No new DSL to learn. Write `@Composable` functions, pass them to `renderToPdf()`, get a `ByteArray`. Works with all standard Compose layout, text, shape, and drawing APIs.

---

## Features at a Glance

| Feature | Description |
|:--------|:------------|
| **Vector output** | Selectable text, crisp at any zoom, small file size |
| **Raster fallback** | Pixel-perfect rendering as an embedded image |
| **Font embedding** | Bundled Inter fonts or system font resolution with automatic subsetting |
| **Link annotations** | Clickable URLs in the PDF via `PdfLink` |
| **Multi-page** | Render multiple pages in a single PDF |
| **Page presets** | A4, Letter, A3 with configurable margins and landscape support |
| **Shapes** | Backgrounds, borders, clips, rounded corners, Canvas drawing |
| **Images** | Embed bitmap images with clipping and layout |

---

## Requirements

- **JDK 17** or later
- **Kotlin** 2.x
- **Compose Multiplatform** (Desktop) 1.9+
