package com.chrisjenx.compose2pdf

import org.apache.pdfbox.Loader
import org.apache.pdfbox.rendering.PDFRenderer
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

/**
 * Shared test helpers for sampling rendered PDF pixels and building solid-color PNG
 * fixtures, deduplicated from per-test-file copies (HeaderFooterRasterTest,
 * HeaderFooterVectorTest, ImageCacheTest, ManualMultiPageImageTest).
 */

/** Rasterizes [pageIndex] at 72 DPI (1pt = 1px) and samples the pixel at content-center x, given [y]. */
fun pagePixel(bytes: ByteArray, pageIndex: Int, y: Int, config: PdfPageConfig): Int {
    Loader.loadPDF(bytes).use { doc ->
        val img = PDFRenderer(doc).renderImageWithDPI(pageIndex, 72f)
        val x = (config.margins.left.value + config.contentWidth.value / 2).toInt()
        return img.getRGB(x, y)
    }
}

fun Int.isRed() =
    ((this shr 16) and 0xFF) > 200 && ((this shr 8) and 0xFF) < 80 && (this and 0xFF) < 80

fun Int.isBlue() =
    (this and 0xFF) > 200 && ((this shr 16) and 0xFF) < 80 && ((this shr 8) and 0xFF) < 80

fun Int.isGray() = ((this shr 16) and 0xFF) in 120..200

/** Encodes a [size]x[size] solid-color PNG. */
fun solidColorPngBytes(color: java.awt.Color, size: Int = 40): ByteArray {
    val image = BufferedImage(size, size, BufferedImage.TYPE_INT_RGB)
    val g = image.createGraphics()
    g.color = color
    g.fillRect(0, 0, size, size)
    g.dispose()
    val out = ByteArrayOutputStream()
    ImageIO.write(image, "png", out)
    return out.toByteArray()
}

/** Asserts [bytes] is a non-trivial PDF (starts with the `%PDF` header). */
fun assertValidPdf(bytes: ByteArray) {
    kotlin.test.assertTrue(bytes.size > 100, "PDF bytes should not be empty, got ${bytes.size}")
    val header = bytes.copyOfRange(0, 5).toString(Charsets.US_ASCII)
    kotlin.test.assertTrue(header.startsWith("%PDF"), "Expected %PDF header, got: $header")
}
