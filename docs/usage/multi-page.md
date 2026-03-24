---
title: Multi-page
parent: Usage Guide
nav_order: 2
---

# Multi-page Documents

Two approaches: **automatic pagination** (content flows across pages) or **manual pagination** (you control each page).

---

## Auto-pagination (recommended)

The simplest approach — just provide your content and it automatically flows across pages:

```kotlin
val pdf = renderToPdf(config = PdfPageConfig.A4WithMargins) {
    ReportHeader()
    DataTable(items)       // kept together — won't be split across pages
    SummarySection()       // pushed to next page if needed
}
```

Direct children of the content block are treated as "keep-together" units. If a child would straddle a page boundary, it is pushed to the next page. A single child taller than a page flows continuously across pages.

{: .note }
For best page breaking, place content items as **direct children** rather than wrapping in a single `Column`. Direct children are what the library measures for page-break decisions.

---

## Manual pagination

For full control over what goes on each page, specify the page count upfront:

```kotlin
val pdf = renderToPdf(pages = 3) { pageIndex ->
    Text("Page ${pageIndex + 1}")
}
```

The `content` lambda receives a zero-based `pageIndex` so you can render different content per page.

---

## Page-specific content

Use `when` to dispatch content for each page:

```kotlin
val pdf = renderToPdf(
    pages = 3,
    config = PdfPageConfig.A4WithMargins,
) { pageIndex ->
    Column(Modifier.fillMaxSize()) {
        when (pageIndex) {
            0 -> CoverPage()
            1 -> DataPage()
            2 -> SummaryPage()
        }
    }
}
```

---

## Shared headers and footers

A common pattern is to share header/footer composables across pages and use `Spacer(Modifier.weight(1f))` to push the footer to the bottom:

```kotlin
val totalPages = 3

val pdf = renderToPdf(
    pages = totalPages,
    config = PdfPageConfig.A4WithMargins,
) { pageIndex ->
    Column(Modifier.fillMaxSize()) {
        // Shared header
        Row(
            Modifier
                .fillMaxWidth()
                .background(Color(0xFF1565C0))
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Text("Quarterly Report", color = Color.White, fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(24.dp))

        // Page-specific content
        when (pageIndex) {
            0 -> CoverPage()
            1 -> DataPage()
            2 -> SummaryPage()
        }

        // Push footer to the bottom of the page
        Spacer(Modifier.weight(1f))

        // Shared footer with page number
        Divider()
        Row(
            Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Acme Corp — Confidential", fontSize = 10.sp, color = Color.Gray)
            Text("Page ${pageIndex + 1} of $totalPages", fontSize = 10.sp, color = Color.Gray)
        }
    }
}
```

---

## Dynamic page count

The page count must be known before calling `renderToPdf`. Compute it from your data:

```kotlin
val items = loadInvoiceItems()
val itemsPerPage = 20
val pageCount = (items.size + itemsPerPage - 1) / itemsPerPage

val pdf = renderToPdf(pages = pageCount) { pageIndex ->
    val pageItems = items.drop(pageIndex * itemsPerPage).take(itemsPerPage)
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        for (item in pageItems) {
            Text(item.description)
        }
    }
}
```

---

## Configuration

All single-page parameters work with multi-page too. The config is applied uniformly to every page:

```kotlin
val pdf = renderToPdf(
    pages = 5,
    config = PdfPageConfig.LetterWithMargins,
    density = Density(2f),
    mode = RenderMode.VECTOR,
    defaultFontFamily = InterFontFamily,
) { pageIndex ->
    // ...
}
```

---

---

## When to use which

| | Auto-pagination | Manual pagination |
|:--|:----------------|:------------------|
| **Best for** | Flowing content (reports, lists, articles) | Fixed-layout pages (cover + data + summary) |
| **Page breaks** | Automatic — elements kept together | You decide what goes on each page |
| **Headers/footers** | Not supported (each page has different content) | Use `Spacer(Modifier.weight(1f))` pattern |
| **`fillMaxHeight()`** | Falls back to single page | Works as expected |

---

## See also

- [API Reference: renderToPdf]({{ site.baseurl }}/api/render-to-pdf) -- Full API signatures
- [Example: Multi-page Report]({{ site.baseurl }}/examples/report) -- Manual pagination walkthrough
- [Layout]({{ site.baseurl }}/usage/layout) -- Using weights and spacers for page structure
