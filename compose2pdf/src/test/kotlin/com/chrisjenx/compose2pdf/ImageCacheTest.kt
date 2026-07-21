package com.chrisjenx.compose2pdf

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.dp
import org.apache.pdfbox.Loader
import org.apache.pdfbox.rendering.PDFRenderer
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertTrue
import org.jetbrains.skia.Image as SkiaImage

/**
 * End-to-end regression test for the SVG image cache.
 *
 * [com.chrisjenx.compose2pdf.internal.SvgToPdfConverter] now keys its image cache by the SVG
 * element `id` (O(1)) rather than the full `href` (a potentially multi-MB base64 data URI) —
 * hashing the id is cheap, hashing the href was not. Keying by id alone would risk a
 * cross-document collision: Skia's SVGCanvas restarts element ids (e.g. `img0`) from zero
 * for every separate SVG document, and the with-slots vector render path renders the body
 * and each header/footer band as SEPARATE documents. The fix avoids that collision
 * STRUCTURALLY instead of by cache-key trickery: [com.chrisjenx.compose2pdf.internal.PdfRenderer]
 * gives the body, header, and footer each their OWN `imageCache`
 * (`bodyImageCache`/`headerImageCache`/`footerImageCache`), so a later document's `img0` can
 * never resolve to an earlier document's cached image — while dedup still works within a
 * single document (e.g. a logo repeated many times in a long body, or on every page's
 * header/footer, where the content and id are stable).
 *
 * This test renders a real PDF through the public API with a RED image in the header and a
 * DIFFERENT (BLUE) image as the first body element — both compile down to Skia element id
 * `img0` in their own SVG document — then rasterizes and asserts each band shows its OWN
 * color rather than the other's.
 */
class ImageCacheTest {

    private fun solidColorPngBytes(color: Color, size: Int = 40): ByteArray {
        val image = BufferedImage(size, size, BufferedImage.TYPE_INT_RGB)
        val g = image.createGraphics()
        g.color = color
        g.fillRect(0, 0, size, size)
        g.dispose()
        val out = ByteArrayOutputStream()
        ImageIO.write(image, "png", out)
        return out.toByteArray()
    }

    private fun Int.isRed() =
        ((this shr 16) and 0xFF) > 200 && ((this shr 8) and 0xFF) < 80 && (this and 0xFF) < 80

    private fun Int.isBlue() =
        (this and 0xFF) > 200 && ((this shr 16) and 0xFF) < 80 && ((this shr 8) and 0xFF) < 80

    @Test
    fun `header image and body image do not collide across separate SVG documents`() {
        val redBitmap = SkiaImage.makeFromEncoded(solidColorPngBytes(Color.RED)).toComposeImageBitmap()
        val blueBitmap = SkiaImage.makeFromEncoded(solidColorPngBytes(Color.BLUE)).toComposeImageBitmap()

        // 72pt margins; a 40dp header band (18pt inset + 40pt band + 10pt gap = 68pt) fits
        // inside them, so the body still starts at margins.top (72pt) unchanged.
        val config = PdfPageConfig.A4WithMargins
        val bytes = renderToPdf(
            config = config,
            mode = RenderMode.VECTOR,
            header = {
                Image(
                    painter = BitmapPainter(redBitmap),
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                )
            },
        ) {
            Image(
                painter = BitmapPainter(blueBitmap),
                contentDescription = null,
                modifier = Modifier.size(40.dp),
            )
        }

        Loader.loadPDF(bytes).use { doc ->
            val img = PDFRenderer(doc).renderImageWithDPI(0, 72f) // 1pt = 1px

            // Header band is edge-anchored 18pt from the top; the 40pt-tall band spans
            // [18, 58]pt. Sample well inside it, away from anti-aliased edges.
            val headerX = (config.margins.left.value + 20).toInt()
            val headerRgb = img.getRGB(headerX, 38)
            assertTrue(
                headerRgb.isRed(),
                "header should show its own red image, got ${Integer.toHexString(headerRgb)}",
            )

            // The blue image is the first body element, drawn at the top-left of the content
            // area (margins.left, margins.top).
            val bodyX = (config.margins.left.value + 20).toInt()
            val bodyY = (config.margins.top.value + 20).toInt()
            val bodyRgb = img.getRGB(bodyX, bodyY)
            assertTrue(
                bodyRgb.isBlue(),
                "body should show its own blue image, not the header's cached red image " +
                    "(id collision), got ${Integer.toHexString(bodyRgb)}",
            )
        }
    }
}
