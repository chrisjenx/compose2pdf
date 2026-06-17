package com.chrisjenx.compose2pdf

import com.chrisjenx.compose2pdf.internal.CoordinateTransform
import kotlin.test.Test
import kotlin.test.assertEquals

class CoordinateTransformTest {

    private fun assertApprox(expected: Float, actual: Float, msg: String = "") {
        assertEquals(expected, actual, 0.01f, msg)
    }

    // --- svgToPageMatrix ---

    @Test
    fun `svgToPageMatrix flips Y axis`() {
        // SVG point (0, 0) at top-left should map to (0, pageHeight) in PDF
        val m = CoordinateTransform.svgToPageMatrix(1f, 1f, 842f)
        // Matrix: [scaleX, 0, 0, -scaleY, 0, pageHeight]
        // transform(x,y) = (scaleX*x + 0, -scaleY*y + pageHeight)
        val srcX = 0f
        val srcY = 0f
        val pdfX = m.scaleX * srcX + m.translateX
        val pdfY = m.scaleY * srcY + m.translateY
        assertApprox(0f, pdfX, "PDF X for SVG origin")
        assertApprox(842f, pdfY, "PDF Y for SVG origin (should be page height)")
    }

    @Test
    fun `svgToPageMatrix maps bottom of SVG to PDF origin`() {
        val m = CoordinateTransform.svgToPageMatrix(1f, 1f, 842f)
        // SVG point (0, 842) should map to PDF (0, 0)
        val pdfY = m.getValue(1, 1) * 842f + m.translateY
        assertApprox(0f, pdfY, "SVG bottom should map to PDF Y=0")
    }

    @Test
    fun `svgToPageMatrix applies scale`() {
        val m = CoordinateTransform.svgToPageMatrix(2f, 2f, 842f)
        // SVG point (10, 0) should map to PDF (20, 842)
        val pdfX = m.scaleX * 10f + m.translateX
        assertApprox(20f, pdfX, "X should be scaled by 2")
    }

    // --- textCounterFlipMatrix ---

    @Test
    fun `textCounterFlipMatrix produces correct values`() {
        val m = CoordinateTransform.textCounterFlipMatrix(100f)
        // Matrix should be [1, 0, 0, -1, 0, 200]
        assertApprox(1f, m.scaleX)
        assertApprox(-1f, m.getValue(1, 1), "Y scale should be -1 (flip)")
        assertApprox(200f, m.translateY, "translateY should be 2*yOffset")
    }

    @Test
    fun `textCounterFlipMatrix with zero offset`() {
        val m = CoordinateTransform.textCounterFlipMatrix(0f)
        assertApprox(0f, m.translateY)
    }

    // --- imageCounterFlipMatrix ---

    @Test
    fun `imageCounterFlipMatrix positions image correctly`() {
        val m = CoordinateTransform.imageCounterFlipMatrix(10f, 20f, 50f)
        // AffineTransform(1, 0, 0, -1, x, y+height) = (1, 0, 0, -1, 10, 70)
        assertApprox(1f, m.scaleX)
        assertApprox(10f, m.translateX, "translateX should be x")
        assertApprox(70f, m.translateY, "translateY should be y+height")
    }

    // --- contentAreaMatrix ---

    @Test
    fun `contentAreaMatrix with no offset`() {
        val m = CoordinateTransform.contentAreaMatrix(
            scale = 0.5f, marginLeft = 72f, marginTop = 72f,
            pageHeight = 842f, verticalOffsetPt = 0f,
        )
        assertApprox(0.5f, m.scaleX)
        assertApprox(-0.5f, m.getValue(1, 1), "Y should be negatively scaled")
        assertApprox(72f, m.translateX, "translateX should be marginLeft")
        assertApprox(770f, m.translateY, "translateY should be pageHeight - marginTop")
    }

    @Test
    fun `contentAreaMatrix with pagination offset`() {
        val contentHeight = 698f // A4 with margins
        val m = CoordinateTransform.contentAreaMatrix(
            scale = 0.5f, marginLeft = 72f, marginTop = 72f,
            pageHeight = 842f, verticalOffsetPt = contentHeight,
        )
        // translateY = 842 - 72 + 698 = 1468
        assertApprox(1468f, m.translateY, "translateY should include pagination offset")
    }

    // --- svgToPdfRect ---

    @Test
    fun `svgToPdfRect converts coordinates correctly`() {
        val rect = CoordinateTransform.svgToPdfRect(
            svgX = 10f, svgY = 20f, width = 100f, height = 30f,
            pageHeight = 842f, marginLeft = 72f, marginTop = 72f,
        )
        // llx = marginLeft + svgX = 72 + 10 = 82
        assertApprox(82f, rect.lowerLeftX)
        // lly = pageHeight - marginTop - svgY - height = 842 - 72 - 20 - 30 = 720
        assertApprox(720f, rect.lowerLeftY)
        assertApprox(100f, rect.width)
        assertApprox(30f, rect.height)
    }

    @Test
    fun `svgToPdfRect at origin`() {
        val rect = CoordinateTransform.svgToPdfRect(
            svgX = 0f, svgY = 0f, width = 50f, height = 50f,
            pageHeight = 842f, marginLeft = 0f, marginTop = 0f,
        )
        assertApprox(0f, rect.lowerLeftX)
        assertApprox(792f, rect.lowerLeftY, "lly = 842 - 0 - 0 - 50")
    }

    @Test
    fun `svgToPdfRect at bottom of page`() {
        val rect = CoordinateTransform.svgToPdfRect(
            svgX = 0f, svgY = 792f, width = 100f, height = 50f,
            pageHeight = 842f, marginLeft = 0f, marginTop = 0f,
        )
        // lly = 842 - 0 - 792 - 50 = 0
        assertApprox(0f, rect.lowerLeftY)
    }
}
