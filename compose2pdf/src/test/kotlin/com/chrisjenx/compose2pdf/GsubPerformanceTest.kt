package com.chrisjenx.compose2pdf

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chrisjenx.compose2pdf.internal.FontResolver
import com.chrisjenx.compose2pdf.internal.SkiaTypefaceEmbedder
import com.chrisjenx.compose2pdf.internal.Type0FontLoader
import org.apache.fontbox.ttf.TTFParser
import org.apache.fontbox.ttf.model.GsubData
import org.apache.pdfbox.io.RandomAccessReadBuffer
import org.apache.pdfbox.Loader
import org.apache.pdfbox.contentstream.operator.Operator
import org.apache.pdfbox.cos.COSString
import org.apache.pdfbox.pdfparser.PDFStreamParser
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.font.PDFont
import org.apache.pdfbox.pdmodel.font.PDType0Font
import org.jetbrains.skia.Data
import org.jetbrains.skia.FontMgr
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Guards against PDFBox re-applying a font's GSUB table to text Skia already shaped.
 *
 * compose2pdf 1.5.0 started embedding the app's real TTFs; PDFBox then parsed each font's
 * GSUB per document and ran its GSUB worker (regex compile per word) on every showText —
 * a 3x slowdown on a 64-invoice render. Text reaches PDFBox already shaped and positioned
 * glyph-by-glyph, so GSUB must never be applied (or even parsed).
 *
 * Inter (bundled, GSUB-bearing) stands in for a consumer's Roboto: no Roboto ships in the
 * repo, and Inter loaded through Compose's file-font API takes exactly the same
 * SkiaTypefaceEmbedder path a consumer's resource/file font does.
 */
class GsubPerformanceTest {

    private fun interBytes(): ByteArray =
        Thread.currentThread().contextClassLoader.getResourceAsStream("fonts/Inter-Regular.ttf")!!.readBytes()

    private fun skiaInter() = FontMgr.default.makeFromData(Data.makeFromBytes(interBytes()))!!

    @Test
    fun `bundled font is embedded without GSUB data`() {
        PDDocument().use { doc ->
            val font = FontResolver.resolve(doc, mutableMapOf(), "Inter", "400", null)
            assertTrue(font is PDType0Font, "expected embedded PDType0Font, got $font")
            assertSame(GsubData.NO_DATA_FOUND, font.gsubData, "PDFBox must not re-apply GSUB to shaped text")
        }
    }

    @Test
    fun `skia typeface is embedded without GSUB data`() {
        PDDocument().use { doc ->
            val font = SkiaTypefaceEmbedder.embed(doc, skiaInter())
            assertTrue(font is PDType0Font, "expected embedded PDType0Font, got $font")
            assertSame(GsubData.NO_DATA_FOUND, font.gsubData, "PDFBox must not re-apply GSUB to shaped text")
        }
    }

    /**
     * The flag alone hides GSUB from showText, but TTFParser still parses the table eagerly
     * (the per-document load cost). The bytes PDFBox parses must not contain GSUB at all.
     */
    @Test
    fun `GSUB table is stripped from font bytes before parsing`() {
        val original = TTFParser().parse(RandomAccessReadBuffer(interBytes()))
        val stripped = TTFParser().parse(RandomAccessReadBuffer(Type0FontLoader.withoutGsub(interBytes())))
        try {
            assertTrue("GSUB" in original.tableMap, "fixture must carry GSUB for this test to discriminate")
            assertFalse("GSUB" in stripped.tableMap, "GSUB must be stripped before PDFBox parses the font")
            assertEquals(original.numberOfGlyphs, stripped.numberOfGlyphs)
            val (o, s) = original.getUnicodeCmapLookup()!! to stripped.getUnicodeCmapLookup()!!
            for (cp in 0x20..0x24F) assertEquals(o.getGlyphId(cp), s.getGlyphId(cp), "cmap mismatch at U+%04X".format(cp))
            for (gid in 0 until original.numberOfGlyphs) assertEquals(original.getAdvanceWidth(gid), stripped.getAdvanceWidth(gid))
        } finally {
            original.close(); stripped.close()
        }
    }

    /**
     * Glyph codes written to the content stream must be the plain cmap mapping — identical to
     * what a GSUB-enabled font produces for the per-glyph showText path compose2pdf uses.
     */
    @Test
    fun `glyph codes and widths unchanged versus GSUB-enabled font`() {
        val samples = listOf(
            "office fluffy affirm", "Late Fee - 2.5% (\$34.69) + \$5.00 fixed", "0123456789",
            "Invoice #INV-2026-0042", "ÀÉÎÕÜ ñ ß æ", "–—‘’“”…",
        )
        fun glyphCodes(load: (PDDocument) -> PDFont): Pair<List<List<Int>>, List<Float>> {
            val pdf = ByteArrayOutputStream()
            val widths = mutableListOf<Float>()
            PDDocument().use { doc ->
                val font = load(doc)
                val page = PDPage().also(doc::addPage)
                PDPageContentStream(doc, page).use { cs ->
                    cs.beginText()
                    cs.setFont(font, 12f)
                    // Per-glyph, exactly like SvgToPdfConverter.renderText
                    for (s in samples) for (ch in s) {
                        cs.showText(ch.toString())
                        widths += font.getStringWidth(ch.toString())
                    }
                    cs.endText()
                }
                doc.save(pdf)
            }
            val codes = Loader.loadPDF(pdf.toByteArray()).use { doc ->
                val tokens = PDFStreamParser(doc.getPage(0)).parse()
                tokens.zipWithNext().filter { (_, op) -> op is Operator && op.name == "Tj" }
                    .map { (arg, _) -> (arg as COSString).bytes.map { it.toInt() and 0xFF } }
            }
            return codes to widths
        }

        val (gsubCodes, gsubWidths) = glyphCodes { doc -> PDType0Font.load(doc, interBytes().inputStream()) }
        val (oursCodes, oursWidths) = glyphCodes { doc -> SkiaTypefaceEmbedder.embed(doc, skiaInter())!! }
        assertEquals(gsubCodes, oursCodes, "glyph codes must match the GSUB-enabled per-glyph output")
        assertEquals(gsubWidths, oursWidths, "glyph widths must match")
    }

    /**
     * Benchmark: a 64-page, text-heavy document (invoice-like rows) with a GSUB-bearing custom
     * file font. Measured on an M-series Mac: ~86s with GSUB intact, ~7.7s fixed. The budget
     * sits ~5x above the fixed time (headroom for slow CI runners) and far below the regressed
     * time. Per-page time is printed for trend-watching.
     */
    @Test
    fun `text-heavy multi-page render with embedded GSUB font meets time budget`() {
        val fontFile = File.createTempFile("c2p-bench", ".ttf").apply { deleteOnExit(); writeBytes(interBytes()) }
        val family = FontFamily(Font(file = fontFile), Font(file = fontFile, weight = FontWeight.Bold))
        val pages = 64

        fun render() = renderToPdf(pages = pages, config = PdfPageConfig.A4WithMargins) { pageIndex ->
            Column(Modifier.padding(4.dp)) {
                Text("Invoice #INV-2026-${pageIndex + 1000}", style = TextStyle(fontFamily = family, fontWeight = FontWeight.Bold, fontSize = 18.sp))
                repeat(40) { row ->
                    Text(
                        "Line $row — Professional services, affiliate office fluff: \$${row * 13}.${row % 100} (2.5% fee)",
                        style = TextStyle(fontFamily = family, fontSize = 10.sp),
                    )
                }
            }
        }

        render() // warm-up: JIT, Skia, font caches
        val start = System.nanoTime()
        val pdf = render()
        val elapsedMs = (System.nanoTime() - start) / 1_000_000
        println("GSUB benchmark: $pages pages in ${elapsedMs}ms (${elapsedMs / pages}ms/page)")

        assertEquals(pages, Loader.loadPDF(pdf).use { it.numberOfPages })
        assertTrue(
            elapsedMs < BUDGET_MS,
            "64-page text render took ${elapsedMs}ms (budget ${BUDGET_MS}ms) — is PDFBox applying GSUB again?",
        )
    }

    private companion object {
        const val BUDGET_MS = 40_000L
    }
}
