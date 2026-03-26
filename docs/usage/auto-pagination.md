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
| **Headers/footers** | Not supported per-page | Use `Spacer(Modifier.weight(1f))` pattern |
| **Page count** | Determined automatically | Must be known upfront |
| **`fillMaxHeight()`** | Falls back to single page | Works as expected |

---

## See also

- [Multi-page Documents]({{ site.baseurl }}/usage/multi-page) -- Manual pagination
- [API Reference: renderToPdf]({{ site.baseurl }}/api/render-to-pdf) -- Full API signatures
- [Best Practices]({{ site.baseurl }}/guides/best-practices) -- Tips for auto-pagination
