---
title: Supported Features
parent: Guides
nav_order: 4
---

# Supported Compose Features

Comprehensive matrix of Compose feature support in compose2pdf's vector and raster rendering modes.

---

## Text

| Feature | Vector | Raster | Notes |
|:--------|:------:|:------:|:------|
| Basic text rendering | Full | Full | Selectable in vector mode |
| Font weight (Normal, Bold) | Full | Full | Bundled Inter covers both |
| Font style (Normal, Italic) | Full | Full | Bundled Inter covers both |
| Font size (`fontSize`) | Full | Full | |
| Text color | Full | Full | |
| Underline | Full | Full | |
| Strikethrough | Full | Full | |
| Combined decorations | Full | Full | Underline + strikethrough |
| Text alignment (Start, Center, End) | Full | Full | |
| Letter spacing | Full | Full | |
| Line height | Full | Full | |
| Max lines + ellipsis | Full | Full | |
| Text wrapping | Full | Full | |

---

## Layout

| Feature | Vector | Raster | Notes |
|:--------|:------:|:------:|:------|
| `Column` | Full | Full | |
| `Row` | Full | Full | |
| `Box` | Full | Full | |
| `Modifier.padding()` | Full | Full | |
| `Modifier.fillMaxSize/Width/Height` | Full | Full | |
| `Modifier.size/width/height` | Full | Full | |
| `Modifier.weight()` | Full | Full | |
| `Modifier.offset()` | Full | Full | |
| `Arrangement.spacedBy` | Full | Full | |
| `Arrangement.SpaceBetween` | Full | Full | |
| `Alignment.Center/Start/End` | Full | Full | |
| `Spacer` | Full | Full | |
| `Divider` | Full | Full | |

---

## Shapes and Drawing

| Feature | Vector | Raster | Notes |
|:--------|:------:|:------:|:------|
| `Modifier.background(color)` | Full | Full | Solid colors |
| `Modifier.border()` | Full | Full | |
| `Modifier.clip(RoundedCornerShape)` | Full | Full | Uniform corners |
| `Modifier.clip(CircleShape)` | Full | Full | |
| `Modifier.clip()` non-uniform corners | Needs PdfRoundedCornerShape | Full | Standard shape loses info in SVG |
| `Modifier.alpha()` / opacity | Full | Full | |
| `Canvas.drawCircle` | Full | Full | Filled and stroked |
| `Canvas.drawRect` | Full | Full | |
| `Canvas.drawLine` | Full | Full | |
| `Canvas.drawArc` | Full | Full | |
| `Canvas.drawOval` | Full | Full | |
| `Canvas.drawPath` | Full | Full | moveTo, lineTo, cubicTo, quadTo |
| Stroke styles (width, cap, join) | Full | Full | |
| Dash patterns | Full | Full | |

---

## Images

| Feature | Vector | Raster | Notes |
|:--------|:------:|:------:|:------|
| `Image` composable | Full | Full | |
| Image sizing | Full | Full | |
| Image clipping (circle, rounded) | Full | Full | |
| Multiple images per page | Full | Full | |
| Scaled images | Full | Full | |
| Transparent images | Full | Full | Alpha channel preserved |

---

## compose2pdf-specific

| Feature | Vector | Raster | Notes |
|:--------|:------:|:------:|:------|
| `PdfLink` annotations | Full | Full | Clickable URLs in PDF |
| `PdfRoundedCornerShape` | Full | Full | PDF-safe asymmetric corners |
| `InterFontFamily` | Full | N/A | Font embedding (vector only) |
| Multi-page documents | Full | Full | |

---

## Limited or unsupported

| Feature | Vector | Raster | Notes |
|:--------|:------:|:------:|:------|
| Gradients | Limited | Full | SVGCanvas doesn't preserve gradient definitions |
| Shadow / elevation | No | Limited | Not converted to vector |
| `LazyColumn` / `LazyRow` | No | No | Use `Column` / `Row` instead |
| Scrolling | No | No | Pages are fixed-size |
| Animation | N/A | N/A | Static snapshot only |
| `TextField` (input) | No | No | PDF is static output |
| `DropdownMenu` | No | No | Interactive component |

{: .note }
"Limited" for gradients in vector mode means Skia may rasterize the gradient area before emitting it as SVG, resulting in a bitmap section within an otherwise vector PDF. Raster mode handles gradients pixel-perfectly.

---

## See also

- [Vector vs Raster]({{ site.baseurl }}/usage/vector-vs-raster) -- Choosing the right mode
- [Troubleshooting]({{ site.baseurl }}/guides/troubleshooting) -- Fixing common issues
