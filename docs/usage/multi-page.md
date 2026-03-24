---
title: Multi-page
parent: Usage Guide
nav_order: 2
---

# Multi-page Documents

Render multiple pages into a single PDF by specifying the page count upfront.

---

## Basic usage

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

{: .note }
Compose does **not** auto-paginate content. There is no automatic "flow content to next page" -- you must manage page breaks yourself by deciding what content goes on each page.

---

## See also

- [API Reference: renderToPdf]({{ site.baseurl }}/api/render-to-pdf) -- Full multi-page signature
- [Example: Multi-page Report]({{ site.baseurl }}/examples/report) -- Complete walkthrough
- [Layout]({{ site.baseurl }}/usage/layout) -- Using weights and spacers for page structure
