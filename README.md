# compose2pdf

Render [Compose Desktop](https://www.jetbrains.com/compose-multiplatform/) content directly to PDF.

```kotlin
val pdfBytes = renderToPdf {
    Text("Hello, PDF!")
}
File("hello.pdf").writeBytes(pdfBytes)
```

## Features

- **Vector PDF output** — text is selectable, scales to any zoom level
- **Raster fallback** — pixel-perfect rendering as an embedded image
- **Font embedding** — bundled Inter fonts or system font resolution with automatic subsetting
- **Link annotations** — clickable URLs in the PDF
- **Multi-page** — render multiple pages in a single PDF
- **Page presets** — A4, Letter, A3 with configurable margins

## Installation

```kotlin
// build.gradle.kts
dependencies {
    implementation("com.chrisjenx:compose2pdf:0.1.0")
}
```

Requires JDK 17+ and Compose Desktop.

## Usage

### Single page

```kotlin
val pdf = renderToPdf(
    config = PdfPageConfig.A4,
    density = Density(2f),
    mode = RenderMode.VECTOR,
) {
    Column(Modifier.padding(24.dp)) {
        Text("Invoice #1234", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text("Amount: $1,250.00")
    }
}
```

### Multi-page

```kotlin
val pdf = renderToPdf(pages = 3) { pageIndex ->
    Text("Page ${pageIndex + 1}")
}
```

### Links

```kotlin
PdfLink(href = "https://example.com") {
    Text("Click me", color = Color.Blue)
}
```

### PDF-safe rounded corners

```kotlin
// Non-uniform radii that render correctly in PDF
Box(Modifier.clip(PdfRoundedCornerShape(topStart = 24.dp, bottomEnd = 24.dp)))
```

## API Reference

| Function / Type | Description |
|---|---|
| `renderToPdf(config, density, mode, defaultFontFamily) { content }` | Single page PDF |
| `renderToPdf(pages, config, density, mode, defaultFontFamily) { pageIndex -> content }` | Multi-page PDF |
| `PdfLink(href) { content }` | Clickable link annotation |
| `PdfRoundedCornerShape(topStart, topEnd, bottomEnd, bottomStart)` | PDF-safe rounded corners |
| `Shape.asPdfSafe()` | Wrap any shape for correct PDF rendering |
| `PdfPageConfig.A4` / `.Letter` / `.A3` | Page size presets |
| `PdfMargins(top, bottom, left, right)` | Page margins |
| `RenderMode.VECTOR` / `.RASTER` | Rendering mode |

## How it works

```
Compose content → Skia PictureRecorder → SVGCanvas → SVG string
    → SvgToPdfConverter → PDFBox vector drawing commands → PDF ByteArray
```

Vector mode preserves text as selectable glyphs with embedded fonts. Raster mode captures a bitmap and embeds it as a PDF image.

## Known limitations

- Depends on `CanvasLayersComposeScene` (`@InternalComposeUiApi`) — no public alternative exists
- Variable fonts are skipped (PDFBox renders them at default axis values); only static `.ttf`/`.otf` are embedded
- SVGCanvas approximates non-uniform rounded rects as bezier paths — use `PdfRoundedCornerShape` for best results

## License

Apache-2.0
