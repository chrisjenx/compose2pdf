# Compose 2 PDF

[![CI](https://github.com/chrisjenx/compose2pdf/actions/workflows/ci.yml/badge.svg)](https://github.com/chrisjenx/compose2pdf/actions/workflows/ci.yml)
[![Maven Central](https://img.shields.io/maven-central/v/com.chrisjenx/compose2pdf)](https://central.sonatype.com/artifact/com.chrisjenx/compose2pdf)
[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](https://www.apache.org/licenses/LICENSE-2.0)

A **Kotlin JVM library** for rendering [Compose Desktop](https://www.jetbrains.com/compose-multiplatform/) content directly to PDF. Generate production-quality PDF documents with vector text, embedded fonts, auto-pagination, and server-side streaming support.

```kotlin
val pdfBytes = renderToPdf {
    Text("Hello, PDF!")
}
File("hello.pdf").writeBytes(pdfBytes)
```

### Compose in, PDF out

| Compose (reference render) | PDF (vector output) |
|:---:|:---:|
| <img src="docs/assets/images/fidelity-invoice-compose.png" width="350" alt="Compose render"> | <img src="docs/assets/images/fidelity-invoice-pdf.png" width="350" alt="PDF output"> |

*The PDF output is virtually identical to the Compose reference — text is selectable, fonts are embedded, and the file is only 17 KB.*

## Features

- **Vector PDF output** — text is selectable, scales to any zoom level
- **Raster fallback** — pixel-perfect rendering as an embedded image
- **Font embedding** — bundled Inter fonts or system font resolution with automatic subsetting
- **Link annotations** — clickable URLs in the PDF via `PdfLink`
- **Auto-pagination** — content automatically flows across pages; elements are kept together at page boundaries
- **Multi-page** — render multiple pages in a single PDF (manual or automatic)
- **Page presets** — A4, Letter, A3 with configurable margins and landscape support
- **Streaming output** — write PDFs directly to an `OutputStream` for Ktor, servlets, or any JVM server

## Installation

```kotlin
// build.gradle.kts
dependencies {
    implementation("com.chrisjenx:compose2pdf:0.1.0")
}
```

Requires **JDK 17+** and **Compose Desktop** (Compose Multiplatform 1.9+).

## Compatibility

[![Compose Compatibility](https://github.com/chrisjenx/compose2pdf/actions/workflows/compatibility.yml/badge.svg)](https://github.com/chrisjenx/compose2pdf/actions/workflows/compatibility.yml)

Tested weekly against the 3 most recent Compose Multiplatform releases:

| Compose Multiplatform | Kotlin | Status |
|:----------------------|:-------|:-------|
| 1.11.0-alpha04 | 2.3.20 | CI tested |
| **1.10.3** | 2.3.20 | CI tested (current) |
| 1.9.3 | 2.3.20 | CI tested |

**Platform support:**

| | JDK 17+ | JDK 21+ |
|:---|:-------:|:-------:|
| **macOS** (arm64, x64) | Supported | Supported |
| **Linux** (x64) | Supported | Supported |
| **Windows** (x64) | Supported | Supported |

> Compose Desktop is JVM-only. Android and iOS are not supported.

## Quick start

### Single page

```kotlin
val pdf = renderToPdf(
    config = PdfPageConfig.LetterWithMargins,
    mode = RenderMode.VECTOR,
) {
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Text("Invoice #1234", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text("Amount: $1,250.00")
    }
}
```

### Auto-pagination (default)

Content automatically flows across pages. Direct children are kept together — if a child would straddle a page boundary, it's pushed to the next page.

```kotlin
val pdf = renderToPdf(config = PdfPageConfig.A4WithMargins) {
    ReportHeader()
    DataTable(items)       // kept together on one page
    SummarySection()       // pushed to next page if needed
}
```

### Manual multi-page

For full control over what goes on each page:

```kotlin
val pdf = renderToPdf(pages = 3) { pageIndex ->
    Column(Modifier.fillMaxSize()) {
        Text("Page ${pageIndex + 1}")
    }
}
```

### Links

```kotlin
PdfLink(href = "https://example.com") {
    Text("Click me", color = Color.Blue, textDecoration = TextDecoration.Underline)
}
```

## How it works

compose2pdf renders Compose content through a **Skia SVGCanvas → Apache PDFBox** pipeline:

1. Your `@Composable` content is rendered by Compose Desktop's layout engine
2. Skia's SVGCanvas captures the draw calls as SVG
3. compose2pdf converts the SVG to PDF vector commands via PDFBox
4. Fonts are resolved, subsetted, and embedded; link annotations are mapped to PDF coordinates

> **Want native PDF output from Skia?** The [Skiko PR #775](https://github.com/JetBrains/skiko/pull/775) proposes adding a direct PDF backend to Skia/Skiko, which would eliminate the SVG intermediary entirely — producing smaller files, faster rendering, and full gradient/effect support in vector mode. If this matters to you, upvote the PR!

## Documentation

**[Full documentation](https://chrisjenx.github.io/compose2pdf/)** — getting started, usage guides, API reference, examples, and more.

- [Getting Started](https://chrisjenx.github.io/compose2pdf/getting-started/)
- [Usage Guide](https://chrisjenx.github.io/compose2pdf/usage/)
- [API Reference](https://chrisjenx.github.io/compose2pdf/api/)
- [Examples](https://chrisjenx.github.io/compose2pdf/examples/)
- [Compatibility](https://chrisjenx.github.io/compose2pdf/compatibility/)
- [Troubleshooting](https://chrisjenx.github.io/compose2pdf/guides/troubleshooting/)

## License

Apache-2.0
