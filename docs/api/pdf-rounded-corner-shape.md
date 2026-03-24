---
title: PdfRoundedCornerShape
parent: API Reference
nav_order: 6
---

# PdfRoundedCornerShape

A Shape that produces correct vector PDF output for rounded rectangles with per-corner radii.

---

## PdfRoundedCornerShape()

```kotlin
fun PdfRoundedCornerShape(
    topStart: Dp = 0.dp,
    topEnd: Dp = 0.dp,
    bottomEnd: Dp = 0.dp,
    bottomStart: Dp = 0.dp,
): Shape
```

### Parameters

| Parameter | Type | Default | Description |
|:----------|:-----|:--------|:------------|
| `topStart` | `Dp` | `0.dp` | Top-left corner radius |
| `topEnd` | `Dp` | `0.dp` | Top-right corner radius |
| `bottomEnd` | `Dp` | `0.dp` | Bottom-right corner radius |
| `bottomStart` | `Dp` | `0.dp` | Bottom-left corner radius |

### Returns

`Shape` -- a PDF-safe shape. For non-uniform corners, produces an `Outline.Generic` with explicit bezier paths. For uniform corners, delegates to standard `RoundedCornerShape`.

### Why this exists

Skia's SVGCanvas serializes `RoundRect` clip paths as `<rect rx="..." ry="...">`, which only supports a single corner radius. When corners differ, the SVG loses the per-corner information and all corners render identically.

`PdfRoundedCornerShape` detects non-uniform corners and emits an explicit bezier `<path d="...">` instead, preserving the full geometry in the PDF.

### Example

```kotlin
Box(
    Modifier
        .size(100.dp)
        .clip(PdfRoundedCornerShape(topStart = 24.dp, bottomEnd = 24.dp))
        .background(Color.Blue)
)
```

---

## Shape.asPdfSafe()

```kotlin
fun Shape.asPdfSafe(): Shape
```

Wraps any `Shape` to ensure asymmetric rounded rect outlines are emitted as explicit bezier paths. Returns the same shape if already wrapped.

### Example

```kotlin
val myShape = RoundedCornerShape(topStart = 16.dp, bottomEnd = 16.dp)
Box(Modifier.clip(myShape.asPdfSafe()).background(Color.Green))
```

---

{: .tip }
You only need `PdfRoundedCornerShape` when corners have **different** radii. For uniform corners (all the same), standard `RoundedCornerShape` works perfectly in PDF output.

---

## See also

- [Usage: Shapes and Drawing]({{ site.baseurl }}/usage/shapes-and-drawing) -- All shape options with examples
