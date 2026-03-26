---
title: Usage Guide
nav_order: 3
has_children: true
---

# Usage Guide

compose2pdf lets you render any `@Composable` content to PDF. The core pattern is simple:

```kotlin
// Returns ByteArray
val pdfBytes: ByteArray = renderToPdf {
    // Your Compose content here
}

// Or stream directly to an OutputStream
renderToPdf(outputStream) {
    // Your Compose content here
}
```

Everything you write inside the lambda is standard Compose -- `Column`, `Row`, `Box`, `Text`, `Canvas`, `Image`, and all the modifiers you already know.

---

## Topics

| Topic | What you'll learn |
|:------|:-----------------|
| [Single Page]({{ site.baseurl }}/usage/single-page) | Render a single-page PDF with all configuration options |
| [Multi-page]({{ site.baseurl }}/usage/multi-page) | Create multi-page documents with headers, footers, and page numbers |
| [Auto-pagination]({{ site.baseurl }}/usage/auto-pagination) | Automatic page splitting for flowing content |
| [Page Configuration]({{ site.baseurl }}/usage/page-configuration) | Page sizes (A4, Letter, A3), margins, landscape, custom dimensions |
| [Text and Fonts]({{ site.baseurl }}/usage/text-and-fonts) | Text styling, font weights, decorations, custom fonts |
| [Layout]({{ site.baseurl }}/usage/layout) | Column, Row, Box, weights, alignment, tables |
| [Shapes and Drawing]({{ site.baseurl }}/usage/shapes-and-drawing) | Backgrounds, borders, clips, Canvas drawing, rounded corners |
| [Images]({{ site.baseurl }}/usage/images) | Embedding bitmap images, clipping, sizing |
| [Links]({{ site.baseurl }}/usage/links) | Clickable URL annotations in PDF output |
| [Vector vs Raster]({{ site.baseurl }}/usage/vector-vs-raster) | Choosing between vector and raster rendering modes |
