package com.chrisjenx.compose2pdf

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.apache.pdfbox.Loader
import org.apache.pdfbox.rendering.PDFRenderer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class HeaderFooterVectorTest {

    private val config = PdfPageConfig.A4WithMargins // page 595x842pt, margins 72pt, content 451x698pt
    private val mode = RenderMode.VECTOR

    // Band geometry at 72 DPI (1pt = 1px), y measured from page top. Bands anchor to the
    // physical page edge (18pt inset) rather than the body margin; body area is unchanged at
    // [72, 770]pt since the bands (edge inset + band height + gap) fit within the 72pt margins:
    // header band: [18, 58] (40dp header, 18pt inset from the top edge) — sample y=38
    // footer band: [794, 824] (30dp footer, 18pt inset from the bottom edge) — sample y=809
    private fun pagePixel(bytes: ByteArray, pageIndex: Int, y: Int): Int {
        Loader.loadPDF(bytes).use { doc ->
            val img = PDFRenderer(doc).renderImageWithDPI(pageIndex, 72f)
            val x = (config.margins.left.value + config.contentWidth.value / 2).toInt()
            return img.getRGB(x, y)
        }
    }

    private fun Int.isRed() = ((this shr 16) and 0xFF) > 200 && ((this shr 8) and 0xFF) < 80
    private fun Int.isBlue() = (this and 0xFF) > 200 && ((this shr 16) and 0xFF) < 80

    private val redHeader: @Composable (PdfPageInfo) -> Unit = {
        Box(Modifier.fillMaxWidth().height(40.dp).background(Color.Red))
    }
    private val blueFooter: @Composable (PdfPageInfo) -> Unit = {
        Box(Modifier.fillMaxWidth().height(30.dp).background(Color.Blue))
    }

    /** 12 x 200dp children; bands fit within the 72pt margins, so effective page = 698dp
     *  (unchanged from a no-slot doc) -> 3 children/page -> 4 pages. */
    private val multiPageBody: @Composable () -> Unit = {
        repeat(12) { Spacer(Modifier.fillMaxWidth().height(200.dp)) }
    }

    @Test
    fun `slots are stamped on every page with correct band geometry`() {
        val bytes = renderToPdf(config = config, mode = mode, header = redHeader, footer = blueFooter) {
            multiPageBody()
        }
        val pageCount = Loader.loadPDF(bytes).use { it.numberOfPages }
        assertEquals(4, pageCount)
        for (page in 0 until pageCount) {
            assertTrue(pagePixel(bytes, page, 38).isRed(), "page $page: header band should be red")
            assertTrue(pagePixel(bytes, page, 809).isBlue(), "page $page: footer band should be blue")
        }
    }

    @Test
    fun `slots receive pageIndex and pageCount for every page`() {
        val received = mutableListOf<Pair<Int, Int>>()
        renderToPdf(config = config, mode = mode, footer = { info ->
            received += info.pageIndex to info.pageCount
            Text("Page ${info.pageNumber} of ${info.pageCount}")
        }) {
            multiPageBody()
        }
        // Measurement composes a (0, 2) sentinel; stamping must compose the real values.
        for (i in 0 until 4) {
            assertTrue(i to 4 in received, "footer should have been composed with ($i, 4); got $received")
        }
    }

    @Test
    fun `single-page content still gets slots with pageCount 1`() {
        val received = mutableListOf<Pair<Int, Int>>()
        val bytes = renderToPdf(config = config, mode = mode, header = redHeader, footer = { info ->
            received += info.pageIndex to info.pageCount
            Box(Modifier.fillMaxWidth().height(30.dp).background(Color.Blue))
        }) {
            Text("Short content")
        }
        assertEquals(1, Loader.loadPDF(bytes).use { it.numberOfPages })
        assertTrue(pagePixel(bytes, 0, 38).isRed(), "header should be stamped on the single page")
        assertTrue(pagePixel(bytes, 0, 809).isBlue(), "footer should be stamped on the single page")
        assertTrue(0 to 1 in received, "footer should have been composed with (0, 1); got $received")
    }

    @Test
    fun `SINGLE_PAGE pagination stamps slots and clips body to effective area`() {
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

    @Test
    fun `body content never overlaps the bands`() {
        // Gray body fills every page fully; bands must still win their reserved space
        val bytes = renderToPdf(config = config, mode = mode, header = redHeader, footer = blueFooter) {
            repeat(4) { Box(Modifier.fillMaxWidth().height(628.dp).background(Color(0xFF9E9E9E))) }
        }
        val pageCount = Loader.loadPDF(bytes).use { it.numberOfPages }
        for (page in 0 until pageCount) {
            assertTrue(pagePixel(bytes, page, 38).isRed(), "page $page: header area is reserved")
            assertTrue(pagePixel(bytes, page, 809).isBlue(), "page $page: footer area is reserved")
        }
    }

    @Test
    fun `zero-height slot reserves no band and is skipped`() {
        // Footer composes nothing -> measures 0px -> no band, page math identical to no-footer
        val bytes = renderToPdf(config = config, mode = mode, footer = { /* renders nothing */ }) {
            // 698dp effective page (no band): 3 x 200dp fit, 4th pushed -> 12 children = 4 pages
            repeat(12) { Spacer(Modifier.fillMaxWidth().height(200.dp)) }
        }
        assertEquals(4, Loader.loadPDF(bytes).use { it.numberOfPages })
    }

    @Test
    fun `slots too tall for the page throw IllegalArgumentException`() {
        assertFailsWith<IllegalArgumentException> {
            renderToPdf(config = config, mode = mode, header = {
                Box(Modifier.fillMaxWidth().fillMaxHeight())
            }) {
                Text("body")
            }
        }
    }

    @Test
    fun `PdfLink in footer produces an annotation on every page inside the band`() {
        val bytes = renderToPdf(config = config, mode = mode, footer = {
            PdfLink("https://example.com") { Text("example.com") }
        }) {
            multiPageBody()
        }
        Loader.loadPDF(bytes).use { doc ->
            // Footer band sits near the bottom page edge, 18pt inset above it: footer top
            // (top-down) = 842-18-30=794, band 30pt tall, bottom edge at 824.
            // PDF y-up (bottom-left origin): band spans [842-824, 842-794] = [18, 48]
            for (i in 0 until doc.numberOfPages) {
                val annotations = doc.getPage(i).annotations
                assertTrue(annotations.isNotEmpty(), "page $i should have a link annotation")
                val rect = annotations.first().rectangle
                assertTrue(
                    rect.lowerLeftY >= 6f && rect.upperRightY <= 66f,
                    "page $i: link rect should sit in the footer band, was [${rect.lowerLeftY}, ${rect.upperRightY}]",
                )
            }
        }
    }

    @Test
    fun `paginated column inside providers breaks at the effective page height`() {
        // Public PaginatedColumn reads LocalPdfPageConfig; with slots it must see the
        // effective content height (here unchanged, since the bands fit inside the margins)
        // or children split across boundaries.
        val bytes = renderToPdf(config = config, mode = mode, header = redHeader, footer = blueFooter) {
            Box { // hides children from the automatic outer PaginatedColumn
                PaginatedColumn {
                    repeat(12) { Spacer(Modifier.fillMaxWidth().height(200.dp)) }
                }
            }
        }
        // Same math as multiPageBody: 3 x 200dp per 698dp effective page -> 4 pages
        assertEquals(4, Loader.loadPDF(bytes).use { it.numberOfPages })
    }
}
