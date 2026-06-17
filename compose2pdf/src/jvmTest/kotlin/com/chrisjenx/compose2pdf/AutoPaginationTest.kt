package com.chrisjenx.compose2pdf

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.apache.pdfbox.Loader
import java.util.logging.Handler
import java.util.logging.LogRecord
import java.util.logging.Logger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AutoPaginationTest {

    private val config = PdfPageConfig.A4WithMargins // contentHeight = 698dp

    @Test
    fun `content that fits one page produces 1-page PDF`() {
        val bytes = renderToPdf(config = config) {
            Text("Hello, single page!")
        }
        Loader.loadPDF(bytes).use { doc ->
            assertEquals(1, doc.numberOfPages)
        }
    }

    @Test
    fun `content overflows to multiple pages - vector`() {
        val bytes = renderToPdf(config = config, mode = RenderMode.VECTOR) {
            // Each spacer is 200dp, 5 of them = 1000dp > 698dp contentHeight
            repeat(5) {
                Spacer(Modifier.fillMaxWidth().height(200.dp))
            }
        }
        Loader.loadPDF(bytes).use { doc ->
            assertTrue(doc.numberOfPages >= 2, "Should have at least 2 pages, got ${doc.numberOfPages}")
        }
    }

    @Test
    fun `content overflows to multiple pages - raster`() {
        val bytes = renderToPdf(config = config, mode = RenderMode.RASTER) {
            repeat(5) {
                Spacer(Modifier.fillMaxWidth().height(200.dp))
            }
        }
        Loader.loadPDF(bytes).use { doc ->
            assertTrue(doc.numberOfPages >= 2, "Should have at least 2 pages, got ${doc.numberOfPages}")
        }
    }

    @Test
    fun `SINGLE_PAGE clips to 1 page even with overflow`() {
        val bytes = renderToPdf(config = config, pagination = PdfPagination.SINGLE_PAGE) {
            repeat(10) {
                Spacer(Modifier.fillMaxWidth().height(200.dp))
            }
        }
        Loader.loadPDF(bytes).use { doc ->
            assertEquals(1, doc.numberOfPages)
        }
    }

    @Test
    fun `smart page breaking pushes element to next page`() {
        // Page contentHeight = 698dp.
        // First child: 600dp (fits on page 1)
        // Second child: 200dp (600 + 200 = 800 > 698, pushed to page 2)
        val bytes = renderToPdf(config = config) {
            Spacer(Modifier.fillMaxWidth().height(600.dp))
            Spacer(Modifier.fillMaxWidth().height(200.dp))
        }
        Loader.loadPDF(bytes).use { doc ->
            assertEquals(2, doc.numberOfPages, "Second element should be pushed to page 2")
        }
    }

    @Test
    fun `oversized single child flows across pages`() {
        // One child at 1500dp > 698dp contentHeight — must span pages
        val bytes = renderToPdf(config = config) {
            Spacer(Modifier.fillMaxWidth().height(1500.dp))
        }
        Loader.loadPDF(bytes).use { doc ->
            assertTrue(doc.numberOfPages >= 3, "1500dp should span at least 3 pages of 698dp, got ${doc.numberOfPages}")
        }
    }

    @Test
    fun `empty content produces 1-page PDF`() {
        val bytes = renderToPdf(config = config) {
            // intentionally empty
        }
        Loader.loadPDF(bytes).use { doc ->
            assertEquals(1, doc.numberOfPages)
        }
    }

    @Test
    fun `fillMaxSize content produces 1-page PDF`() {
        val bytes = renderToPdf(config = config) {
            Box(Modifier.fillMaxSize()) {
                Text("Fills entire page")
            }
        }
        Loader.loadPDF(bytes).use { doc ->
            // fillMaxSize should fill the tall scene → fallback to 1 page
            assertEquals(1, doc.numberOfPages)
        }
    }

    @Test
    fun `produces valid PDF bytes`() {
        val bytes = renderToPdf(config = config) {
            repeat(5) {
                Spacer(Modifier.fillMaxWidth().height(200.dp))
            }
        }
        val header = bytes.copyOfRange(0, 5).toString(Charsets.US_ASCII)
        assertTrue(header.startsWith("%PDF"), "Should produce valid PDF, got header: $header")
    }

    @Test
    fun `many small elements fit correctly`() {
        // 20 items at 30dp each = 600dp total < 698dp → should fit in 1 page
        val bytes = renderToPdf(config = config) {
            repeat(20) {
                Spacer(Modifier.fillMaxWidth().height(30.dp))
            }
        }
        Loader.loadPDF(bytes).use { doc ->
            assertEquals(1, doc.numberOfPages, "600dp of content should fit on 1 page of 698dp")
        }
    }

    @Test
    fun `auto pagination logs warning when page count exceeds max`() {
        val logs = mutableListOf<String>()
        val handler = object : Handler() {
            override fun publish(record: LogRecord) { logs.add(record.message) }
            override fun flush() {}
            override fun close() {}
        }
        val logger = Logger.getLogger("com.chrisjenx.compose2pdf.internal.PdfRenderer")
        logger.addHandler(handler)
        try {
            // Use a tiny page config so content easily exceeds 100 pages
            // contentHeight ~20dp, 100+ pages of 20dp = 2000dp+ needed
            val tinyConfig = PdfPageConfig(
                width = 100.dp, height = 30.dp, margins = PdfMargins(top = 5.dp, bottom = 5.dp),
            )
            // 120 spacers at 20dp = 2400dp total / 20dp per page = 120 pages > 100 max
            renderToPdf(config = tinyConfig, density = Density(1f)) {
                repeat(120) {
                    Spacer(Modifier.fillMaxWidth().height(20.dp))
                }
            }
            assertTrue(
                logs.any { it.contains("truncated") || it.contains("exceeded") || it.contains("max") },
                "Should log warning about page truncation, got logs: $logs"
            )
        } finally {
            logger.removeHandler(handler)
        }
    }

    @Test
    fun `content spanning exactly 3 pages`() {
        // 698dp per page. 3 items at 698dp each = 3 pages exactly
        val bytes = renderToPdf(config = config) {
            repeat(3) {
                Spacer(Modifier.fillMaxWidth().height(698.dp))
            }
        }
        Loader.loadPDF(bytes).use { doc ->
            assertEquals(3, doc.numberOfPages, "3 page-sized elements should produce 3 pages")
        }
    }
}
