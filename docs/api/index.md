---
title: API Reference
nav_order: 4
has_children: true
---

# API Reference

Complete reference for all public types and functions in compose2pdf.

```kotlin
import com.chrisjenx.compose2pdf.*
```

---

## Quick reference

| Function / Type | Description |
|:----------------|:------------|
| [`renderToPdf { content }`]({{ site.baseurl }}/api/render-to-pdf) | Render to PDF, returns `ByteArray` |
| [`renderToPdf(outputStream) { content }`]({{ site.baseurl }}/api/render-to-pdf) | Render to PDF, writes to `OutputStream` (streaming) |
| [`renderToPdf(pages) { pageIndex -> content }`]({{ site.baseurl }}/api/render-to-pdf) | Render multiple pages, returns `ByteArray` |
| [`renderToPdf(outputStream, pages) { pageIndex -> content }`]({{ site.baseurl }}/api/render-to-pdf) | Render multiple pages, writes to `OutputStream` |
| `PdfPagination` | `AUTO` (auto-paginate) or `SINGLE_PAGE` (clip) |
| [`PdfPageConfig`]({{ site.baseurl }}/api/pdf-page-config) | Page dimensions and margins (A4, Letter, A3, custom) |
| [`PdfMargins`]({{ site.baseurl }}/api/pdf-margins) | Page margin presets and custom margins |
| [`PdfLink`]({{ site.baseurl }}/api/pdf-link) | Clickable URL annotation composable |
| [`PaginatedColumn`]({{ site.baseurl }}/api/paginated-column) | Column layout with page-break protection |
| [`LocalPdfPageConfig`]({{ site.baseurl }}/api/local-pdf-page-config) | CompositionLocal for page dimensions inside `renderToPdf` |
| [`RenderMode`]({{ site.baseurl }}/api/render-mode) | Vector or raster rendering mode |
| [`PdfRoundedCornerShape`]({{ site.baseurl }}/api/pdf-rounded-corner-shape) | PDF-safe rounded corners for non-uniform radii |
| [`Shape.asPdfSafe()`]({{ site.baseurl }}/api/pdf-rounded-corner-shape) | Wrap any shape for correct PDF rendering |
| [`InterFontFamily`]({{ site.baseurl }}/api/fonts) | Bundled Inter font family |
| [`Compose2PdfException`]({{ site.baseurl }}/api/exceptions) | Exception thrown on rendering failure |
