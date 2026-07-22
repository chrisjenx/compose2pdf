package com.chrisjenx.compose2pdf

import com.chrisjenx.compose2pdf.internal.PageLayout
import com.chrisjenx.compose2pdf.internal.SvgToPdfConverter
import org.apache.pdfbox.contentstream.operator.Operator
import org.apache.pdfbox.cos.COSNumber
import org.apache.pdfbox.cos.COSString
import org.apache.pdfbox.pdfparser.PDFStreamParser
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * When the SVG's shaping font can't be embedded (e.g. system default fonts like
 * ".SF NS" on macOS or Roboto on Linux servers), FontResolver falls back to a
 * standard-14 font whose glyph widths differ from the shaping font's advances.
 * Glyphs wider than their per-glyph x-position slot must be horizontally
 * compressed (Tz) so they can't collide with the following glyph.
 */
class FontFallbackMetricsTest {

    /** Parses the page content stream into (tzValues, tjCount) for inspection. */
    private fun horizontalScalingOps(page: PDPage): List<Float> {
        val tokens = PDFStreamParser(page).parse()
        val tz = mutableListOf<Float>()
        for (i in tokens.indices) {
            val tok = tokens[i]
            if (tok is Operator && tok.name == "Tz") {
                val v = tokens[i - 1]
                if (v is COSNumber) tz.add(v.floatValue())
            }
        }
        return tz
    }

    @Test
    fun `a very wide glyph in a tight slot is compressed below 50 percent to avoid collision`() {
        // 'W' at font-size 40 in Helvetica is 0.944em = 37.76pt wide, but the layout only
        // reserves a 12pt slot before the next glyph. The compression must shrink it to fit
        // (~32%), not floor at 50% — a 50% floor would leave it ~18.9pt, overrunning the slot.
        val svg = """<svg xmlns="http://www.w3.org/2000/svg" width="300" height="60">
            <text x="10.0, 22.0, 34.0" y="40" font-size="40"
                  font-family="NoSuchFontFamilyXyz">WWW</text>
        </svg>"""
        PDDocument().use { doc ->
            SvgToPdfConverter.addPage(doc, svg, PageLayout.full(300f, 60f), density = 1f)
            val tz = horizontalScalingOps(doc.getPage(0))
            assertTrue(
                tz.any { it < 50f },
                "Expected a Tz below 50% so the wide glyph fits its slot, got $tz",
            )
            // slot 12pt / natural 37.76pt ≈ 32%
            assertTrue(tz.any { it in 28f..36f }, "Expected 'W' compressed to ~32%, got $tz")
        }
    }

    @Test
    fun `fallback font compresses glyphs wider than their shaping slot`() {
        // x positions simulate Roboto advances at 19.5pt: '%' slot is 14.28pt but
        // standard-14 Helvetica draws '%' at 0.889em = 17.33pt — must be compressed.
        val svg = """<svg xmlns="http://www.w3.org/2000/svg" width="300" height="50">
            <text x="10.0, 20.96, 35.24, 40.08, 46.76" y="30" font-size="19.5"
                  font-family="NoSuchFontFamilyXyz">5% (4</text>
        </svg>"""
        PDDocument().use { doc ->
            SvgToPdfConverter.addPage(doc, svg, PageLayout.full(300f, 50f), density = 1f)
            val tz = horizontalScalingOps(doc.getPage(0))
            assertTrue(
                tz.any { it < 99f },
                "Expected at least one Tz < 100 compressing an overflowing glyph, got $tz",
            )
            // The '%' glyph slot is 14.28pt vs 17.33pt natural width → scale ≈ 82%.
            assertTrue(
                tz.any { it in 78f..87f },
                "Expected '%' compressed to ~82%, got $tz",
            )
        }
    }

    @Test
    fun `matched embedded font output has no horizontal scaling`() {
        // Bundled Inter resolves and matches the shaping font — output must be
        // untouched (fidelity guarantee), even when slots are tighter than natural
        // widths (kerning).
        val svg = """<svg xmlns="http://www.w3.org/2000/svg" width="300" height="50">
            <text x="10.0, 18.0, 26.0, 34.0, 42.0" y="30" font-size="16"
                  font-family="Inter">Hello</text>
        </svg>"""
        PDDocument().use { doc ->
            SvgToPdfConverter.addPage(doc, svg, PageLayout.full(300f, 50f), density = 1f)
            val tz = horizontalScalingOps(doc.getPage(0))
            assertTrue(tz.isEmpty(), "Expected no Tz operators for a matched embedded font, got $tz")
        }
    }
}
