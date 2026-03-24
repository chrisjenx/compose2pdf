---
title: Hello World
parent: Examples
nav_order: 1
---

# Hello World

The simplest compose2pdf example -- render text to a PDF in three lines.

---

## Full code

```kotlin
import com.chrisjenx.compose2pdf.renderToPdf
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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

---

## Walkthrough

### 1. Call renderToPdf

```kotlin
val pdfBytes = renderToPdf {
    // Compose content here
}
```

With no parameters, you get:
- **A4** page size (595 x 842 points)
- **No margins** (content fills the full page)
- **Vector** rendering (text is selectable)
- **Inter** font (bundled, auto-embedded)
- **2x** density (good balance of quality and memory)

### 2. Write Compose content

```kotlin
Column(Modifier.fillMaxSize().padding(32.dp)) {
    Text("Hello, PDF!", fontSize = 28.sp)
    Spacer(Modifier.height(8.dp))
    Text("Generated with compose2pdf", fontSize = 14.sp, color = Color.Gray)
}
```

Standard Compose layout -- `Column` stacks items vertically, `Modifier.fillMaxSize()` fills the page, `padding(32.dp)` adds inset from the edges.

### 3. Save the result

```kotlin
File("hello.pdf").writeBytes(pdfBytes)
```

`renderToPdf` returns a `ByteArray` -- write it to a file, send it over HTTP, or process it further.

---

## What the output looks like

- A single A4 page
- "Hello, PDF!" in 28sp black text at the top-left (with 32dp padding)
- "Generated with compose2pdf" in 14sp gray text below
- Text is selectable in any PDF viewer
- File size: typically 15-25 KB

---

## Next steps

- [Add page configuration]({{ site.baseurl }}/usage/page-configuration) -- margins, page size, landscape
- [Multi-page documents]({{ site.baseurl }}/usage/multi-page) -- render multiple pages
- [Professional Invoice]({{ site.baseurl }}/examples/invoice) -- a real-world example
