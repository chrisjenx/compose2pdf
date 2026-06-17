package com.chrisjenx.compose2pdf

import com.chrisjenx.compose2pdf.internal.SvgPathParser
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Tests for SvgPathParser. Since parse() writes directly to PDPageContentStream,
 * we verify that parsing completes without exceptions and emits content stream bytes.
 */
class SvgPathParserTest {

    /** Parses an SVG path string into a real PDF content stream and returns the page bytes. */
    private fun parsePath(d: String): ByteArray {
        val doc = PDDocument()
        try {
            val page = PDPage(PDRectangle(100f, 100f))
            doc.addPage(page)
            val cs = PDPageContentStream(doc, page)
            cs.use {
                SvgPathParser.parse(d, it)
            }
            val baos = java.io.ByteArrayOutputStream()
            doc.save(baos)
            return baos.toByteArray()
        } finally {
            doc.close()
        }
    }

    /** Verifies parsing completes without error and produces a valid PDF. */
    private fun assertParses(d: String) {
        val bytes = parsePath(d)
        assertTrue(bytes.isNotEmpty(), "PDF bytes should be non-empty for path '$d'")
        val header = bytes.copyOfRange(0, 5).toString(Charsets.US_ASCII)
        assertTrue(header.startsWith("%PDF"), "Should produce valid PDF for path '$d'")
    }

    // --- Basic absolute commands ---

    @Test
    fun `moveTo and lineTo`() {
        assertParses("M10,20 L30,40")
    }

    @Test
    fun `horizontal lineTo`() {
        assertParses("M10,20 H50")
    }

    @Test
    fun `vertical lineTo`() {
        assertParses("M10,20 V60")
    }

    @Test
    fun `close path`() {
        assertParses("M10,10 L50,10 L50,50 Z")
    }

    @Test
    fun `triangle path`() {
        assertParses("M10,10 L50,50 L10,50 Z")
    }

    // --- Relative commands ---

    @Test
    fun `relative moveTo and lineTo`() {
        assertParses("m10,20 l30,40")
    }

    @Test
    fun `relative horizontal`() {
        assertParses("m10,20 h40")
    }

    @Test
    fun `relative vertical`() {
        assertParses("m10,20 v40")
    }

    @Test
    fun `relative close`() {
        assertParses("m10,10 l40,0 l0,40 z")
    }

    // --- Cubic Bezier ---

    @Test
    fun `absolute cubic bezier`() {
        assertParses("M10,10 C20,20,40,20,50,10")
    }

    @Test
    fun `relative cubic bezier`() {
        assertParses("M10,10 c10,10,30,10,40,0")
    }

    @Test
    fun `smooth cubic bezier`() {
        assertParses("M10,10 C20,20,40,20,50,10 S70,20,80,10")
    }

    @Test
    fun `relative smooth cubic bezier`() {
        assertParses("M10,10 c10,10,30,10,40,0 s20,10,30,0")
    }

    // --- Quadratic Bezier ---

    @Test
    fun `absolute quadratic bezier`() {
        assertParses("M10,10 Q30,30,50,10")
    }

    @Test
    fun `relative quadratic bezier`() {
        assertParses("M10,10 q20,20,40,0")
    }

    @Test
    fun `smooth quadratic bezier`() {
        assertParses("M10,10 Q30,30,50,10 T90,10")
    }

    @Test
    fun `relative smooth quadratic bezier`() {
        assertParses("M10,10 q20,20,40,0 t40,0")
    }

    // --- Arcs ---

    @Test
    fun `absolute arc`() {
        assertParses("M10,80 A25,25,0,0,1,50,80")
    }

    @Test
    fun `relative arc`() {
        assertParses("M10,80 a25,25,0,0,1,40,0")
    }

    @Test
    fun `arc with large-arc flag`() {
        assertParses("M10,80 A25,25,0,1,1,50,80")
    }

    @Test
    fun `arc with rotation`() {
        assertParses("M10,80 A25,15,45,0,1,50,80")
    }

    @Test
    fun `degenerate arc same point is no-op`() {
        // Same start and end point — should be a no-op (line 166 of SvgPathParser)
        assertParses("M10,80 A25,25,0,0,1,10,80")
    }

    @Test
    fun `degenerate arc zero radius draws line`() {
        assertParses("M10,80 A0,0,0,0,1,50,80")
    }

    // --- Implicit repetition ---

    @Test
    fun `implicit lineTo after moveTo`() {
        // After M, subsequent coordinate pairs become L commands
        assertParses("M10,20 30,40 50,60")
    }

    @Test
    fun `implicit relative lineTo after relative moveTo`() {
        assertParses("m10,20 30,40 50,60")
    }

    @Test
    fun `implicit repeat of lineTo`() {
        assertParses("M0,0 L10,10 20,20 30,30")
    }

    // --- Edge cases ---

    @Test
    fun `empty path string`() {
        assertParses("")
    }

    @Test
    fun `single moveTo only`() {
        assertParses("M10,20")
    }

    @Test
    fun `single Z only`() {
        assertParses("Z")
    }

    @Test
    fun `space separated coordinates`() {
        assertParses("M 10 20 L 30 40")
    }

    @Test
    fun `negative coordinates`() {
        assertParses("M-10,-20 L-30,-40")
    }

    @Test
    fun `mixed positive and negative`() {
        assertParses("M10,-20 L-30,40")
    }

    @Test
    fun `decimal coordinates`() {
        assertParses("M10.5,20.3 L30.7,40.1")
    }

    @Test
    fun `complex path with multiple command types`() {
        assertParses("M10,10 L50,10 Q70,30,50,50 T10,50 C5,40,5,20,10,10 Z")
    }
}
