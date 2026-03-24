# Compose 2 PDF

[![CI](https://github.com/chrisjenx/compose2pdf/actions/workflows/ci.yml/badge.svg)](https://github.com/chrisjenx/compose2pdf/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](https://www.apache.org/licenses/LICENSE-2.0)

Render [Compose Desktop](https://www.jetbrains.com/compose-multiplatform/) content directly to PDF.

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

## Installation

```kotlin
// build.gradle.kts
dependencies {
    implementation("com.chrisjenx:compose2pdf:0.1.0")
}
```

Requires **JDK 17+** and **Compose Desktop** (Compose Multiplatform 1.9+).

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

## Documentation

**[Full documentation](https://chrisjenx.github.io/compose2pdf/)** — getting started, usage guides, API reference, examples, and more.

- [Getting Started](https://chrisjenx.github.io/compose2pdf/getting-started/)
- [Usage Guide](https://chrisjenx.github.io/compose2pdf/usage/)
- [API Reference](https://chrisjenx.github.io/compose2pdf/api/)
- [Examples](https://chrisjenx.github.io/compose2pdf/examples/)
- [Troubleshooting](https://chrisjenx.github.io/compose2pdf/guides/troubleshooting/)

## License

Apache-2.0
