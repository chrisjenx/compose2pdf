---
title: PdfPageInfo
parent: API Reference
nav_order: 9
---

# PdfPageInfo

Page information passed to the `header`/`footer` slots on the auto-pagination [`renderToPdf`]({{ site.baseurl }}/api/render-to-pdf) overloads.

```kotlin
class PdfPageInfo(
    val pageIndex: Int,
    val pageCount: Int,
)
```

Deliberately a plain class (not a `data class`) so fields can be added later without breaking binary compatibility.

---

## Properties

| Property | Type | Description |
|:---------|:-----|:------------|
| `pageIndex` | `Int` | Zero-based index of the page being rendered |
| `pageCount` | `Int` | Total number of emitted pages. If auto-pagination truncates at its page cap, this is the emitted (truncated) count |
| `pageNumber` | `Int` | One-based page number, for display: `"Page $pageNumber of $pageCount"`. Computed as `pageIndex + 1` |

---

## Validation

Construction throws `IllegalArgumentException` if `pageIndex` is negative, `pageCount` is not positive, or `pageIndex >= pageCount`.

---

## See also

- [renderToPdf]({{ site.baseurl }}/api/render-to-pdf) -- `header`/`footer` parameters that receive this type
- [Usage: Auto-pagination]({{ site.baseurl }}/usage/auto-pagination) -- Headers and footers section
