package com.chrisjenx.compose2pdf

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.apache.pdfbox.Loader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HeaderFooterRasterTest {

    private val config = PdfPageConfig.A4WithMargins
    private val mode = RenderMode.RASTER

    private fun pagePixel(bytes: ByteArray, pageIndex: Int, y: Int): Int =
        pagePixel(bytes, pageIndex, y, config)

    private val redHeader: @Composable (PdfPageInfo) -> Unit = {
        Box(Modifier.fillMaxWidth().height(40.dp).background(Color.Red))
    }
    private val blueFooter: @Composable (PdfPageInfo) -> Unit = {
        Box(Modifier.fillMaxWidth().height(30.dp).background(Color.Blue))
    }

    @Test
    fun `raster slots are stamped on every page with correct band geometry`() {
        val received = mutableListOf<Pair<Int, Int>>()
        val bytes = renderToPdf(config = config, mode = mode, header = redHeader, footer = { info ->
            received += info.pageIndex to info.pageCount
            Box(Modifier.fillMaxWidth().height(30.dp).background(Color.Blue))
        }) {
            // 12 x 200dp children; bands fit within the 72pt margins, so effective page = 698dp
            // (unchanged from a no-slot doc) -> 3/page -> 4 pages
            repeat(12) { Spacer(Modifier.fillMaxWidth().height(200.dp)) }
        }
        val pageCount = Loader.loadPDF(bytes).use { it.numberOfPages }
        assertEquals(4, pageCount)
        for (page in 0 until pageCount) {
            assertTrue(pagePixel(bytes, page, 38).isRed(), "page $page: header band should be red")
            assertTrue(pagePixel(bytes, page, 809).isBlue(), "page $page: footer band should be blue")
            assertTrue(page to 4 in received, "footer should have been composed with ($page, 4)")
        }
    }

    @Test
    fun `raster partial last slice with bands is not stretched`() {
        // Effective page = 698dp (bands fit inside the margins, body area unchanged).
        // 942dp body = 1.5 effective pages: page 2 holds 942-698 = 244dp.
        val bytes = renderToPdf(config = config, mode = mode, header = redHeader, footer = blueFooter) {
            Box(Modifier.fillMaxWidth().height(942.dp).background(Color(0xFF9E9E9E)))
        }
        assertEquals(2, Loader.loadPDF(bytes).use { it.numberOfPages })
        // Page 2 body band spans y=72..770; its content (244dp) ends at y=72+244=316.
        assertTrue(pagePixel(bytes, 1, 200).isGray(), "page 2: top of body band should have content")
        assertFalse(pagePixel(bytes, 1, 600).isGray(), "page 2: lower body band must be blank, not stretched")
        assertTrue(pagePixel(bytes, 1, 809).isBlue(), "page 2: footer still stamped")
    }

    @Test
    fun `raster single page gets slots with pageCount 1`() {
        val received = mutableListOf<Pair<Int, Int>>()
        val bytes = renderToPdf(config = config, mode = mode, header = redHeader, footer = { info ->
            received += info.pageIndex to info.pageCount
            Box(Modifier.fillMaxWidth().height(30.dp).background(Color.Blue))
        }) {
            Text("Short content")
        }
        assertEquals(1, Loader.loadPDF(bytes).use { it.numberOfPages })
        assertTrue(pagePixel(bytes, 0, 38).isRed())
        assertTrue(pagePixel(bytes, 0, 809).isBlue())
        assertTrue(0 to 1 in received)
    }

    @Test
    fun `raster SINGLE_PAGE pagination stamps slots`() {
        val bytes = renderToPdf(
            config = config, mode = mode, pagination = PdfPagination.SINGLE_PAGE,
            header = redHeader, footer = blueFooter,
        ) {
            Box(Modifier.fillMaxWidth().height(2000.dp).background(Color(0xFF9E9E9E)))
        }
        assertEquals(1, Loader.loadPDF(bytes).use { it.numberOfPages })
        assertTrue(pagePixel(bytes, 0, 38).isRed(), "header band must not be overdrawn by body")
        assertTrue(pagePixel(bytes, 0, 809).isBlue(), "footer band must not be overdrawn by body")
    }
}
