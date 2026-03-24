---
title: PdfPageConfig
parent: API Reference
nav_order: 2
---

# PdfPageConfig

Page dimensions and margins for PDF output.

```kotlin
data class PdfPageConfig(
    val width: Dp,
    val height: Dp,
    val margins: PdfMargins = PdfMargins.None,
)
```

---

## Properties

| Property | Type | Description |
|:---------|:-----|:------------|
| `width` | `Dp` | Full page width in PDF points |
| `height` | `Dp` | Full page height in PDF points |
| `margins` | [`PdfMargins`]({{ site.baseurl }}/api/pdf-margins) | Page margins (default: none) |
| `contentWidth` | `Dp` (computed) | `width - margins.left - margins.right` |
| `contentHeight` | `Dp` (computed) | `height - margins.top - margins.bottom` |

{: .note }
Compose `Dp` and PDF points are identical: both equal 1/72 of an inch. A value of `72.dp` is exactly 1 inch in the PDF. These values are **not** affected by the render density parameter.

---

## Companion presets

| Preset | Width | Height | Margins | Physical |
|:-------|:------|:-------|:--------|:---------|
| `A4` | 595 dp | 842 dp | None | 210 x 297 mm |
| `A4WithMargins` | 595 dp | 842 dp | Normal (72dp) | 210 x 297 mm |
| `Letter` | 612 dp | 792 dp | None | 8.5 x 11 in |
| `LetterWithMargins` | 612 dp | 792 dp | Normal (72dp) | 8.5 x 11 in |
| `A3` | 842 dp | 1191 dp | None | 297 x 420 mm |
| `A3WithMargins` | 842 dp | 1191 dp | Normal (72dp) | 297 x 420 mm |

---

## Methods

### landscape()

```kotlin
fun landscape(): PdfPageConfig
```

Returns a landscape version with width and height swapped. Margins are rotated 90 degrees (left becomes top, top becomes right, right becomes bottom, bottom becomes left).

```kotlin
val landscape = PdfPageConfig.A4.landscape()
// width = 842.dp, height = 595.dp
```

---

## Validation

Construction throws `IllegalArgumentException` if:
- `width` or `height` is not positive
- `contentWidth` or `contentHeight` (after subtracting margins) is not positive

---

## Examples

### Custom page size

```kotlin
val config = PdfPageConfig(
    width = 360.dp,   // 5 inches
    height = 504.dp,  // 7 inches
    margins = PdfMargins.Narrow,
)
```

### Add margins to a preset

```kotlin
val config = PdfPageConfig.A4.copy(margins = PdfMargins.Narrow)
```

---

## See also

- [Usage: Page Configuration]({{ site.baseurl }}/usage/page-configuration) -- All configuration options with examples
- [PdfMargins]({{ site.baseurl }}/api/pdf-margins)
