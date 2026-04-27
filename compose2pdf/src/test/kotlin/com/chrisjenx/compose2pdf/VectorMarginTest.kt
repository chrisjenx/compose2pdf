package com.chrisjenx.compose2pdf

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.apache.pdfbox.Loader
import org.apache.pdfbox.rendering.PDFRenderer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VectorMarginTest {

    @Test
    fun `vector PDF respects page margins for single-page render`() {
        val config = PdfPageConfig(
            width = 200.dp,
            height = 200.dp,
            margins = PdfMargins(top = 50.dp, bottom = 50.dp, left = 50.dp, right = 50.dp),
        )
        val bytes = renderToPdf(
            config = config,
            density = Density(1f),
            mode = RenderMode.VECTOR,
            pagination = PdfPagination.SINGLE_PAGE,
        ) {
            Box(Modifier.fillMaxSize().background(Color.Red))
        }

        Loader.loadPDF(bytes).use { doc ->
            val image = PDFRenderer(doc).renderImageWithDPI(0, 72f) // 1pt == 1px
            assertEquals(200, image.width)
            assertEquals(200, image.height)

            // Top-left corner inside margin region (top=50, left=50) → must be white.
            assertWhite(image, 10, 10, "top-left margin")
            assertWhite(image, 49, 49, "just inside top-left margin boundary")

            // Center of page is inside content area → must be red.
            assertRed(image, 100, 100, "content centre")

            // Just inside content boundary on each side → must be red.
            assertRed(image, 51, 51, "top-left content corner")
            assertRed(image, 148, 148, "bottom-right content corner")

            // Outer corners outside content → must be white.
            assertWhite(image, 10, 190, "bottom-left margin")
            assertWhite(image, 190, 10, "top-right margin")
            assertWhite(image, 190, 190, "bottom-right margin")
        }
    }

    @Test
    fun `vector PDF respects asymmetric margins for manual multi-page render`() {
        val config = PdfPageConfig(
            width = 200.dp,
            height = 200.dp,
            margins = PdfMargins(top = 30.dp, bottom = 60.dp, left = 20.dp, right = 40.dp),
        )
        val bytes = renderToPdf(
            pages = 2,
            config = config,
            density = Density(1f),
            mode = RenderMode.VECTOR,
        ) { _ ->
            Box(Modifier.fillMaxSize().background(Color.Red))
        }

        Loader.loadPDF(bytes).use { doc ->
            assertEquals(2, doc.numberOfPages)
            for (pageIndex in 0 until 2) {
                val image = PDFRenderer(doc).renderImageWithDPI(pageIndex, 72f)
                // top margin: rows 0..29 white
                assertWhite(image, 100, 5, "page=$pageIndex top margin")
                assertWhite(image, 100, 29, "page=$pageIndex top margin edge")
                // left margin: cols 0..19 white at content-y
                assertWhite(image, 5, 100, "page=$pageIndex left margin")
                // right margin: cols 160..199 white (200 - 40 = 160)
                assertWhite(image, 165, 100, "page=$pageIndex right margin")
                // bottom margin: rows 140..199 white (200 - 60 = 140)
                assertWhite(image, 100, 145, "page=$pageIndex bottom margin")
                // content area centre red
                assertRed(image, 90, 80, "page=$pageIndex content centre")
            }
        }
    }

    private fun assertWhite(image: java.awt.image.BufferedImage, x: Int, y: Int, label: String) {
        val rgb = image.getRGB(x, y) and 0xFFFFFF
        assertTrue(
            isWhitish(rgb),
            "Expected white at ($x,$y) [$label], got ${"%06X".format(rgb)}"
        )
    }

    private fun assertRed(image: java.awt.image.BufferedImage, x: Int, y: Int, label: String) {
        val rgb = image.getRGB(x, y) and 0xFFFFFF
        val r = (rgb shr 16) and 0xFF
        val g = (rgb shr 8) and 0xFF
        val b = rgb and 0xFF
        assertTrue(
            r > 200 && g < 60 && b < 60,
            "Expected red at ($x,$y) [$label], got ${"%06X".format(rgb)}"
        )
    }

    private fun isWhitish(rgb: Int): Boolean {
        val r = (rgb shr 16) and 0xFF
        val g = (rgb shr 8) and 0xFF
        val b = rgb and 0xFF
        return r > 240 && g > 240 && b > 240
    }
}
