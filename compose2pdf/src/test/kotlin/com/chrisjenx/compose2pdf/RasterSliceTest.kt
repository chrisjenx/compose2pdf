package com.chrisjenx.compose2pdf

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.apache.pdfbox.Loader
import org.apache.pdfbox.rendering.PDFRenderer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RasterSliceTest {

    private val config = PdfPageConfig.A4WithMargins // contentHeight = 698pt

    private fun pixelAt(bytes: ByteArray, pageIndex: Int, frac: Float): Int {
        Loader.loadPDF(bytes).use { doc ->
            val img = PDFRenderer(doc).renderImageWithDPI(pageIndex, 72f)
            val x = (config.margins.left.value + config.contentWidth.value / 2).toInt()
            val y = (config.margins.top.value + config.contentHeight.value * frac).toInt()
            return img.getRGB(x, y)
        }
    }

    private fun Int.isRed() = ((this shr 16) and 0xFF) > 200 && ((this shr 8) and 0xFF) < 80

    @Test
    fun `last partial raster slice is not stretched`() {
        // 1047dp = 1.5 pages: page 2 holds 349dp of content (top half); the rest must stay blank
        val bytes = renderToPdf(config = config, mode = RenderMode.RASTER) {
            Box(Modifier.fillMaxWidth().height(1047.dp).background(Color.Red))
        }
        Loader.loadPDF(bytes).use { doc -> assertEquals(2, doc.numberOfPages) }
        assertTrue(pixelAt(bytes, 1, 0.25f).isRed(), "top half of page 2 should contain content")
        assertFalse(pixelAt(bytes, 1, 0.75f).isRed(), "bottom half of page 2 must be blank, not stretched content")
        assertFalse(pixelAt(bytes, 1, 0.95f).isRed(), "bottom of page 2 must be blank, not stretched content")
    }

    @Test
    fun `full raster slices keep exact content-area geometry`() {
        // 1396dp = exactly 2 full pages; both pages fully covered
        val bytes = renderToPdf(config = config, mode = RenderMode.RASTER) {
            Box(Modifier.fillMaxWidth().height(1396.dp).background(Color.Red))
        }
        for (page in 0..1) {
            assertTrue(pixelAt(bytes, page, 0.05f).isRed(), "page $page top should be red")
            assertTrue(pixelAt(bytes, page, 0.95f).isRed(), "page $page bottom should be red")
        }
    }
}
