package com.chrisjenx.compose2pdf

import com.chrisjenx.compose2pdf.internal.PageLayout
import com.chrisjenx.compose2pdf.internal.SvgToPdfConverter
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.font.PDFont
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject
import org.apache.pdfbox.rendering.PDFRenderer
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.util.Base64
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Regression test for the SVG image cache colliding across separate SVG documents.
 *
 * Skia's SVGCanvas restarts element ids (e.g. `img0`) from zero for every separate SVG
 * document it emits. In the with-slots vector render path, the body and each header/footer
 * band are rendered as SEPARATE SVG documents that share ONE `imageCache`. Before the fix,
 * [SvgToPdfConverter]'s image decoder cached by element id, so a second document's `img0`
 * would incorrectly resolve to the FIRST document's cached [PDImageXObject] — the wrong
 * image gets stamped. Keying the cache by the image's own `href` (its content, a unique
 * `data:image/...;base64,...` URI) instead of the per-document element id eliminates the
 * collision while still deduping genuinely-identical images.
 *
 * This test renders two SEPARATE SVG documents (mirroring the body/slot split) onto two
 * pages of one [PDDocument] that share a single `imageCache`. Both documents use the SAME
 * element id (`img0`) but embed DIFFERENT image content (red vs. blue). It asserts that
 * page 2 shows its OWN (blue) image rather than page 1's cached (red) one.
 */
class ImageCacheTest {

    private val svgNs = "http://www.w3.org/2000/svg"

    private fun solidColorPngDataUri(color: Color): String {
        val image = BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB)
        val g = image.createGraphics()
        g.color = color
        g.fillRect(0, 0, 8, 8)
        g.dispose()
        val out = ByteArrayOutputStream()
        ImageIO.write(image, "png", out)
        val base64 = Base64.getEncoder().encodeToString(out.toByteArray())
        return "data:image/png;base64,$base64"
    }

    /** Same element id ("img0") as any other document produced by this helper — mirrors Skia's per-document id restart. */
    private fun imageSvg(href: String, size: Int): String =
        """<svg xmlns="$svgNs" width="$size" height="$size"><image id="img0" x="0" y="0" width="$size" height="$size" href="$href"/></svg>"""

    private fun isRed(rgb: Int) =
        ((rgb shr 16) and 0xFF) > 200 && ((rgb shr 8) and 0xFF) < 80 && (rgb and 0xFF) < 80

    private fun isBlue(rgb: Int) =
        (rgb and 0xFF) > 200 && ((rgb shr 16) and 0xFF) < 80 && ((rgb shr 8) and 0xFF) < 80

    @Test
    fun `imageCache keyed by content prevents cross-document collision`() {
        val redHref = solidColorPngDataUri(Color.RED)
        val blueHref = solidColorPngDataUri(Color.BLUE)

        val layout = PageLayout.full(100f, 100f)
        val fontCache = mutableMapOf<String, PDFont>()
        val imageCache = mutableMapOf<String, PDImageXObject>()

        val page1Rgb: Int
        val page2Rgb: Int
        PDDocument().use { doc ->
            // Page 1: a SEPARATE SVG document with a RED image at element id "img0".
            SvgToPdfConverter.addPage(doc, imageSvg(redHref, 100), layout, density = 1f, fontCache, imageCache)
            // Page 2: ANOTHER separate SVG document, SAME element id "img0", DIFFERENT (blue) content.
            SvgToPdfConverter.addPage(doc, imageSvg(blueHref, 100), layout, density = 1f, fontCache, imageCache)

            val renderer = PDFRenderer(doc)
            page1Rgb = renderer.renderImageWithDPI(0, 72f).getRGB(50, 50)
            page2Rgb = renderer.renderImageWithDPI(1, 72f).getRGB(50, 50)
        }

        assertTrue(isRed(page1Rgb), "page 1 should show its own red image, got ${Integer.toHexString(page1Rgb)}")
        assertTrue(
            isBlue(page2Rgb),
            "page 2 should show its own blue image, not page 1's cached red image (id collision), got ${Integer.toHexString(page2Rgb)}",
        )
    }
}
