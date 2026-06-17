package com.chrisjenx.compose2pdf

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.apache.pdfbox.Loader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PublicPaginatedColumnTest {

    private val config = PdfPageConfig.A4WithMargins // contentHeight = 698dp

    @Test
    fun `PaginatedColumn produces correct page breaks`() {
        val bytes = renderToPdf(config = config) {
            PaginatedColumn {
                repeat(5) {
                    Spacer(Modifier.fillMaxWidth().height(200.dp))
                }
            }
        }
        Loader.loadPDF(bytes).use { doc ->
            assertTrue(doc.numberOfPages >= 2, "1000dp should overflow 698dp page, got ${doc.numberOfPages}")
        }
    }

    @Test
    fun `PaginatedColumn nested inside user Column restores page breaking`() {
        // Without PaginatedColumn, a Column wrapping content is a single child
        // and auto-pagination sees only one keep-together unit.
        // With PaginatedColumn inside, each spacer becomes a keep-together unit.
        val bytes = renderToPdf(config = config) {
            Column {
                PaginatedColumn {
                    // 600dp + 200dp = 800dp > 698dp page
                    Spacer(Modifier.fillMaxWidth().height(600.dp))
                    Spacer(Modifier.fillMaxWidth().height(200.dp))
                }
            }
        }
        Loader.loadPDF(bytes).use { doc ->
            assertEquals(2, doc.numberOfPages, "Second spacer should be pushed to page 2")
        }
    }

    @Test
    fun `PaginatedColumn inside CompositionLocalProvider`() {
        val local = compositionLocalOf { "default" }
        val bytes = renderToPdf(config = config) {
            CompositionLocalProvider(local provides "custom") {
                PaginatedColumn {
                    repeat(5) {
                        Spacer(Modifier.fillMaxWidth().height(200.dp))
                    }
                }
            }
        }
        Loader.loadPDF(bytes).use { doc ->
            assertTrue(doc.numberOfPages >= 2, "Should paginate inside provider, got ${doc.numberOfPages}")
        }
    }

    @Test
    fun `oversized child flows across pages`() {
        val bytes = renderToPdf(config = config) {
            PaginatedColumn {
                Spacer(Modifier.fillMaxWidth().height(1500.dp))
            }
        }
        Loader.loadPDF(bytes).use { doc ->
            assertTrue(doc.numberOfPages >= 3, "1500dp should span 3+ pages of 698dp, got ${doc.numberOfPages}")
        }
    }

    @Test
    fun `works with RASTER mode`() {
        val bytes = renderToPdf(config = config, mode = RenderMode.RASTER) {
            PaginatedColumn {
                repeat(5) {
                    Spacer(Modifier.fillMaxWidth().height(200.dp))
                }
            }
        }
        Loader.loadPDF(bytes).use { doc ->
            assertTrue(doc.numberOfPages >= 2, "Should paginate in RASTER mode, got ${doc.numberOfPages}")
        }
    }

    @Test
    fun `content that fits on one page produces 1-page PDF`() {
        val bytes = renderToPdf(config = config) {
            PaginatedColumn {
                Spacer(Modifier.fillMaxWidth().height(100.dp))
            }
        }
        Loader.loadPDF(bytes).use { doc ->
            assertEquals(1, doc.numberOfPages)
        }
    }

    @Test
    fun `works with SINGLE_PAGE mode`() {
        // PaginatedColumn inside SINGLE_PAGE should not crash — it acts as a simple
        // vertical stack since auto-pagination is disabled
        val bytes = renderToPdf(config = config, pagination = PdfPagination.SINGLE_PAGE) {
            PaginatedColumn {
                repeat(5) {
                    Spacer(Modifier.fillMaxWidth().height(200.dp))
                }
            }
        }
        Loader.loadPDF(bytes).use { doc ->
            assertEquals(1, doc.numberOfPages, "SINGLE_PAGE should clip to 1 page")
        }
    }

    @Test
    fun `works with manual multi-page renderToPdf`() {
        val bytes = renderToPdf(pages = 2, config = config) { pageIndex ->
            PaginatedColumn {
                Text("Page ${pageIndex + 1}")
                Spacer(Modifier.fillMaxWidth().height(100.dp))
            }
        }
        Loader.loadPDF(bytes).use { doc ->
            assertEquals(2, doc.numberOfPages)
        }
    }

    @Test
    fun `invoice pattern with PaginatedColumn inside wrapper`() {
        val bytes = renderToPdf(config = config) {
            // Simulates a user theme/style provider wrapping content
            Column(Modifier.padding(8.dp)) {
                PaginatedColumn {
                    // Header
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text("INVOICE #12345")
                        Text("Company Name Inc.")
                    }
                    Spacer(Modifier.height(8.dp))
                    // Line items — enough to overflow
                    for (i in 1..25) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                            Text("Item $i", Modifier.weight(1f))
                            Text("$${i * 100}.00")
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    // Summary
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text("Total: $2500.00")
                    }
                }
            }
        }
        Loader.loadPDF(bytes).use { doc ->
            assertTrue(doc.numberOfPages >= 2, "Invoice should overflow to 2+ pages, got ${doc.numberOfPages}")
        }
    }
}
