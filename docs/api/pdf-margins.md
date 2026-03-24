---
title: PdfMargins
parent: API Reference
nav_order: 3
---

# PdfMargins

Margins applied to each page of the PDF, in PDF points (1pt = 1/72 inch).

```kotlin
data class PdfMargins(
    val top: Dp = 0.dp,
    val bottom: Dp = 0.dp,
    val left: Dp = 0.dp,
    val right: Dp = 0.dp,
)
```

---

## Properties

| Property | Type | Default | Description |
|:---------|:-----|:--------|:------------|
| `top` | `Dp` | `0.dp` | Top margin |
| `bottom` | `Dp` | `0.dp` | Bottom margin |
| `left` | `Dp` | `0.dp` | Left margin |
| `right` | `Dp` | `0.dp` | Right margin |

---

## Companion presets

| Preset | Value | Physical |
|:-------|:------|:---------|
| `None` | 0 dp all sides | No margins |
| `Narrow` | 24 dp all sides | ~8.5 mm / ~1/3 inch |
| `Normal` | 72 dp all sides | 1 inch / 25.4 mm |

---

## Factory methods

### symmetric

```kotlin
fun symmetric(horizontal: Dp = 0.dp, vertical: Dp = 0.dp): PdfMargins
```

Creates margins with the same horizontal (left/right) and vertical (top/bottom) values.

```kotlin
val margins = PdfMargins.symmetric(horizontal = 48.dp, vertical = 36.dp)
// top = 36, bottom = 36, left = 48, right = 48
```

---

## Validation

All margins must be non-negative. Construction throws `IllegalArgumentException` for negative values.

---

## See also

- [PdfPageConfig]({{ site.baseurl }}/api/pdf-page-config)
- [Usage: Page Configuration]({{ site.baseurl }}/usage/page-configuration)
