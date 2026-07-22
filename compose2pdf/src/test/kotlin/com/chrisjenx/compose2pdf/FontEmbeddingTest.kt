package com.chrisjenx.compose2pdf

import androidx.compose.foundation.layout.Column
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.unit.sp
import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.font.PDType0Font
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The PDF must embed the exact fonts Compose shaped the text with — for any FontFamily,
 * automatically. Glyph positions in the SVG come from the shaping font, so substituting a
 * metric-incompatible font (the old standard-14 Helvetica fallback) squashes/overlaps
 * letters. See ComposeFontStack/TypefaceCaptureRegistry/SkiaTypefaceEmbedder.
 */
class FontEmbeddingTest {

    /** All fonts used on the page, as (baseName, isEmbedded). */
    private fun pageFonts(pdf: ByteArray): List<Pair<String, Boolean>> =
        Loader.loadPDF(pdf).use { doc ->
            doc.pages.flatMap { page ->
                page.resources.fontNames.map { name ->
                    val font = page.resources.getFont(name)
                    (font.name ?: "?") to font.isEmbedded
                }
            }
        }

    @Test
    fun `system default font is embedded, not substituted`() {
        val pdf = renderToPdf(config = PdfPageConfig.A4WithMargins) {
            MaterialTheme {
                Text("Default font: Late Fee - 2.5% (\$34.69) + \$5.00 fixed", style = TextStyle(fontSize = 16.sp))
            }
        }
        val fonts = pageFonts(pdf)
        assertTrue(fonts.isNotEmpty(), "expected at least one font resource")
        assertTrue(
            fonts.all { it.second },
            "expected every font embedded (shaping font captured), got $fonts",
        )
    }

    @Test
    fun `custom file font unknown to the system is embedded`() {
        // Simulate a user's brand font: bundled Inter renamed (in its name table) to a
        // family that exists neither as a bundled font nor anywhere on the system, loaded
        // through Compose's file-font API exactly like a consumer would.
        val renamed = File.createTempFile("c2p-custom", ".ttf").apply { deleteOnExit() }
        renamed.writeBytes(renameInterTo_Qnter())
        val customFamily = FontFamily(Font(file = renamed))

        val pdf = renderToPdf(config = PdfPageConfig.A4WithMargins) {
            MaterialTheme {
                Column {
                    Text(
                        "Custom font: Late Fee - 2.5% (\$34.69) + \$5.00 fixed",
                        style = TextStyle(fontFamily = customFamily, fontSize = 16.sp),
                    )
                }
            }
        }
        val fonts = pageFonts(pdf)
        assertTrue(
            fonts.any { it.first.contains("Qnter") && it.second },
            "expected the renamed custom font embedded, got $fonts",
        )
        assertTrue(fonts.all { it.second }, "no substituted standard fonts expected, got $fonts")
    }

    @Test
    fun `bundled Inter still resolves through the bundled path`() {
        val pdf = renderToPdf(config = PdfPageConfig.A4WithMargins) {
            Text("Inter: Late Fee - 2.5% (\$34.69)", style = TextStyle(fontFamily = InterFontFamily, fontSize = 16.sp))
        }
        val fonts = pageFonts(pdf)
        assertTrue(
            fonts.any { it.first.contains("Inter-Regular") && it.second },
            "expected bundled Inter embedded, got $fonts",
        )
    }

    @Test
    fun `two bold-range weights of one family embed the fallback file only once`() {
        // Bold (700) and ExtraBold (800) both collapse to Inter-Bold. Distinct cache keys
        // must not each load a separate subset of the identical file into the document.
        val cache = mutableMapOf<String, org.apache.pdfbox.pdmodel.font.PDFont>()
        org.apache.pdfbox.pdmodel.PDDocument().use { doc ->
            val bold = com.chrisjenx.compose2pdf.internal.FontResolver.resolve(doc, cache, "Inter", "700", null)
            val extraBold = com.chrisjenx.compose2pdf.internal.FontResolver.resolve(doc, cache, "Inter", "800", null)
            assertTrue(bold.name?.contains("Bold") == true, "expected Inter-Bold, got ${bold.name}")
            kotlin.test.assertSame(bold, extraBold, "both bold-range weights must share one embedded font")
        }
    }

    @Test
    fun `a second render is not shadowed by a prior render's font of the same family name`() {
        // Two documents both use family "Zoxca" for physically different fonts (Inter's
        // Regular vs Bold, renamed). The second render must embed ITS font, not the one the
        // first render captured under the same family name.
        val regularAsZoxca = File.createTempFile("c2p-zoxca-reg", ".ttf")
            .apply { deleteOnExit(); writeBytes(renameFont("fonts/Inter-Regular.ttf", "Zoxca")) }
        val boldAsZoxca = File.createTempFile("c2p-zoxca-bold", ".ttf")
            .apply { deleteOnExit(); writeBytes(renameFont("fonts/Inter-Bold.ttf", "Zoxca")) }

        fun render(file: File) = renderToPdf(config = PdfPageConfig.A4WithMargins) {
            MaterialTheme {
                Text("Wave WWW", style = TextStyle(fontFamily = FontFamily(Font(file = file)), fontSize = 20.sp))
            }
        }

        // First render captures the Regular-derived "Zoxca"; second uses the Bold-derived one.
        render(regularAsZoxca)
        val secondPdf = render(boldAsZoxca)

        // The embedded "Zoxca" in the second PDF must carry Bold metrics, not the Regular
        // ones the first render would have shadowed in with.
        val embeddedW = Loader.loadPDF(secondPdf).use { doc ->
            val page = doc.getPage(0)
            val name = page.resources.fontNames.first { page.resources.getFont(it).name?.contains("Zoxca") == true }
            page.resources.getFont(name).getStringWidth("W")
        }
        val regularW = PDDocumentWidth("fonts/Inter-Regular.ttf")
        val boldW = PDDocumentWidth("fonts/Inter-Bold.ttf")
        assertTrue(regularW != boldW, "test premise: Inter Regular and Bold have different 'W' widths")
        kotlin.test.assertEquals(boldW, embeddedW, "second render must embed its own (Bold) font, got W=$embeddedW (regular=$regularW, bold=$boldW)")
    }

    private fun PDDocumentWidth(resource: String): Float =
        org.apache.pdfbox.pdmodel.PDDocument().use { doc ->
            val bytes = Thread.currentThread().contextClassLoader.getResourceAsStream(resource)!!.readBytes()
            PDType0Font.load(doc, bytes.inputStream()).getStringWidth("W")
        }

    @Test
    fun `reconstructed typeface has identical metrics to the source font`() {
        val interBytes = Thread.currentThread().contextClassLoader
            .getResourceAsStream("fonts/Inter-Regular.ttf")!!.readBytes()
        val typeface = org.jetbrains.skia.FontMgr.default
            .makeFromData(org.jetbrains.skia.Data.makeFromBytes(interBytes))!!
        org.apache.pdfbox.pdmodel.PDDocument().use { doc ->
            val direct = PDType0Font.load(doc, interBytes.inputStream())
            val embedded = com.chrisjenx.compose2pdf.internal.SkiaTypefaceEmbedder.embed(doc, typeface)
            assertTrue(embedded != null, "expected typeface to embed")
            for (ch in listOf("%", "a", " ", "(", "W", ".")) {
                kotlin.test.assertEquals(
                    direct.getStringWidth(ch), embedded.getStringWidth(ch),
                    "width mismatch for '$ch'",
                )
            }
        }
    }

    private fun renameInterTo_Qnter(): ByteArray = renameFont("fonts/Inter-Regular.ttf", "Qnter")

    /**
     * Byte-patches [resource]'s name table, replacing "Inter" with [to] (which must be 5
     * chars, matching "Inter") in both ASCII and UTF-16BE name records, producing a
     * structurally valid font that reports an arbitrary family name.
     */
    private fun renameFont(resource: String, to: String): ByteArray {
        require(to.length == 5) { "replacement must be 5 chars to match 'Inter'" }
        val bytes = Thread.currentThread().contextClassLoader
            .getResourceAsStream(resource)!!.readBytes()
        val numTables = ((bytes[4].toInt() and 0xFF) shl 8) or (bytes[5].toInt() and 0xFF)
        var nameOffset = -1
        var nameLength = -1
        for (i in 0 until numTables) {
            val entry = 12 + i * 16
            val tag = String(bytes, entry, 4, Charsets.US_ASCII)
            if (tag == "name") {
                nameOffset = readU32(bytes, entry + 8)
                nameLength = readU32(bytes, entry + 12)
            }
        }
        check(nameOffset > 0) { "name table not found" }
        val ascii = "Inter".toByteArray(Charsets.US_ASCII)
        val asciiNew = to.toByteArray(Charsets.US_ASCII)
        val utf16 = "Inter".toByteArray(Charsets.UTF_16BE)
        val utf16New = to.toByteArray(Charsets.UTF_16BE)
        var i = nameOffset
        while (i < nameOffset + nameLength - utf16.size) {
            if (matches(bytes, i, utf16)) {
                utf16New.copyInto(bytes, i); i += utf16.size
            } else if (matches(bytes, i, ascii)) {
                asciiNew.copyInto(bytes, i); i += ascii.size
            } else i++
        }
        return bytes
    }

    private fun readU32(b: ByteArray, at: Int): Int =
        ((b[at].toInt() and 0xFF) shl 24) or ((b[at + 1].toInt() and 0xFF) shl 16) or
            ((b[at + 2].toInt() and 0xFF) shl 8) or (b[at + 3].toInt() and 0xFF)

    private fun matches(b: ByteArray, at: Int, pattern: ByteArray): Boolean {
        for (j in pattern.indices) if (b[at + j] != pattern[j]) return false
        return true
    }
}
