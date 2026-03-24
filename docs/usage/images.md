---
title: Images
parent: Usage Guide
nav_order: 7
---

# Images

Embed bitmap images in your PDF with standard Compose `Image` composables.

---

![Images example]({{ site.baseurl }}/assets/images/06-images.png){: .rounded .shadow .mb-4 }
*Images in PDF example — [download PDF]({{ site.baseurl }}/assets/pdfs/06-images.pdf)*

---

## Basic image

```kotlin
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.painter.BitmapPainter

val bitmap = loadImageBitmap() // Your ImageBitmap

renderToPdf {
    Image(
        bitmap = bitmap,
        contentDescription = "Photo",
        modifier = Modifier.size(200.dp, 150.dp),
    )
}
```

---

## Creating images programmatically

You can create `ImageBitmap` from Skia surfaces:

```kotlin
import org.jetbrains.skia.Surface as SkSurface
import org.jetbrains.skia.Paint as SkPaint
import org.jetbrains.skia.Color as SkColor
import org.jetbrains.skia.Rect as SkRect

val bitmap = run {
    val surface = SkSurface.makeRasterN32Premul(128, 128)
    surface.canvas.drawRect(
        SkRect.makeWH(128f, 128f),
        SkPaint().apply { color = SkColor.makeRGB(25, 118, 210) },
    )
    surface.makeImageSnapshot().toComposeImageBitmap()
}
```

---

## Image sizing

Control the rendered size with `Modifier.size()`:

```kotlin
Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
    Image(BitmapPainter(bitmap), "Small", Modifier.size(48.dp))
    Image(BitmapPainter(bitmap), "Medium", Modifier.size(80.dp))
    Image(BitmapPainter(bitmap), "Large", Modifier.size(128.dp))
}
```

---

## Circle-clipped images (avatars)

```kotlin
Image(
    painter = BitmapPainter(bitmap),
    contentDescription = "Avatar",
    modifier = Modifier
        .size(64.dp)
        .clip(CircleShape),
)
```

---

## Images with text layout

Combine images and text in standard Compose layouts:

```kotlin
Row(
    horizontalArrangement = Arrangement.spacedBy(16.dp),
    verticalAlignment = Alignment.CenterVertically,
) {
    Image(
        painter = BitmapPainter(bitmap),
        contentDescription = "Avatar",
        modifier = Modifier.size(64.dp).clip(CircleShape),
    )
    Column {
        Text("Jane Smith", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text("Product Designer", fontSize = 12.sp, color = Color.Gray)
    }
}
```

---

## How images are embedded

- **Vector mode:** Images are captured as base64-encoded data during SVG generation, then decoded and embedded as lossless PDF image objects via PDFBox's `LosslessFactory`.
- **Raster mode:** The entire page (including images) is rendered as a single bitmap and embedded as one PDF image.

---

{: .tip }
For the smallest file sizes with image-heavy documents, use vector mode. Each image is embedded at its natural resolution. In raster mode, the entire page is one bitmap at the render density.

---

## See also

- [Shapes and Drawing]({{ site.baseurl }}/usage/shapes-and-drawing) -- Clipping and backgrounds
- [Vector vs Raster]({{ site.baseurl }}/usage/vector-vs-raster) -- How images differ between modes
