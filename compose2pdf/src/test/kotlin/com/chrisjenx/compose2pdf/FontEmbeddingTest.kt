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

    /**
     * Byte-patches the bundled Inter's name table, replacing "Inter" with "Qnter" in both
     * ASCII and UTF-16BE name records, producing a structurally valid font with an unknown
     * family name.
     */
    private fun renameInterTo_Qnter(): ByteArray {
        val bytes = Thread.currentThread().contextClassLoader
            .getResourceAsStream("fonts/Inter-Regular.ttf")!!.readBytes()
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
        val asciiNew = "Qnter".toByteArray(Charsets.US_ASCII)
        val utf16 = "Inter".toByteArray(Charsets.UTF_16BE)
        val utf16New = "Qnter".toByteArray(Charsets.UTF_16BE)
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
