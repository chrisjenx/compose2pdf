---
title: Getting Started
nav_order: 2
---

# Getting Started

Generate your first PDF from Compose in under 5 minutes.

---

## Prerequisites

- **JDK 17** or later
- A **Compose Desktop** project (Compose Multiplatform)
- **Gradle** build system

If you don't have a Compose Desktop project yet, follow the [JetBrains Compose Multiplatform getting started guide](https://www.jetbrains.com/help/kotlin-multiplatform-dev/compose-multiplatform-getting-started.html).

---

## Add the dependency

### Gradle (Kotlin DSL)

```kotlin
dependencies {
    implementation("com.chrisjenx:compose2pdf:0.1.0")
}
```

### Gradle (Groovy DSL)

```groovy
dependencies {
    implementation 'com.chrisjenx:compose2pdf:0.1.0'
}
```

The library is published to **Maven Central** -- no additional repository configuration is needed.

---

## Your first PDF

Create a Kotlin file (e.g., `GeneratePdf.kt`) and add:

```kotlin
import com.chrisjenx.compose2pdf.renderToPdf
import androidx.compose.foundation.layout.*
import androidx.compose.material.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File

fun main() {
    val pdfBytes = renderToPdf {
        Column(Modifier.fillMaxSize().padding(32.dp)) {
            Text("Hello, PDF!", fontSize = 28.sp)
            Spacer(Modifier.height(8.dp))
            Text(
                "Generated with compose2pdf",
                fontSize = 14.sp,
                color = Color.Gray,
            )
        }
    }
    File("hello.pdf").writeBytes(pdfBytes)
    println("Created hello.pdf (${pdfBytes.size / 1024} KB)")
}
```

Run it, and open `hello.pdf` in any PDF viewer. You'll see:
- "Hello, PDF!" in 28sp text
- "Generated with compose2pdf" in smaller gray text
- Selectable, vector text on an A4 page

### What just happened?

1. `renderToPdf { ... }` takes a `@Composable` lambda -- the same kind you write for Compose UI
2. The library renders your composable through Skia's SVGCanvas, converts the SVG to PDF vector commands via PDFBox, and returns the PDF as a `ByteArray`
3. Default settings: A4 page, no margins, vector mode, Inter font, 2x density

---

## Customizing the output

```kotlin
val pdf = renderToPdf(
    config = PdfPageConfig.LetterWithMargins,   // US Letter with 1" margins
    density = Density(2f),                       // 2x pixel density
    mode = RenderMode.VECTOR,                    // Vector output (default)
) {
    Column(Modifier.fillMaxSize()) {
        Text("Custom configuration", fontSize = 20.sp)
    }
}
```

---

## Next steps

- [Usage Guide]({{ site.baseurl }}/usage/) -- Learn about all the features
- [Multi-page documents]({{ site.baseurl }}/usage/multi-page) -- Render multiple pages
- [Page configuration]({{ site.baseurl }}/usage/page-configuration) -- Page sizes, margins, landscape
- [Examples]({{ site.baseurl }}/examples/) -- Real-world code examples
- [API Reference]({{ site.baseurl }}/api/) -- Full API documentation
