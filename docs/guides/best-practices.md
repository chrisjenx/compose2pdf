---
title: Best Practices
parent: Guides
nav_order: 1
---

# Best Practices

Tips for producing high-quality PDFs with compose2pdf.

---

## Use PdfRoundedCornerShape for non-uniform corners

Standard `RoundedCornerShape` works for uniform corners, but when corners differ, use `PdfRoundedCornerShape`:

```kotlin
// Good: correct in PDF
Modifier.clip(PdfRoundedCornerShape(topStart = 24.dp, bottomEnd = 24.dp))

// Bad: all corners will look the same in vector mode
Modifier.clip(RoundedCornerShape(topStart = 24.dp, bottomEnd = 24.dp))
```

If you have an existing `Shape`, wrap it with `.asPdfSafe()`.

---

## Choose density wisely

| Use case | Recommended density |
|:---------|:-------------------|
| Vector mode (default) | `Density(2f)` -- the default, good balance |
| Raster mode, screen viewing | `Density(2f)` |
| Raster mode, print quality | `Density(3f)` |
| Maximum raster quality | `Density(4f)` -- large files, high memory |

Higher density in **vector mode** primarily improves sub-pixel positioning. In **raster mode**, it directly controls image resolution.

---

## Prefer vector mode for text documents

`RenderMode.VECTOR` (the default) produces:
- Selectable, searchable text
- Crisp rendering at any zoom level
- Smaller file sizes (10-100 KB typical)
- Embedded, subsetted fonts

Reserve `RenderMode.RASTER` for content that relies on visual effects not supported in vector conversion (e.g., gradients).

---

## Think in points for print

Compose `Dp` equals PDF points (1/72 inch). Design with physical dimensions in mind:

| Measurement | Value |
|:------------|:------|
| 1 inch | 72 dp |
| 1 cm | ~28.35 dp |
| 1 mm | ~2.835 dp |
| 10pt font | 10.sp |
| 12pt font | 12.sp |

The content area for A4 with normal margins is **451 x 698 dp** (about 6.3 x 9.7 inches).

---

## Use the bundled Inter font

`InterFontFamily` (the default) guarantees:
- Identical rendering between Compose layout and PDF embedding
- No dependency on system fonts
- Consistent output across macOS, Linux, and Windows

Only switch to `null` (system fonts) or a custom `FontFamily` when you need a specific typeface.

---

## Structure multi-page layouts with composables

Extract shared page elements into reusable composable functions:

```kotlin
@Composable
fun PageChrome(
    pageIndex: Int,
    totalPages: Int,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Header()
        Spacer(Modifier.height(24.dp))
        Column(Modifier.weight(1f), content = content)
        Footer(pageIndex + 1, totalPages)
    }
}

// Usage
renderToPdf(pages = 5) { pageIndex ->
    PageChrome(pageIndex, 5) {
        // Page-specific content
    }
}
```

---

## Use auto-pagination for flowing content

By default, `renderToPdf` automatically splits content across pages. Place content items as direct children for best results:

```kotlin
renderToPdf(config = PdfPageConfig.A4WithMargins) {
    // Each direct child is a "keep-together" unit
    ReportHeader()
    DataTable(items)       // won't be split across pages
    SummarySection()       // pushed to next page if needed
}
```

Avoid wrapping everything in a single `Column` — the library can only keep **direct children** together. Content using `fillMaxHeight()` or `Modifier.weight()` won't auto-paginate well.

---

## Use manual pagination for fixed layouts

When you need full control (headers/footers, page numbers, page-specific content), use the manual multi-page API:

```kotlin
val itemsPerPage = 20
val pageCount = (items.size + itemsPerPage - 1) / itemsPerPage

renderToPdf(pages = pageCount) { pageIndex ->
    val pageItems = items.drop(pageIndex * itemsPerPage).take(itemsPerPage)
    // Render pageItems
}
```

---

## Use streaming for large documents or servers

For large PDFs or server-side rendering, use the `OutputStream` overload to avoid holding the final PDF bytes in memory:

```kotlin
// Stream to a file
FileOutputStream("report.pdf").use { out ->
    renderToPdf(out, config = PdfPageConfig.A4WithMargins) {
        LargeReport(data)
    }
}

// Stream to an HTTP response (Ktor)
call.respondOutputStream(ContentType.Application.Pdf) {
    renderToPdf(this) { ReportContent() }
}
```

The `ByteArray` variant is simpler for small documents. Use `OutputStream` when memory matters.

See [Server-side & Ktor]({{ site.baseurl }}/guides/server-side) for more patterns.

---

## Serialize concurrent renders

`renderToPdf` is not thread-safe. If generating PDFs concurrently:

```kotlin
val mutex = Mutex()

suspend fun generatePdf(content: @Composable () -> Unit): ByteArray {
    return mutex.withLock {
        renderToPdf { content() }
    }
}
```

---

## See also

- [Troubleshooting]({{ site.baseurl }}/guides/troubleshooting) -- Common issues and fixes
- [Supported Features]({{ site.baseurl }}/guides/supported-features) -- What works in each mode
