---
title: renderToPdf
parent: API Reference
nav_order: 1
---

# renderToPdf

The primary entry point for PDF generation. Four overloads: auto-paginating and manual multi-page, each with `ByteArray` and `OutputStream` variants.

---

## Auto-paginating (default)

```kotlin
fun renderToPdf(
    config: PdfPageConfig = PdfPageConfig.A4,
    density: Density = Density(2f),
    mode: RenderMode = RenderMode.VECTOR,
    defaultFontFamily: FontFamily? = InterFontFamily,
    pagination: PdfPagination = PdfPagination.AUTO,
    content: @Composable () -> Unit,
): ByteArray
```

Renders Compose content to a PDF, automatically splitting across pages when content overflows.

With `PdfPagination.AUTO` (the default), direct children of `content` are treated as "keep-together" units — if a child would straddle a page boundary, it is pushed to the next page. A single child taller than a page flows continuously across pages.

### Parameters

| Parameter | Type | Default | Description |
|:----------|:-----|:--------|:------------|
| `config` | [`PdfPageConfig`]({{ site.baseurl }}/api/pdf-page-config) | `PdfPageConfig.A4` | Page size and margins |
| `density` | `Density` | `Density(2f)` | Pixel resolution for Compose layout. Each PDF point maps to `density` x `density` pixels. Higher values improve anti-aliasing (especially for raster mode) at the cost of memory |
| `mode` | [`RenderMode`]({{ site.baseurl }}/api/render-mode) | `RenderMode.VECTOR` | Vector (SVG-based) or raster rendering |
| `defaultFontFamily` | `FontFamily?` | [`InterFontFamily`]({{ site.baseurl }}/api/fonts) | Font family for the default text style. When non-null, content is wrapped so both Compose and PDFBox use the same font. Pass `null` for system fonts |
| `pagination` | `PdfPagination` | `PdfPagination.AUTO` | Controls page splitting. `AUTO` automatically paginates. `SINGLE_PAGE` clips to one page |
| `content` | `@Composable () -> Unit` | -- | The composable content to render |

### Returns

`ByteArray` -- a valid PDF document (one or more pages).

### Throws

| Exception | When |
|:----------|:-----|
| [`Compose2PdfException`]({{ site.baseurl }}/api/exceptions) | Rendering fails (wraps the underlying cause) |
| `IllegalArgumentException` | Precondition failures (not wrapped) |

### Example

```kotlin
val pdfBytes = renderToPdf(
    config = PdfPageConfig.LetterWithMargins,
    mode = RenderMode.VECTOR,
) {
    // Direct children are "keep-together" units
    ReportHeader()
    DataTable(items)       // kept together on one page
    SummarySection()       // pushed to next page if needed
}
File("report.pdf").writeBytes(pdfBytes)
```

---

## Auto-paginating (OutputStream)

```kotlin
fun renderToPdf(
    outputStream: OutputStream,
    config: PdfPageConfig = PdfPageConfig.A4,
    density: Density = Density(2f),
    mode: RenderMode = RenderMode.VECTOR,
    defaultFontFamily: FontFamily? = InterFontFamily,
    pagination: PdfPagination = PdfPagination.AUTO,
    content: @Composable () -> Unit,
)
```

Streaming variant -- writes the PDF directly to `outputStream` without creating an intermediate `ByteArray`. The stream is **not closed** by this function.

Parameters and behavior are identical to the `ByteArray` variant above.

### Example

```kotlin
// Ktor
call.respondOutputStream(ContentType.Application.Pdf) {
    renderToPdf(this, config = PdfPageConfig.A4WithMargins) {
        ReportContent(data)
    }
}

// File
FileOutputStream("report.pdf").use { out ->
    renderToPdf(out) { ReportContent(data) }
}
```

---

## Multi-page

```kotlin
fun renderToPdf(
    pages: Int,
    config: PdfPageConfig = PdfPageConfig.A4,
    density: Density = Density(2f),
    mode: RenderMode = RenderMode.VECTOR,
    defaultFontFamily: FontFamily? = InterFontFamily,
    content: @Composable (pageIndex: Int) -> Unit,
): ByteArray
```

Renders multiple pages of Compose content to a single PDF. The page count must be known upfront.

### Parameters

| Parameter | Type | Default | Description |
|:----------|:-----|:--------|:------------|
| `pages` | `Int` | -- | Number of pages to render (must be positive) |
| `config` | [`PdfPageConfig`]({{ site.baseurl }}/api/pdf-page-config) | `PdfPageConfig.A4` | Page size and margins (applied uniformly to all pages) |
| `density` | `Density` | `Density(2f)` | Pixel resolution for Compose layout |
| `mode` | [`RenderMode`]({{ site.baseurl }}/api/render-mode) | `RenderMode.VECTOR` | Vector or raster rendering |
| `defaultFontFamily` | `FontFamily?` | [`InterFontFamily`]({{ site.baseurl }}/api/fonts) | Default font family |
| `content` | `@Composable (pageIndex: Int) -> Unit` | -- | Content for each page. Receives the **zero-based** page index |

### Returns

`ByteArray` -- a valid multi-page PDF document.

### Throws

| Exception | When |
|:----------|:-----|
| [`Compose2PdfException`]({{ site.baseurl }}/api/exceptions) | Rendering fails |
| `IllegalArgumentException` | `pages` is not positive |

### Example

```kotlin
val totalPages = 3
val pdfBytes = renderToPdf(
    pages = totalPages,
    config = PdfPageConfig.A4WithMargins,
) { pageIndex ->
    Column(Modifier.fillMaxSize()) {
        Text("Page ${pageIndex + 1} of $totalPages")
        when (pageIndex) {
            0 -> CoverPage()
            1 -> DataPage()
            2 -> SummaryPage()
        }
    }
}
```

---

## Multi-page (OutputStream)

```kotlin
fun renderToPdf(
    outputStream: OutputStream,
    pages: Int,
    config: PdfPageConfig = PdfPageConfig.A4,
    density: Density = Density(2f),
    mode: RenderMode = RenderMode.VECTOR,
    defaultFontFamily: FontFamily? = InterFontFamily,
    content: @Composable (pageIndex: Int) -> Unit,
)
```

Streaming variant of the multi-page API. Writes the PDF directly to `outputStream`. The stream is **not closed** by this function.

Parameters and behavior are identical to the `ByteArray` multi-page variant above.

### Example

```kotlin
call.respondOutputStream(ContentType.Application.Pdf) {
    renderToPdf(this, pages = 3, config = PdfPageConfig.A4WithMargins) { pageIndex ->
        PageContent(pageIndex)
    }
}
```

---

## Thread safety

Both overloads are **not thread-safe**. Concurrent calls should be serialized externally (e.g., via a `Mutex` or single-threaded `Dispatchers`).

---

## See also

- [Usage: Single Page]({{ site.baseurl }}/usage/single-page)
- [Usage: Multi-page]({{ site.baseurl }}/usage/multi-page)
- [Usage: Auto-pagination]({{ site.baseurl }}/usage/auto-pagination)
- [Guide: Server-side & Ktor]({{ site.baseurl }}/guides/server-side)
