---
title: RenderMode
parent: API Reference
nav_order: 5
---

# RenderMode

Controls how Compose content is rendered to PDF.

```kotlin
enum class RenderMode {
    VECTOR,
    RASTER,
}
```

{: .important }
The `RenderMode` parameter is only accepted by the **JVM** `renderToPdf` API. Android always produces vector output via `android.graphics.pdf.PdfDocument`'s Skia-backed Canvas. iOS always produces vector output via Core Graphics. The enum is defined in `commonMain` but is not a parameter on Android or iOS.

---

## Values

### VECTOR

Vector rendering via Skia SVGCanvas. Produces smaller PDFs with selectable text and crisp scaling.

The rendering pipeline: Compose -> Skia PictureRecorder -> SVGCanvas -> SVG string -> SvgToPdfConverter -> PDFBox vector commands -> PDF.

### RASTER

Raster rendering via ImageComposeScene. Produces pixel-perfect output as an embedded image.

The rendering pipeline: Compose -> ImageComposeScene -> BufferedImage -> PDFBox embedded image -> PDF.

---

## Usage (JVM only)

```kotlin
// Vector (default)
val pdf = renderToPdf(mode = RenderMode.VECTOR) { content() }

// Raster
val pdf = renderToPdf(mode = RenderMode.RASTER, density = Density(3f)) { content() }
```

---

## See also

- [Usage: Vector vs Raster]({{ site.baseurl }}/usage/vector-vs-raster) -- Detailed comparison and decision guide (JVM)
