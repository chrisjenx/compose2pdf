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

---

## Values

### VECTOR

Vector rendering via Skia SVGCanvas. Produces smaller PDFs with selectable text and crisp scaling.

The rendering pipeline: Compose -> Skia PictureRecorder -> SVGCanvas -> SVG string -> SvgToPdfConverter -> PDFBox vector commands -> PDF.

### RASTER

Raster rendering via ImageComposeScene. Produces pixel-perfect output as an embedded image.

The rendering pipeline: Compose -> ImageComposeScene -> BufferedImage -> PDFBox embedded image -> PDF.

---

## Usage

```kotlin
// Vector (default)
val pdf = renderToPdf(mode = RenderMode.VECTOR) { content() }

// Raster
val pdf = renderToPdf(mode = RenderMode.RASTER, density = Density(3f)) { content() }
```

---

## See also

- [Usage: Vector vs Raster]({{ site.baseurl }}/usage/vector-vs-raster) -- Detailed comparison and decision guide
