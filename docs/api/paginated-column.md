---
title: PaginatedColumn
parent: API Reference
nav_order: 5
---

# PaginatedColumn

Composable layout that prevents direct children from being split across PDF page boundaries.

```kotlin
@Composable
fun PaginatedColumn(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
)
```

---

## Parameters

| Parameter | Type | Default | Description |
|:----------|:-----|:--------|:------------|
| `modifier` | `Modifier` | `Modifier` | Modifier applied to the layout |
| `content` | `@Composable () -> Unit` | -- | Children to lay out with page-break protection |

---

## Behavior

`PaginatedColumn` is a `Column`-like layout that inserts padding at page boundaries so that no direct child is split across pages.

- **Keep-together:** If a child would straddle a page boundary, it is pushed to the next page
- **Oversized children:** A single child taller than a page flows continuously across pages
- **Reads page config automatically:** Uses [LocalPdfPageConfig]({{ site.baseurl }}/api/local-pdf-page-config) and `LocalDensity` from the composition — no manual height parameter needed

---

## When to use

[`renderToPdf`]({{ site.baseurl }}/api/render-to-pdf) with `PdfPagination.AUTO` already wraps your content in a `PaginatedColumn` internally, so **direct children** of the content block are protected automatically.

Use the public `PaginatedColumn` when your content is wrapped in a provider, `Column`, or `Box` that hides individual children from the automatic pagination:

```kotlin
renderToPdf(config = PdfPageConfig.A4WithMargins) {
    MyThemeProvider {          // auto-pagination sees 1 child (the provider)
        PaginatedColumn {      // restores per-child page breaking
            items.forEach { item ->
                ItemCard(item) // each card is a "keep-together" unit
            }
        }
    }
}
```

Without the explicit `PaginatedColumn`, the theme provider is a single child and auto-pagination cannot insert page breaks between the items inside it.

---

## Examples

### Invoice with style wrapper

```kotlin
renderToPdf(config = PdfPageConfig.A4WithMargins) {
    InvoiceStyleProvider(style) {
        PaginatedColumn {
            HeaderSection()
            BillToSection()
            for (item in lineItems) {
                LineItemRow(item)   // each row is a keep-together unit
            }
            SummarySection()
        }
    }
}
```

### Mixed fixed and paginated content

```kotlin
renderToPdf(config = PdfPageConfig.A4WithMargins) {
    // Fixed header — not inside PaginatedColumn
    CompanyLogo()
    Spacer(Modifier.height(16.dp))

    // Paginated body
    PaginatedColumn {
        dataRows.forEach { row ->
            DataRowCard(row)
        }
    }
}
```

---

## See also

- [Usage: Auto-pagination]({{ site.baseurl }}/usage/auto-pagination) -- How automatic page breaking works
- [API Reference: LocalPdfPageConfig]({{ site.baseurl }}/api/local-pdf-page-config) -- Access page dimensions in content
- [API Reference: renderToPdf]({{ site.baseurl }}/api/render-to-pdf) -- Full API signatures
