---
title: LocalPdfPageConfig
parent: API Reference
nav_order: 6
---

# LocalPdfPageConfig

CompositionLocal providing the current [PdfPageConfig]({{ site.baseurl }}/api/pdf-page-config) during PDF rendering.

```kotlin
val LocalPdfPageConfig = compositionLocalOf<PdfPageConfig?> { null }
```

---

## Behavior

**Inside `renderToPdf`:** Provides the active page configuration including page dimensions, margins, and computed content area.

**Outside `renderToPdf`:** The value is `null`. This makes it safe to use in composables shared between screen UI and PDF output — check for null before using.

---

## Available properties

Through `LocalPdfPageConfig.current`, you get a [`PdfPageConfig`]({{ site.baseurl }}/api/pdf-page-config) with:

| Property | Type | Description |
|:---------|:-----|:------------|
| `width` | `Dp` | Total page width |
| `height` | `Dp` | Total page height |
| `margins` | `PdfMargins` | Page margins |
| `contentWidth` | `Dp` | Usable width (width minus left/right margins) |
| `contentHeight` | `Dp` | Usable height (height minus top/bottom margins) |

---

## Examples

### Read page dimensions in content

```kotlin
renderToPdf(config = PdfPageConfig.A4WithMargins) {
    val pageConfig = LocalPdfPageConfig.current
    Text("Content area: ${pageConfig?.contentWidth} x ${pageConfig?.contentHeight}")
}
```

### Adapt layout to page size

```kotlin
@Composable
fun AdaptiveGrid(items: List<Item>) {
    val pageConfig = LocalPdfPageConfig.current
    val columns = if ((pageConfig?.contentWidth ?: 400.dp) > 500.dp) 3 else 2
    // ... layout items in grid
}
```

---

## See also

- [API Reference: PdfPageConfig]({{ site.baseurl }}/api/pdf-page-config) -- Page dimensions and margins
- [API Reference: PaginatedColumn]({{ site.baseurl }}/api/paginated-column) -- Uses this local for page-break calculations
