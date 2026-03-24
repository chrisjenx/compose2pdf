---
title: renderToPdf
parent: API Reference
nav_order: 1
---

# renderToPdf

The primary entry point for PDF generation. Two overloads: single-page and multi-page.

---

## Single page

```kotlin
fun renderToPdf(
    config: PdfPageConfig = PdfPageConfig.A4,
    density: Density = Density(2f),
    mode: RenderMode = RenderMode.VECTOR,
    defaultFontFamily: FontFamily? = InterFontFamily,
    content: @Composable () -> Unit,
): ByteArray
```

Renders a single page of Compose content to a PDF.

### Parameters

| Parameter | Type | Default | Description |
|:----------|:-----|:--------|:------------|
| `config` | [`PdfPageConfig`]({{ site.baseurl }}/api/pdf-page-config) | `PdfPageConfig.A4` | Page size and margins |
| `density` | `Density` | `Density(2f)` | Pixel resolution for Compose layout. Each PDF point maps to `density` x `density` pixels. Higher values improve anti-aliasing (especially for raster mode) at the cost of memory |
| `mode` | [`RenderMode`]({{ site.baseurl }}/api/render-mode) | `RenderMode.VECTOR` | Vector (SVG-based) or raster rendering |
| `defaultFontFamily` | `FontFamily?` | [`InterFontFamily`]({{ site.baseurl }}/api/fonts) | Font family for the default text style. When non-null, content is wrapped so both Compose and PDFBox use the same font. Pass `null` for system fonts |
| `content` | `@Composable () -> Unit` | -- | The composable content to render |

### Returns

`ByteArray` -- a valid PDF document.

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
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Text("Invoice #1234", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text("Amount: $1,250.00")
    }
}
File("invoice.pdf").writeBytes(pdfBytes)
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

## Thread safety

Both overloads are **not thread-safe**. Concurrent calls should be serialized externally (e.g., via a `Mutex` or single-threaded `Dispatchers`).

---

## See also

- [Usage: Single Page]({{ site.baseurl }}/usage/single-page)
- [Usage: Multi-page]({{ site.baseurl }}/usage/multi-page)
