package com.chrisjenx.compose2pdf

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VectorRenderTest {

    @Test
    fun `vector PDF contains selectable text`() {
        val config = PdfPageConfig.A4.copy(margins = PdfMargins.Normal)
        val bytes = renderToPdf(config = config, mode = RenderMode.VECTOR) {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                Text("Invoice #1234", fontSize = 28.sp, color = Color.Black)
                Text("Amount Due: \$500.00", fontSize = 18.sp, color = Color.DarkGray)
            }
        }

        // Write to disk for manual inspection
        File("build/test-vector.pdf").also {
            it.parentFile.mkdirs()
            it.writeBytes(bytes)
            println("Vector PDF: ${it.absolutePath} (${bytes.size} bytes)")
        }

        assertTrue(bytes.isNotEmpty())

        // Extract text from PDF — should find our content
        Loader.loadPDF(bytes).use { doc ->
            assertEquals(1, doc.numberOfPages)
            val text = PDFTextStripper().getText(doc)
            println("Extracted text: $text")
            assertTrue(text.contains("Invoice"), "PDF text should contain 'Invoice', got: $text")
        }
    }

    @Test
    fun `vector multi-page PDF`() {
        val bytes = renderToPdf(pages = 3, mode = RenderMode.VECTOR) { pageIndex ->
            Box(Modifier.fillMaxSize().background(Color.White)) {
                Column(Modifier.padding(24.dp)) {
                    Text("Page ${pageIndex + 1}", fontSize = 24.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("Content for page ${pageIndex + 1}")
                    Spacer(Modifier.height(16.dp))
                    Box(
                        Modifier.fillMaxWidth().height(4.dp)
                            .background(Color.Blue)
                    )
                }
            }
        }

        File("build/test-vector-multipage.pdf").also {
            it.parentFile.mkdirs()
            it.writeBytes(bytes)
            println("Multi-page vector PDF: ${it.absolutePath} (${bytes.size} bytes)")
        }

        Loader.loadPDF(bytes).use { doc ->
            assertEquals(3, doc.numberOfPages, "Should have 3 pages")
        }
    }

    @Test
    fun `raster PDF is larger than vector PDF`() {
        val content: @androidx.compose.runtime.Composable () -> Unit = {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                Text("Hello World", fontSize = 24.sp)
            }
        }

        val vectorBytes = renderToPdf(mode = RenderMode.VECTOR, content = content)
        val rasterBytes = renderToPdf(
            mode = RenderMode.RASTER,
            density = Density(2f),
            content = content,
        )

        println("Vector PDF: ${vectorBytes.size} bytes")
        println("Raster PDF: ${rasterBytes.size} bytes")

        // Vector should be significantly smaller than raster
        assertTrue(
            vectorBytes.size < rasterBytes.size,
            "Vector (${vectorBytes.size}) should be smaller than raster (${rasterBytes.size})"
        )
    }
}
