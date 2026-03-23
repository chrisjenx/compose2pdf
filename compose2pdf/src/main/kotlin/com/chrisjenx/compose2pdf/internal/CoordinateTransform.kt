package com.chrisjenx.compose2pdf.internal

import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.util.Matrix

/**
 * Coordinate system conversion utilities between SVG/Compose (Y-down) and PDF (Y-up).
 */
internal object CoordinateTransform {

    /**
     * Creates the initial page transform matrix that flips Y-axis and scales
     * from SVG coordinates to PDF page coordinates.
     *
     * SVG uses a top-left origin with Y increasing downward.
     * PDF uses a bottom-left origin with Y increasing upward.
     */
    fun svgToPageMatrix(scaleX: Float, scaleY: Float, pageHeightPt: Float): Matrix =
        Matrix(scaleX, 0f, 0f, -scaleY, 0f, pageHeightPt)

    /**
     * Creates a matrix that counter-flips Y for text rendering.
     * Text needs to be right-side up even though the global coordinate system is Y-flipped.
     */
    fun textCounterFlipMatrix(yOffset: Float): Matrix =
        Matrix(1f, 0f, 0f, -1f, 0f, 2f * yOffset)

    /**
     * Creates a matrix that counter-flips Y for image rendering.
     * Images need to be right-side up in the Y-flipped coordinate system.
     */
    fun imageCounterFlipMatrix(x: Float, y: Float, height: Float): Matrix =
        Matrix(java.awt.geom.AffineTransform(1.0, 0.0, 0.0, -1.0, x.toDouble(), (y + height).toDouble()))

    /**
     * Converts a rectangle from Compose/SVG coordinates (Y-down from content origin)
     * to PDF coordinates (Y-up from page origin) for link annotations.
     */
    fun svgToPdfRect(
        svgX: Float,
        svgY: Float,
        width: Float,
        height: Float,
        pageHeight: Float,
        marginLeft: Float,
        marginTop: Float,
    ): PDRectangle {
        val llx = marginLeft + svgX
        val lly = pageHeight - marginTop - svgY - height
        return PDRectangle(llx, lly, width, height)
    }
}
