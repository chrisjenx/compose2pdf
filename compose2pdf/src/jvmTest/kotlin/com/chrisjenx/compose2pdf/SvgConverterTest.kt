package com.chrisjenx.compose2pdf

import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import com.chrisjenx.compose2pdf.internal.SvgToPdfConverter
import org.apache.pdfbox.pdmodel.PDDocument
import java.io.File
import java.util.logging.Handler
import java.util.logging.LogRecord
import java.util.logging.Logger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SvgConverterTest {

    @Test
    fun `rounded corner shape renders as valid vector PDF`() {
        val bytes = renderToPdf(mode = RenderMode.VECTOR) {
            Box(Modifier.fillMaxSize()) {
                Box(
                    Modifier.size(200.dp)
                        .background(Color.Blue, RoundedCornerShape(16.dp))
                )
            }
        }
        assertTrue(bytes.isNotEmpty())
        Loader.loadPDF(bytes).use { doc ->
            assertEquals(1, doc.numberOfPages)
        }
    }

    @Test
    fun `circle shape renders as valid vector PDF`() {
        val bytes = renderToPdf(mode = RenderMode.VECTOR) {
            Box(Modifier.fillMaxSize()) {
                Box(
                    Modifier.size(100.dp)
                        .background(Color.Red, CircleShape)
                )
            }
        }
        assertTrue(bytes.isNotEmpty())
        Loader.loadPDF(bytes).use { doc ->
            assertEquals(1, doc.numberOfPages)
        }
    }

    @Test
    fun `canvas draw operations render in vector PDF`() {
        val bytes = renderToPdf(mode = RenderMode.VECTOR) {
            Canvas(Modifier.size(200.dp)) {
                drawCircle(color = Color.Red, radius = 50f)
                drawLine(
                    color = Color.Blue,
                    start = Offset(0f, 0f),
                    end = Offset(200f, 200f),
                    strokeWidth = 3f,
                )
                drawArc(
                    color = Color.Green,
                    startAngle = 0f,
                    sweepAngle = 180f,
                    useCenter = true,
                    topLeft = Offset(10f, 10f),
                    size = Size(100f, 100f),
                )
            }
        }
        assertTrue(bytes.isNotEmpty())
        Loader.loadPDF(bytes).use { doc ->
            assertEquals(1, doc.numberOfPages)
        }
    }

    @Test
    fun `semi-transparent elements render in vector PDF`() {
        val bytes = renderToPdf(mode = RenderMode.VECTOR) {
            Box(Modifier.fillMaxSize()) {
                Box(
                    Modifier.size(100.dp)
                        .background(Color.Red.copy(alpha = 0.5f))
                )
                Box(
                    Modifier.size(80.dp)
                        .background(Color.Blue.copy(alpha = 0.3f))
                )
            }
        }
        assertTrue(bytes.isNotEmpty())
        Loader.loadPDF(bytes).use { doc ->
            assertEquals(1, doc.numberOfPages)
        }
    }

    @Test
    fun `bordered elements render in vector PDF`() {
        val bytes = renderToPdf(mode = RenderMode.VECTOR) {
            Box(Modifier.fillMaxSize().padding(16.dp)) {
                Box(
                    Modifier.size(150.dp)
                        .border(2.dp, Color.Black, RoundedCornerShape(8.dp))
                        .background(Color.LightGray, RoundedCornerShape(8.dp))
                )
            }
        }
        assertTrue(bytes.isNotEmpty())
        Loader.loadPDF(bytes).use { doc ->
            assertEquals(1, doc.numberOfPages)
        }
    }

    @Test
    fun `stroked canvas shapes render in vector PDF`() {
        val bytes = renderToPdf(mode = RenderMode.VECTOR) {
            Canvas(Modifier.size(200.dp)) {
                drawRect(
                    color = Color.Blue,
                    topLeft = Offset(10f, 10f),
                    size = Size(180f, 180f),
                    style = Stroke(width = 4f),
                )
                drawCircle(
                    color = Color.Red,
                    radius = 60f,
                    style = Stroke(width = 2f),
                )
            }
        }
        assertTrue(bytes.isNotEmpty())
        Loader.loadPDF(bytes).use { doc ->
            assertEquals(1, doc.numberOfPages)
        }
    }

    @Test
    fun `mixed shapes and text render correctly`() {
        val bytes = renderToPdf(mode = RenderMode.VECTOR) {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                Text("Header", fontSize = 24.sp, color = Color.Black)
                Spacer(Modifier.height(8.dp))
                Box(
                    Modifier.size(100.dp)
                        .background(Color.Blue, RoundedCornerShape(8.dp))
                )
                Spacer(Modifier.height(8.dp))
                Box(
                    Modifier.size(50.dp)
                        .background(Color.Red, CircleShape)
                )
                Spacer(Modifier.height(8.dp))
                Box(
                    Modifier.fillMaxWidth().height(2.dp)
                        .background(Color.Gray)
                )
                Spacer(Modifier.height(8.dp))
                Text("Footer", fontSize = 12.sp, color = Color.DarkGray)
            }
        }

        File("build/test-mixed-shapes.pdf").also {
            it.parentFile.mkdirs()
            it.writeBytes(bytes)
        }

        Loader.loadPDF(bytes).use { doc ->
            assertEquals(1, doc.numberOfPages)
            val text = PDFTextStripper().getText(doc)
            assertTrue(text.contains("Header"), "PDF should contain 'Header', got: $text")
            assertTrue(text.contains("Footer"), "PDF should contain 'Footer', got: $text")
        }
    }

    @Test
    fun `multiple colors render in vector PDF`() {
        val bytes = renderToPdf(mode = RenderMode.VECTOR) {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                Box(Modifier.size(50.dp).background(Color.Red))
                Box(Modifier.size(50.dp).background(Color.Green))
                Box(Modifier.size(50.dp).background(Color.Blue))
                Box(Modifier.size(50.dp).background(Color.Yellow))
                Box(Modifier.size(50.dp).background(Color.Cyan))
                Box(Modifier.size(50.dp).background(Color.Magenta))
            }
        }
        assertTrue(bytes.isNotEmpty())
        Loader.loadPDF(bytes).use { doc ->
            assertEquals(1, doc.numberOfPages)
        }
    }

    @Test
    fun `image composable renders in vector PDF`() {
        // Create a simple test bitmap
        val skiaImage = org.jetbrains.skia.Surface.makeRasterN32Premul(64, 64).apply {
            canvas.drawRect(org.jetbrains.skia.Rect.makeWH(64f, 64f), org.jetbrains.skia.Paint().apply {
                color = org.jetbrains.skia.Color.makeRGB(255, 0, 0)
            })
        }.makeImageSnapshot()
        val bitmap = skiaImage.toComposeImageBitmap()

        val bytes = renderToPdf(mode = RenderMode.VECTOR) {
            Box(Modifier.fillMaxSize().padding(16.dp)) {
                Image(
                    painter = BitmapPainter(bitmap),
                    contentDescription = "test image",
                    modifier = Modifier.size(64.dp),
                )
            }
        }

        File("build/test-vector-image.pdf").also {
            it.parentFile.mkdirs()
            it.writeBytes(bytes)
        }

        assertTrue(bytes.isNotEmpty())
        Loader.loadPDF(bytes).use { doc ->
            assertEquals(1, doc.numberOfPages)
        }
    }

    @Test
    fun `PDF link annotations are added to vector PDF`() {
        val config = PdfPageConfig.A4.copy(margins = PdfMargins.Narrow)
        val bytes = renderToPdf(config = config, mode = RenderMode.VECTOR) {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                Text("Regular text", fontSize = 16.sp)
                PdfLink(href = "https://example.com") {
                    Text(
                        "Click here",
                        fontSize = 16.sp,
                        color = Color.Blue,
                    )
                }
                PdfLink(href = "https://github.com") {
                    Text(
                        "GitHub",
                        fontSize = 14.sp,
                        color = Color.Blue,
                    )
                }
            }
        }

        File("build/test-links.pdf").also {
            it.parentFile.mkdirs()
            it.writeBytes(bytes)
        }

        Loader.loadPDF(bytes).use { doc ->
            assertEquals(1, doc.numberOfPages)
            val annotations = doc.getPage(0).annotations
            assertEquals(2, annotations.size, "Should have 2 link annotations")

            val link1 = annotations[0] as org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink
            val link2 = annotations[1] as org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink

            val action1 = link1.action as org.apache.pdfbox.pdmodel.interactive.action.PDActionURI
            val action2 = link2.action as org.apache.pdfbox.pdmodel.interactive.action.PDActionURI

            assertEquals("https://example.com", action1.uri)
            assertEquals("https://github.com", action2.uri)

            // Verify rectangles have positive dimensions
            assertTrue(link1.rectangle.width > 0, "Link 1 width should be positive")
            assertTrue(link1.rectangle.height > 0, "Link 1 height should be positive")
        }
    }

    @Test
    fun `PDF link annotations work in raster mode`() {
        val bytes = renderToPdf(mode = RenderMode.RASTER) {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                PdfLink(href = "https://example.com") {
                    Text("Link in raster", fontSize = 16.sp, color = Color.Blue)
                }
            }
        }

        Loader.loadPDF(bytes).use { doc ->
            val annotations = doc.getPage(0).annotations
            assertEquals(1, annotations.size, "Should have 1 link annotation in raster mode")
        }
    }

    // --- SVG element warning tests ---

    private fun collectLogs(loggerName: String, block: () -> Unit): List<String> {
        val logs = mutableListOf<String>()
        val handler = object : Handler() {
            override fun publish(record: LogRecord) { logs.add(record.message) }
            override fun flush() {}
            override fun close() {}
        }
        val logger = Logger.getLogger(loggerName)
        logger.addHandler(handler)
        try {
            block()
        } finally {
            logger.removeHandler(handler)
        }
        return logs
    }

    private val converterLogger = "com.chrisjenx.compose2pdf.internal.SvgToPdfConverter"

    @Test
    fun `malformed rect width logs warning`() {
        val svg = """<svg xmlns="http://www.w3.org/2000/svg" width="100" height="100">
            <rect x="10" y="10" width="abc" height="50" fill="red"/>
        </svg>"""
        val logs = collectLogs(converterLogger) {
            PDDocument().use { doc ->
                SvgToPdfConverter.addPage(doc, svg, 100f, 100f)
            }
        }
        assertTrue(
            logs.any { "rect" in it.lowercase() && ("width" in it.lowercase() || "skip" in it.lowercase()) },
            "Should warn about malformed rect, got: $logs"
        )
    }

    @Test
    fun `missing circle radius logs warning`() {
        val svg = """<svg xmlns="http://www.w3.org/2000/svg" width="100" height="100">
            <circle cx="50" cy="50" fill="blue"/>
        </svg>"""
        val logs = collectLogs(converterLogger) {
            PDDocument().use { doc ->
                SvgToPdfConverter.addPage(doc, svg, 100f, 100f)
            }
        }
        assertTrue(
            logs.any { "circle" in it.lowercase() || "radius" in it.lowercase() },
            "Should warn about missing circle radius, got: $logs"
        )
    }

    @Test
    fun `missing ellipse rx logs warning`() {
        val svg = """<svg xmlns="http://www.w3.org/2000/svg" width="100" height="100">
            <ellipse cx="50" cy="50" ry="30" fill="green"/>
        </svg>"""
        val logs = collectLogs(converterLogger) {
            PDDocument().use { doc ->
                SvgToPdfConverter.addPage(doc, svg, 100f, 100f)
            }
        }
        assertTrue(
            logs.any { "ellipse" in it.lowercase() || "rx" in it.lowercase() },
            "Should warn about missing ellipse rx, got: $logs"
        )
    }

    @Test
    fun `missing image dimensions logs warning`() {
        val svg = """<svg xmlns="http://www.w3.org/2000/svg" width="100" height="100">
            <image href="data:image/png;base64,iVBOR" fill="red"/>
        </svg>"""
        val logs = collectLogs(converterLogger) {
            PDDocument().use { doc ->
                SvgToPdfConverter.addPage(doc, svg, 100f, 100f)
            }
        }
        assertTrue(
            logs.any { "image" in it.lowercase() && ("width" in it.lowercase() || "dimension" in it.lowercase() || "skip" in it.lowercase()) },
            "Should warn about missing image dimensions, got: $logs"
        )
    }

    // --- Inline style caching correctness ---

    @Test
    fun `elements with inline styles render correctly`() {
        val svg = """<svg xmlns="http://www.w3.org/2000/svg" width="200" height="200">
            <rect x="10" y="10" width="80" height="80" style="fill:red;stroke:blue;stroke-width:2"/>
            <rect x="110" y="10" width="80" height="80" style="fill:green;stroke:none"/>
            <circle cx="50" cy="150" r="30" style="fill:yellow;stroke:black;stroke-width:1"/>
        </svg>"""
        PDDocument().use { doc ->
            SvgToPdfConverter.addPage(doc, svg, 200f, 200f)
            assertEquals(1, doc.numberOfPages)
        }
    }

    @Test
    fun `multiple pages with same SVG produce valid PDF`() {
        val svg = """<svg xmlns="http://www.w3.org/2000/svg" width="100" height="100">
            <rect x="10" y="10" width="80" height="80" fill="red"/>
        </svg>"""
        PDDocument().use { doc ->
            repeat(10) {
                SvgToPdfConverter.addPage(doc, svg, 100f, 100f)
            }
            assertEquals(10, doc.numberOfPages)
        }
    }
}
