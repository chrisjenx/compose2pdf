package com.chrisjenx.compose2pdf

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.apache.pdfbox.Loader
import org.apache.pdfbox.rendering.PDFRenderer
import java.awt.Color
import kotlin.test.Test
import kotlin.test.assertTrue
import org.jetbrains.skia.Image as SkiaImage

/**
 * Regression test for the manual multi-page vector path
 * ([com.chrisjenx.compose2pdf.internal.PdfRenderer.renderVector], reached via the public
 * `renderToPdf(pages, mode = RenderMode.VECTOR) { pageIndex -> ... }` API).
 *
 * Each manual page is rendered as its OWN [com.chrisjenx.compose2pdf.internal.ComposeToSvg]
 * SVG document, and Skia's SVGCanvas restarts element ids (`img0`, ...) from zero for every
 * separate document. `renderVector` must therefore give each page its own `imageCache` — a
 * cache shared across pages would let page 2's `img0` wrongly resolve to page 1's cached
 * image (a real collision for e.g. a photo-per-page document).
 *
 * This test renders a 2-page document with a DIFFERENT solid-color image per page — page 0
 * red, page 1 blue — and asserts page 1 shows its own blue image rather than page 0's cached
 * red one.
 */
class ManualMultiPageImageTest {

    @Test
    fun `manual multi-page vector render does not share images across pages`() {
        val redBitmap = SkiaImage.makeFromEncoded(solidColorPngBytes(Color.RED)).toComposeImageBitmap()
        val blueBitmap = SkiaImage.makeFromEncoded(solidColorPngBytes(Color.BLUE)).toComposeImageBitmap()

        val bytes = renderToPdf(pages = 2, mode = RenderMode.VECTOR) { pageIndex ->
            Image(
                painter = BitmapPainter(if (pageIndex == 0) redBitmap else blueBitmap),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
            )
        }

        Loader.loadPDF(bytes).use { doc ->
            val renderer = PDFRenderer(doc)

            val page0Image = renderer.renderImageWithDPI(0, 72f)
            val page0Rgb = page0Image.getRGB(page0Image.width / 2, page0Image.height / 2)
            assertTrue(
                page0Rgb.isRed(),
                "page 0 should show its own red image, got ${Integer.toHexString(page0Rgb)}",
            )

            val page1Image = renderer.renderImageWithDPI(1, 72f)
            val page1Rgb = page1Image.getRGB(page1Image.width / 2, page1Image.height / 2)
            assertTrue(
                page1Rgb.isBlue(),
                "page 1 should show its own blue image, not page 0's cached red image " +
                    "(id collision), got ${Integer.toHexString(page1Rgb)}",
            )
        }
    }
}
