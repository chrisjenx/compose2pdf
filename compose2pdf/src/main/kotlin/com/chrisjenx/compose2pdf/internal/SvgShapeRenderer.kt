package com.chrisjenx.compose2pdf.internal

import org.apache.pdfbox.pdmodel.PDPageContentStream

/**
 * Drawing helpers for SVG shapes on a PDFBox content stream.
 */
internal object SvgShapeRenderer {

    /** Bezier approximation constant for circles/ellipses: 4 * (sqrt(2) - 1) / 3 */
    const val KAPPA = 0.5522847498f

    /** Draws an ellipse using 4 cubic Bezier curves (standard approximation). */
    fun drawEllipse(cs: PDPageContentStream, cx: Float, cy: Float, rx: Float, ry: Float) {
        val kx = rx * KAPPA
        val ky = ry * KAPPA
        cs.moveTo(cx + rx, cy)
        cs.curveTo(cx + rx, cy + ky, cx + kx, cy + ry, cx, cy + ry)
        cs.curveTo(cx - kx, cy + ry, cx - rx, cy + ky, cx - rx, cy)
        cs.curveTo(cx - rx, cy - ky, cx - kx, cy - ry, cx, cy - ry)
        cs.curveTo(cx + kx, cy - ry, cx + rx, cy - ky, cx + rx, cy)
        cs.closePath()
    }

    /** Draws a rounded rectangle using line segments and cubic Bezier corners. */
    fun drawRoundedRect(
        cs: PDPageContentStream,
        x: Float, y: Float, w: Float, h: Float, rx: Float, ry: Float,
    ) {
        val kx = rx * KAPPA
        val ky = ry * KAPPA
        // Start at top-left + rx (just past the top-left corner curve)
        cs.moveTo(x + rx, y)
        // Top edge → top-right corner
        cs.lineTo(x + w - rx, y)
        cs.curveTo(x + w - rx + kx, y, x + w, y + ry - ky, x + w, y + ry)
        // Right edge → bottom-right corner
        cs.lineTo(x + w, y + h - ry)
        cs.curveTo(x + w, y + h - ry + ky, x + w - rx + kx, y + h, x + w - rx, y + h)
        // Bottom edge → bottom-left corner
        cs.lineTo(x + rx, y + h)
        cs.curveTo(x + rx - kx, y + h, x, y + h - ry + ky, x, y + h - ry)
        // Left edge → top-left corner
        cs.lineTo(x, y + ry)
        cs.curveTo(x, y + ry - ky, x + rx - kx, y, x + rx, y)
        cs.closePath()
    }

    /** Builds SVG path data for a rounded rectangle (for use with SvgPathParser in clip paths). */
    fun buildRoundedRectPathData(
        x: Float, y: Float, w: Float, h: Float, rx: Float, ry: Float,
    ): String = buildString {
        append("M${x + rx},${y}")
        append("L${x + w - rx},${y}")
        append("A$rx,$ry,0,0,1,${x + w},${y + ry}")
        append("L${x + w},${y + h - ry}")
        append("A$rx,$ry,0,0,1,${x + w - rx},${y + h}")
        append("L${x + rx},${y + h}")
        append("A$rx,$ry,0,0,1,${x},${y + h - ry}")
        append("L${x},${y + ry}")
        append("A$rx,$ry,0,0,1,${x + rx},${y}")
        append("Z")
    }
}
