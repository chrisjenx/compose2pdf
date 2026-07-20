---
title: Auto-pagination
parent: Usage Guide
nav_order: 3
---

# Auto-pagination

By default, `renderToPdf` automatically splits content across multiple pages when it overflows. This is the simplest way to create multi-page documents.

---

## How it works

```kotlin
val pdf = renderToPdf(config = PdfPageConfig.A4WithMargins) {
    // Each direct child is a "keep-together" unit
    ReportHeader()
    DataTable(items)       // won't be split across pages
    SummarySection()       // pushed to next page if needed
}
```

1. Your content is laid out in a tall virtual scene
2. Direct children are measured as "keep-together" units
3. If a child would straddle a page boundary, padding is inserted to push it to the next page
4. A single child taller than a page flows continuously across pages

---

## Keep-together units

The library treats **direct children** of the content block as keep-together units. This means:

```kotlin
renderToPdf {
    // GOOD: Each section is a direct child -- can be kept together
    Section1()
    Section2()
    Section3()
}
```

```kotlin
renderToPdf {
    // BAD: Single Column wraps everything -- no page-break decisions possible
    Column {
        Section1()
        Section2()
        Section3()
    }
}
```

{: .note }
Place content items as **direct children** rather than wrapping in a single `Column`. The library can only keep direct children together.

### Using `PaginatedColumn` inside wrappers

If you need to wrap content in a provider, theme, or layout container, use the public [`PaginatedColumn`]({{ site.baseurl }}/api/paginated-column) composable inside the wrapper to restore per-child page breaking:

```kotlin
renderToPdf(config = PdfPageConfig.A4WithMargins) {
    MyThemeProvider {              // auto-pagination sees 1 child
        PaginatedColumn {          // restores per-child page breaking
            Section1()             // keep-together unit
            Section2()             // keep-together unit
            Section3()             // keep-together unit
        }
    }
}
```

`PaginatedColumn` reads the page configuration automatically from the composition — no parameters needed beyond `modifier` and `content`.

---

## Oversized children

If a single child is taller than the page content area, it flows continuously across pages rather than being truncated:

```kotlin
renderToPdf(config = PdfPageConfig.A4WithMargins) {
    // This 2000dp element spans ~3 pages (A4 content height is 698dp)
    Spacer(Modifier.fillMaxWidth().height(2000.dp))
}
```

---

## Disabling auto-pagination

To clip content to a single page (pre-0.2.0 behavior), pass `PdfPagination.SINGLE_PAGE`:

```kotlin
val pdf = renderToPdf(pagination = PdfPagination.SINGLE_PAGE) {
    // Content beyond the page boundary is clipped
    Text("Only what fits on one page is visible")
}
```

---

## Fallback behavior

Auto-pagination falls back to single-page rendering when:

- **Content fits on one page** -- no pagination needed
- **Content uses `fillMaxHeight()`** -- the layout fills the entire measurement scene, so the library can't determine the natural content height

In both cases, you get a single-page PDF identical to `PdfPagination.SINGLE_PAGE`.

---

## Page limit

Auto-pagination supports up to **100 pages**. If your content requires more pages, the output is truncated at 100 pages and a warning is logged. For very large documents, consider splitting content into batches.

---

## Streaming output

For large auto-paginated documents, use the streaming variant to avoid holding the final PDF bytes in memory:

```kotlin
FileOutputStream("report.pdf").use { out ->
    renderToPdf(out, config = PdfPageConfig.A4WithMargins) {
        repeat(50) { index ->
            DataRow(items[index])
        }
    }
}
```

---

## When to use auto vs manual pagination

| | Auto-pagination | Manual pagination |
|:--|:----------------|:------------------|
| **Best for** | Flowing content (reports, lists, articles) | Fixed-layout pages (cover + data + summary) |
| **Page breaks** | Automatic -- elements kept together | You decide what goes on each page |
| **Headers/footers** | Supported via `header`/`footer` slots | Use `Spacer(Modifier.weight(1f))` pattern |
| **Page count** | Determined automatically | Must be known upfront |
| **`fillMaxHeight()`** | Falls back to single page | Works as expected |

---

## Headers and footers

Add a repeated header and/or footer band to every page with the `header` and `footer`
slots. Both receive a `PdfPageInfo` with `pageIndex` (zero-based), `pageCount`, and a
convenience `pageNumber` (one-based):

```kotlin
val pdf = renderToPdf(
    config = PdfPageConfig.A4WithMargins,
    header = {
        Row(Modifier.fillMaxWidth().background(Color(0xFF1565C0)).padding(10.dp)) {
            Text("Acme Corp", color = Color.White, fontWeight = FontWeight.Bold)
        }
    },
    footer = { info ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            Text("Page ${info.pageNumber} of ${info.pageCount}", fontSize = 9.sp)
        }
    },
) {
    // body content — auto-paginated between the bands
}
```

### How the space works

- Bands render **inside the page margins**, anchored about 0.25in (18pt) from the
  physical page edge — the same convention as a browser's print header/footer or a
  word processor's "header from edge" setting.
- Adding a header/footer does **not** move or shrink your body content: the body keeps
  using your configured `margins` as long as the band (edge inset + band height + a
  small ~10pt gap to the body) fits within that margin. Only a band too tall for its
  margin pushes the body inward to make room. `LocalPdfPageConfig` (and therefore
  `PaginatedColumn`) always reflects this effective content area, which equals your
  configured margins in the common case.
- Each slot's height is **measured once** and is stable on **every** page — content
  taller than the measured band is clipped. Measurement uses a `pageCount = 2`
  sentinel, so a footer wrapped in `if (info.pageCount > 1) { ... }` still reserves
  its space.
- A header + footer that leave no room for content throw `IllegalArgumentException`.
- Slots work in both `VECTOR` and `RASTER` modes, with `PdfPagination.SINGLE_PAGE`,
  and on single-page documents (`pageCount == 1`).
- `PdfLink` works inside slots.
- Want a large masthead only on page 1? Keep it in the body content — the `header`
  slot is for the repeated band. If pagination truncates at the 100-page cap,
  `pageCount` reflects the emitted pages.

---

## See also

- [API Reference: PaginatedColumn]({{ site.baseurl }}/api/paginated-column) -- Public composable for fine-grained page breaks
- [Multi-page Documents]({{ site.baseurl }}/usage/multi-page) -- Manual pagination
- [API Reference: renderToPdf]({{ site.baseurl }}/api/render-to-pdf) -- Full API signatures
- [Best Practices]({{ site.baseurl }}/guides/best-practices) -- Tips for auto-pagination
