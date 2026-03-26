---
title: Single Page
parent: Usage Guide
nav_order: 1
---

# Single Page PDFs

The simplest way to create a PDF -- call `renderToPdf` with a composable lambda.

{: .note }
`renderToPdf` now **auto-paginates by default**. If your content overflows a single page, it automatically flows to additional pages. To force single-page behavior (clip overflow), pass `pagination = PdfPagination.SINGLE_PAGE`.

---

## Basic usage

```kotlin
val pdfBytes = renderToPdf {
    Column(Modifier.fillMaxSize().padding(32.dp)) {
        Text("Hello, PDF!", fontSize = 28.sp)
    }
}
File("output.pdf").writeBytes(pdfBytes)
```

This renders an A4 page in vector mode with the bundled Inter font. If the content fits on one page, you get a single-page PDF.

---

## All parameters

```kotlin
val pdfBytes = renderToPdf(
    config = PdfPageConfig.A4,              // Page size and margins
    density = Density(2f),                  // Pixel resolution for layout
    mode = RenderMode.VECTOR,               // Vector or raster rendering
    defaultFontFamily = InterFontFamily,    // Font family for text
    pagination = PdfPagination.AUTO,        // Auto-paginate or single page
) {
    // Your composable content
}
```

| Parameter | Type | Default | Description |
|:----------|:-----|:--------|:------------|
| `config` | `PdfPageConfig` | `PdfPageConfig.A4` | Page dimensions and margins |
| `density` | `Density` | `Density(2f)` | Pixel density for Compose layout. Higher = better anti-aliasing, more memory |
| `mode` | `RenderMode` | `RenderMode.VECTOR` | `VECTOR` for selectable text, `RASTER` for pixel-perfect |
| `defaultFontFamily` | `FontFamily?` | `InterFontFamily` | Default font. Pass `null` for system fonts |
| `pagination` | `PdfPagination` | `PdfPagination.AUTO` | `AUTO` splits content across pages. `SINGLE_PAGE` clips to one page |
| `content` | `@Composable () -> Unit` | -- | The content to render |

**Returns:** `ByteArray` containing a valid PDF document.

---

## Examples

### With page configuration

```kotlin
val pdf = renderToPdf(config = PdfPageConfig.LetterWithMargins) {
    Column(Modifier.fillMaxSize()) {
        Text("US Letter with 1-inch margins", fontSize = 20.sp)
    }
}
```

### With system fonts

```kotlin
val pdf = renderToPdf(defaultFontFamily = null) {
    Text("Using system fonts")
}
```

### Raster mode with higher density

```kotlin
val pdf = renderToPdf(
    mode = RenderMode.RASTER,
    density = Density(3f),
) {
    Text("Pixel-perfect rendering")
}
```

---

## Saving and streaming

`renderToPdf` returns a `ByteArray`, or you can stream directly to an `OutputStream`:

```kotlin
// ByteArray -- simple, works everywhere
val pdfBytes = renderToPdf { MyContent() }
File("report.pdf").writeBytes(pdfBytes)

// OutputStream -- avoids extra copy for large PDFs
FileOutputStream("report.pdf").use { out ->
    renderToPdf(out) { MyContent() }
}

// Ktor -- stream directly to the HTTP response
call.respondOutputStream(ContentType.Application.Pdf) {
    renderToPdf(this) { MyContent() }
}
```

See [Server-side & Ktor]({{ site.baseurl }}/guides/server-side) for more integration patterns.

---

{: .note }
`renderToPdf` is **not thread-safe**. If you need to generate PDFs concurrently, serialize calls with a mutex or single-threaded dispatcher.

---

## See also

- [API Reference: renderToPdf]({{ site.baseurl }}/api/render-to-pdf) -- Full function signature and documentation
- [Page Configuration]({{ site.baseurl }}/usage/page-configuration) -- All page sizes and margin options
- [Vector vs Raster]({{ site.baseurl }}/usage/vector-vs-raster) -- Choosing a rendering mode
