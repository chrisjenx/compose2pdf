---
title: Server-side & Ktor
parent: Guides
nav_order: 5
---

# Server-side Integration

compose2pdf works well for server-side PDF generation. The `OutputStream` overloads let you stream PDFs directly to HTTP responses without buffering the entire document as a `ByteArray`.

---

## Ktor

Use `respondOutputStream` to stream the PDF directly to the HTTP response:

```kotlin
import com.chrisjenx.compose2pdf.*
import io.ktor.http.*
import io.ktor.server.response.*

fun Route.pdfRoutes() {
    get("/report.pdf") {
        call.respondOutputStream(ContentType.Application.Pdf) {
            renderToPdf(this, config = PdfPageConfig.A4WithMargins) {
                ReportContent(loadData())
            }
        }
    }
}
```

### Multi-page with Ktor

```kotlin
get("/invoice/{id}.pdf") {
    val invoice = loadInvoice(call.parameters["id"]!!)
    call.respondOutputStream(ContentType.Application.Pdf) {
        renderToPdf(this, config = PdfPageConfig.LetterWithMargins) {
            InvoiceHeader(invoice)
            ItemsTable(invoice.items)
            TotalSection(invoice)
        }
    }
}
```

### Manual pagination with Ktor

```kotlin
get("/catalog.pdf") {
    val products = loadProducts()
    val pageCount = (products.size + 9) / 10  // 10 per page

    call.respondOutputStream(ContentType.Application.Pdf) {
        renderToPdf(this, pages = pageCount, config = PdfPageConfig.A4WithMargins) { pageIndex ->
            val pageProducts = products.drop(pageIndex * 10).take(10)
            CatalogPage(pageIndex, pageCount, pageProducts)
        }
    }
}
```

---

## ByteArray alternative

If you prefer the `ByteArray` API (simpler, works with any framework):

```kotlin
// Ktor
get("/report.pdf") {
    val bytes = renderToPdf { ReportContent(loadData()) }
    call.respondBytes(bytes, ContentType.Application.Pdf)
}

// Spring Boot
@GetMapping("/report.pdf", produces = ["application/pdf"])
fun report(): ByteArray = renderToPdf { ReportContent(loadData()) }

// Servlet
override fun doGet(req: HttpServletRequest, resp: HttpServletResponse) {
    resp.contentType = "application/pdf"
    renderToPdf(resp.outputStream) { ReportContent() }
}
```

---

## When to use streaming vs ByteArray

| | `OutputStream` (streaming) | `ByteArray` |
|:--|:---------------------------|:------------|
| **Memory** | Avoids extra copy of final PDF bytes | Holds full PDF in memory as byte array |
| **Best for** | Large documents, high-traffic servers | Small documents, simple usage |
| **API** | `renderToPdf(outputStream) { ... }` | `renderToPdf { ... }` returns `ByteArray` |
| **Error handling** | Exceptions thrown before/during write | Exceptions thrown before return |

{: .note }
The `PDDocument` is still built in memory before writing (PDFBox limitation). The streaming API eliminates the extra `ByteArray` copy at serialization time, which matters for large documents.

---

## Concurrent rendering

`renderToPdf` is **not thread-safe**. On a server handling concurrent requests, serialize PDF rendering:

```kotlin
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

val pdfMutex = Mutex()

get("/report.pdf") {
    call.respondOutputStream(ContentType.Application.Pdf) {
        pdfMutex.withLock {
            renderToPdf(this) { ReportContent() }
        }
    }
}
```

For higher throughput, use a dispatcher that limits parallelism:

```kotlin
val pdfDispatcher = Dispatchers.IO.limitedParallelism(1)

get("/report.pdf") {
    withContext(pdfDispatcher) {
        call.respondOutputStream(ContentType.Application.Pdf) {
            renderToPdf(this) { ReportContent() }
        }
    }
}
```

---

## Error handling

Both streaming and ByteArray variants throw `Compose2PdfException` on rendering failure:

```kotlin
get("/report.pdf") {
    try {
        call.respondOutputStream(ContentType.Application.Pdf) {
            renderToPdf(this) { ReportContent() }
        }
    } catch (e: Compose2PdfException) {
        call.respond(HttpStatusCode.InternalServerError, "PDF generation failed: ${e.message}")
    }
}
```

---

## Dependencies

compose2pdf requires a JVM with AWT support. Most server JDKs include this. On headless Linux servers, Compose Desktop requires an X11 display -- use `xvfb-run` for CI or server environments without a display:

```bash
xvfb-run java -jar my-server.jar
```

Or set the `DISPLAY` environment variable:

```bash
export DISPLAY=:99
Xvfb :99 &
java -jar my-server.jar
```

---

## See also

- [API Reference: renderToPdf]({{ site.baseurl }}/api/render-to-pdf) -- Full API signatures including streaming overloads
- [Best Practices]({{ site.baseurl }}/guides/best-practices) -- Density, fonts, and concurrency tips
