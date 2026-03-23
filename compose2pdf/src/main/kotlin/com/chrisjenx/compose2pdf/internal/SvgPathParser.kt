package com.chrisjenx.compose2pdf.internal

import org.apache.pdfbox.pdmodel.PDPageContentStream
import kotlin.math.*

/**
 * Parses SVG path data strings and emits corresponding PDFBox path operations.
 * Supports all SVG path commands including quadratic/smooth curves and arcs.
 */
internal object SvgPathParser {

    private val PATH_TOKEN_RE =
        Regex("""[MmLlHhVvCcSsQqTtAaZz]|[+-]?(?:\d+\.?\d*|\.\d+)""")
    private const val PATH_COMMANDS = "MmLlHhVvCcSsQqTtAaZz"

    /**
     * Parses an SVG path data string and emits the corresponding PDFBox path operations.
     */
    fun parse(d: String, cs: PDPageContentStream) {
        val tokens = PATH_TOKEN_RE.findAll(d).map { it.value }.toList()
        var i = 0
        var cx = 0f; var cy = 0f   // current point
        var sx = 0f; var sy = 0f   // subpath start (for Z)
        var lastCmd = ' '
        // Tracking for smooth curve reflection
        var lcp2x = 0f; var lcp2y = 0f // last cubic control point 2 (for S/s)
        var lqpx = 0f; var lqpy = 0f   // last quadratic control point (for T/t)

        fun nf(): Float = tokens[i++].toFloat()

        while (i < tokens.size) {
            val tok = tokens[i]
            val cmd: Char
            if (tok.length == 1 && tok[0] in PATH_COMMANDS) {
                cmd = tok[0]
                i++
            } else {
                // Implicit repeat: M→L, m→l, otherwise same command
                cmd = when (lastCmd) {
                    'M' -> 'L'
                    'm' -> 'l'
                    'Z', 'z', ' ' -> break
                    else -> lastCmd
                }
            }

            when (cmd) {
                'M' -> {
                    cx = nf(); cy = nf(); sx = cx; sy = cy
                    cs.moveTo(cx, cy)
                }
                'm' -> {
                    cx += nf(); cy += nf(); sx = cx; sy = cy
                    cs.moveTo(cx, cy)
                }
                'L' -> { cx = nf(); cy = nf(); cs.lineTo(cx, cy) }
                'l' -> { cx += nf(); cy += nf(); cs.lineTo(cx, cy) }
                'H' -> { cx = nf(); cs.lineTo(cx, cy) }
                'h' -> { cx += nf(); cs.lineTo(cx, cy) }
                'V' -> { cy = nf(); cs.lineTo(cx, cy) }
                'v' -> { cy += nf(); cs.lineTo(cx, cy) }
                'C' -> {
                    val x1 = nf(); val y1 = nf()
                    val x2 = nf(); val y2 = nf()
                    cx = nf(); cy = nf()
                    cs.curveTo(x1, y1, x2, y2, cx, cy)
                    lcp2x = x2; lcp2y = y2
                }
                'c' -> {
                    val x1 = cx + nf(); val y1 = cy + nf()
                    val x2 = cx + nf(); val y2 = cy + nf()
                    cx += nf(); cy += nf()
                    cs.curveTo(x1, y1, x2, y2, cx, cy)
                    lcp2x = x2; lcp2y = y2
                }
                'S' -> {
                    val x1 = 2 * cx - lcp2x; val y1 = 2 * cy - lcp2y
                    val x2 = nf(); val y2 = nf()
                    cx = nf(); cy = nf()
                    cs.curveTo(x1, y1, x2, y2, cx, cy)
                    lcp2x = x2; lcp2y = y2
                }
                's' -> {
                    val x1 = 2 * cx - lcp2x; val y1 = 2 * cy - lcp2y
                    val x2 = cx + nf(); val y2 = cy + nf()
                    cx += nf(); cy += nf()
                    cs.curveTo(x1, y1, x2, y2, cx, cy)
                    lcp2x = x2; lcp2y = y2
                }
                'Q' -> {
                    val qx = nf(); val qy = nf()
                    val x = nf(); val y = nf()
                    quadToCubic(cs, cx, cy, qx, qy, x, y)
                    lqpx = qx; lqpy = qy; cx = x; cy = y
                }
                'q' -> {
                    val qx = cx + nf(); val qy = cy + nf()
                    val x = cx + nf(); val y = cy + nf()
                    quadToCubic(cs, cx, cy, qx, qy, x, y)
                    lqpx = qx; lqpy = qy; cx = x; cy = y
                }
                'T' -> {
                    val qx = 2 * cx - lqpx; val qy = 2 * cy - lqpy
                    val x = nf(); val y = nf()
                    quadToCubic(cs, cx, cy, qx, qy, x, y)
                    lqpx = qx; lqpy = qy; cx = x; cy = y
                }
                't' -> {
                    val qx = 2 * cx - lqpx; val qy = 2 * cy - lqpy
                    val x = cx + nf(); val y = cy + nf()
                    quadToCubic(cs, cx, cy, qx, qy, x, y)
                    lqpx = qx; lqpy = qy; cx = x; cy = y
                }
                'A' -> {
                    val rx = nf(); val ry = nf(); val rot = nf()
                    val la = nf().toInt() != 0; val sw = nf().toInt() != 0
                    val x = nf(); val y = nf()
                    arcToCubic(cs, cx, cy, rx, ry, rot, la, sw, x, y)
                    cx = x; cy = y
                }
                'a' -> {
                    val rx = nf(); val ry = nf(); val rot = nf()
                    val la = nf().toInt() != 0; val sw = nf().toInt() != 0
                    val x = cx + nf(); val y = cy + nf()
                    arcToCubic(cs, cx, cy, rx, ry, rot, la, sw, x, y)
                    cx = x; cy = y
                }
                'Z', 'z' -> {
                    cs.closePath(); cx = sx; cy = sy
                }
            }

            // Reset smooth curve control points when previous wasn't a matching type
            if (cmd !in "CcSs") { lcp2x = cx; lcp2y = cy }
            if (cmd !in "QqTt") { lqpx = cx; lqpy = cy }
            lastCmd = cmd
        }
    }

    /** Converts a quadratic Bezier to cubic Bezier (PDFBox only supports cubic). */
    private fun quadToCubic(
        cs: PDPageContentStream,
        x0: Float, y0: Float, qx: Float, qy: Float, x: Float, y: Float,
    ) {
        val c1x = x0 + 2f / 3f * (qx - x0)
        val c1y = y0 + 2f / 3f * (qy - y0)
        val c2x = x + 2f / 3f * (qx - x)
        val c2y = y + 2f / 3f * (qy - y)
        cs.curveTo(c1x, c1y, c2x, c2y, x, y)
    }

    /**
     * Converts an SVG arc to one or more cubic Bezier curves.
     * Implements the SVG spec endpoint-to-center arc parameterization (F.6).
     */
    private fun arcToCubic(
        cs: PDPageContentStream,
        x1: Float, y1: Float,
        rxIn: Float, ryIn: Float,
        xRotDeg: Float,
        largeArc: Boolean,
        sweep: Boolean,
        x2: Float, y2: Float,
    ) {
        // Degenerate: same point → no-op
        if (x1 == x2 && y1 == y2) return
        // Degenerate: zero radius → straight line
        var rx = abs(rxIn).toDouble()
        var ry = abs(ryIn).toDouble()
        if (rx == 0.0 || ry == 0.0) { cs.lineTo(x2, y2); return }

        val phi = Math.toRadians(xRotDeg.toDouble())
        val cp = cos(phi); val sp = sin(phi)

        // Step 1: Compute (x1', y1') in rotated frame
        val dx = (x1 - x2).toDouble() / 2.0
        val dy = (y1 - y2).toDouble() / 2.0
        val x1p = cp * dx + sp * dy
        val y1p = -sp * dx + cp * dy

        // Step 2: Ensure radii are large enough
        val lambda = (x1p * x1p) / (rx * rx) + (y1p * y1p) / (ry * ry)
        if (lambda > 1.0) {
            val s = sqrt(lambda); rx *= s; ry *= s
        }
        val rxSq = rx * rx; val rySq = ry * ry
        val x1pSq = x1p * x1p; val y1pSq = y1p * y1p

        // Step 3: Compute center point in rotated frame
        var sq = ((rxSq * rySq - rxSq * y1pSq - rySq * x1pSq) /
            (rxSq * y1pSq + rySq * x1pSq)).coerceAtLeast(0.0)
        sq = sqrt(sq)
        if (largeArc == sweep) sq = -sq
        val cxp = sq * rx * y1p / ry
        val cyp = -sq * ry * x1p / rx

        // Transform center to world coordinates
        val mx = (x1 + x2).toDouble() / 2.0
        val my = (y1 + y2).toDouble() / 2.0
        val ccx = cp * cxp - sp * cyp + mx
        val ccy = sp * cxp + cp * cyp + my

        // Step 4: Compute start angle and sweep
        val theta1 = vecAngle(1.0, 0.0, (x1p - cxp) / rx, (y1p - cyp) / ry)
        var dTheta = vecAngle(
            (x1p - cxp) / rx, (y1p - cyp) / ry,
            (-x1p - cxp) / rx, (-y1p - cyp) / ry,
        )
        if (!sweep && dTheta > 0) dTheta -= 2 * PI
        if (sweep && dTheta < 0) dTheta += 2 * PI

        // Step 5: Split into ≤90° segments, approximate each as cubic Bezier
        val numSegs = ceil(abs(dTheta) / (PI / 2)).toInt().coerceAtLeast(1)
        val segAngle = dTheta / numSegs
        val alpha = 4.0 / 3.0 * tan(segAngle / 4.0)

        var t = theta1
        for (s in 0 until numSegs) {
            val t2 = t + segAngle
            val cosT1 = cos(t); val sinT1 = sin(t)
            val cosT2 = cos(t2); val sinT2 = sin(t2)

            // Points on the ellipse in local frame
            val ep1x = rx * cosT1; val ep1y = ry * sinT1
            val ep2x = rx * cosT2; val ep2y = ry * sinT2

            // Control points via tangent vectors
            val c1x = ep1x - alpha * rx * sinT1
            val c1y = ep1y + alpha * ry * cosT1
            val c2x = ep2x + alpha * rx * sinT2
            val c2y = ep2y - alpha * ry * cosT2

            // Transform to world coordinates (rotate by phi, translate by center)
            fun wx(ex: Double, ey: Double) = (cp * ex - sp * ey + ccx).toFloat()
            fun wy(ex: Double, ey: Double) = (sp * ex + cp * ey + ccy).toFloat()

            cs.curveTo(
                wx(c1x, c1y), wy(c1x, c1y),
                wx(c2x, c2y), wy(c2x, c2y),
                wx(ep2x, ep2y), wy(ep2x, ep2y),
            )
            t = t2
        }
    }

    /** Computes the angle between two 2D vectors per SVG spec F.6.5. */
    private fun vecAngle(ux: Double, uy: Double, vx: Double, vy: Double): Double {
        val dot = ux * vx + uy * vy
        val len = sqrt(ux * ux + uy * uy) * sqrt(vx * vx + vy * vy)
        var a = acos((dot / len).coerceIn(-1.0, 1.0))
        if (ux * vy - uy * vx < 0) a = -a
        return a
    }
}
