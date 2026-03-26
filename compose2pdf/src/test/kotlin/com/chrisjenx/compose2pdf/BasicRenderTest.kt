package com.chrisjenx.compose2pdf

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.apache.pdfbox.Loader
import androidx.compose.ui.unit.Density
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class BasicRenderTest {

    @Test
    fun `renderToPdf produces valid PDF bytes`() {
        val bytes = renderToPdf {
            Box(Modifier.fillMaxSize().background(Color.White)) {
                Text("Hello, PDF!")
            }
        }
        assertTrue(bytes.isNotEmpty(), "PDF bytes should not be empty")
        val header = bytes.copyOfRange(0, 5).toString(Charsets.US_ASCII)
        assertTrue(header.startsWith("%PDF"), "PDF should start with %PDF header, got: $header")
    }

    @Test
    fun `renderToPdf with custom page config`() {
        val config = PdfPageConfig.Letter
        val bytes = renderToPdf(config = config) {
            Text("US Letter page")
        }
        assertTrue(bytes.isNotEmpty())
        assertTrue(bytes.copyOfRange(0, 5).toString(Charsets.US_ASCII).startsWith("%PDF"))
    }

    @Test
    fun `renderToPdf with margins`() {
        val config = PdfPageConfig.A4.copy(margins = PdfMargins.Narrow)
        val bytes = renderToPdf(config = config) {
            Text("Page with margins")
        }
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun `multi-page renderToPdf`() {
        val bytes = renderToPdf(pages = 3) { pageIndex ->
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                Text("Page ${pageIndex + 1}", fontSize = 24.sp)
                Text("This is page content for page ${pageIndex + 1}")
            }
        }
        assertTrue(bytes.isNotEmpty())
        Loader.loadPDF(bytes).use { doc ->
            assertEquals(3, doc.numberOfPages, "PDF should contain 3 pages")
        }
    }

    @Test
    fun `renderToPdf with zero pages throws`() {
        try {
            renderToPdf(pages = 0) { Text("nope") }
            assertTrue(false, "Should have thrown")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("positive"))
        }
    }

    @Test
    fun `renderToPdf with zero density throws`() {
        assertFailsWith<IllegalArgumentException> {
            renderToPdf(density = Density(0f)) { Text("boom") }
        }
    }

    @Test
    fun `renderToPdf with negative density throws`() {
        assertFailsWith<IllegalArgumentException> {
            renderToPdf(density = Density(-1f)) { Text("boom") }
        }
    }

    @Test
    fun `multi-page renderToPdf with zero density throws`() {
        assertFailsWith<IllegalArgumentException> {
            renderToPdf(pages = 1, density = Density(0f)) { Text("boom") }
        }
    }
}
