---
title: Troubleshooting
parent: Guides
nav_order: 2
---

# Troubleshooting

Common issues and how to fix them.

---

## Non-uniform rounded corners render identically

**Problem:** All corners of a `RoundedCornerShape` look the same even though you specified different radii.

**Cause:** Skia's SVGCanvas serializes rounded rects as `<rect rx ry>` which only supports a single corner radius.

**Fix:** Use `PdfRoundedCornerShape` for non-uniform corners:

```kotlin
// Instead of:
Modifier.clip(RoundedCornerShape(topStart = 24.dp, bottomEnd = 24.dp))

// Use:
Modifier.clip(PdfRoundedCornerShape(topStart = 24.dp, bottomEnd = 24.dp))
```

---

## Text is not selectable in the PDF

**Problem:** Text appears as an image and cannot be selected or searched.

**Cause:** The PDF was generated with `RenderMode.RASTER`.

**Fix:** Use `RenderMode.VECTOR` (the default):

```kotlin
renderToPdf(mode = RenderMode.VECTOR) { /* content */ }
```

---

## Variable fonts don't render correctly

**Problem:** A custom font appears as a fallback (Helvetica) or renders incorrectly.

**Cause:** PDFBox cannot handle variable fonts (fonts with an `fvar` OpenType table). The library's `FontResolver` automatically skips them.

**Fix:** Use static `.ttf` or `.otf` font files. Most font families distribute static variants alongside variable ones. For example, use `Inter-Regular.ttf` instead of `Inter-Variable.ttf`.

---

## PDF file size is too large

**Problem:** Generated PDFs are several megabytes.

**Possible causes and fixes:**

| Cause | Fix |
|:------|:----|
| Using `RenderMode.RASTER` | Switch to `RenderMode.VECTOR` |
| High density in raster mode | Lower `Density` (e.g., `2f` instead of `4f`) |
| Large embedded images | Resize images before rendering |
| Many pages with images | Each page embeds its own image data |

Vector mode typically produces 10-100 KB files. Raster mode at `Density(3f)` on A4 can exceed 5 MB per page.

---

## Gradients don't appear in vector mode

**Problem:** Gradient backgrounds render as flat colors or bitmapped sections.

**Cause:** Skia's SVGCanvas does not emit gradient definitions in a format the converter processes. Gradients are rasterized by Skia before reaching the SVG output.

**Fix options:**
1. Use `RenderMode.RASTER` for gradient-heavy pages
2. Simulate gradients with thin colored strips:
   ```kotlin
   Row(Modifier.fillMaxWidth().height(60.dp)) {
       for (i in 0 until 50) {
           val fraction = i / 50f
           val color = lerp(Color.Blue, Color.Red, fraction)
           Box(Modifier.weight(1f).fillMaxHeight().background(color))
       }
   }
   ```

---

## Content overflows the page

**Problem:** Content extends beyond the visible page area or is clipped.

**Cause:** Compose does not auto-paginate. The content area is fixed at `config.contentWidth x config.contentHeight`.

**Fix:** Split content across pages manually:

```kotlin
val itemsPerPage = 20
val pageCount = (items.size + itemsPerPage - 1) / itemsPerPage

renderToPdf(pages = pageCount) { pageIndex ->
    val pageItems = items.drop(pageIndex * itemsPerPage).take(itemsPerPage)
    Column { pageItems.forEach { Text(it) } }
}
```

---

## Headless rendering on Linux CI

**Problem:** `renderToPdf` fails on a headless Linux server (CI) with display-related errors.

**Cause:** Compose Desktop requires a display server (X11) even for offscreen rendering.

**Fix:** Use `xvfb-run` to provide a virtual framebuffer:

```yaml
# GitHub Actions example
- name: Run tests
  run: xvfb-run ./gradlew :compose2pdf:test
```

For Docker, install `xvfb` and wrap the command:

```bash
apt-get install -y xvfb
xvfb-run ./gradlew :compose2pdf:test
```

---

## Compose2PdfException during rendering

**Problem:** A `Compose2PdfException` is thrown.

**Fix:** Check the `cause` for the underlying error:

```kotlin
try {
    renderToPdf { content() }
} catch (e: Compose2PdfException) {
    println("Rendering failed: ${e.message}")
    e.cause?.printStackTrace()
}
```

Common causes:
- Invalid font file (corrupted or variable font)
- Out of memory for very large pages at high density
- Compose layout errors in the content lambda

---

## @InternalComposeUiApi warnings

**Problem:** Compiler warnings about `@InternalComposeUiApi` usage.

**Cause:** The library uses `CanvasLayersComposeScene` which is an internal Compose API. No public alternative exists.

**Fix:** This is expected. The opt-in is configured in the library's build.gradle.kts. If you see warnings in your own build, they come from the library dependency and can be safely ignored.

---

## See also

- [Best Practices]({{ site.baseurl }}/guides/best-practices) -- Avoid common pitfalls
- [Supported Features]({{ site.baseurl }}/guides/supported-features) -- What works in each mode
