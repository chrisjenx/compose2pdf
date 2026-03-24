# compose2pdf

[![CI](https://github.com/nickhall-ck/compose2pdf/actions/workflows/ci.yml/badge.svg)](https://github.com/nickhall-ck/compose2pdf/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](https://www.apache.org/licenses/LICENSE-2.0)

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
- **Link annotations** — clickable URLs in the PDF via `PdfLink`
- **Multi-page** — render multiple pages in a single PDF
- **Page presets** — A4, Letter, A3 with configurable margins and landscape support

## Installation

```kotlin
// build.gradle.kts
dependencies {
    implementation("com.chrisjenx:compose2pdf:0.1.0")
}
```

Requires **JDK 17+** and **Compose Desktop** (Compose Multiplatform 1.9+).

## Quick start

### Single page

```kotlin
val pdf = renderToPdf(
    config = PdfPageConfig.LetterWithMargins,
    mode = RenderMode.VECTOR,
) {
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Text("Invoice #1234", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text("Amount: $1,250.00")
    }
}
```

### Multi-page

```kotlin
val pdf = renderToPdf(pages = 3) { pageIndex ->
    Column(Modifier.fillMaxSize()) {
        Text("Page ${pageIndex + 1}")
    }
}
```

### Links

```kotlin
PdfLink(href = "https://example.com") {
    Text("Click me", color = Color.Blue, textDecoration = TextDecoration.Underline)
}
```

## Documentation

**[Full documentation](https://nickhall-ck.github.io/compose2pdf/)** — getting started, usage guides, API reference, examples, and more.

- [Getting Started](https://nickhall-ck.github.io/compose2pdf/getting-started)
- [Usage Guide](https://nickhall-ck.github.io/compose2pdf/usage/)
- [API Reference](https://nickhall-ck.github.io/compose2pdf/api/)
- [Examples](https://nickhall-ck.github.io/compose2pdf/examples/)
- [Troubleshooting](https://nickhall-ck.github.io/compose2pdf/guides/troubleshooting)

## License

Apache-2.0
