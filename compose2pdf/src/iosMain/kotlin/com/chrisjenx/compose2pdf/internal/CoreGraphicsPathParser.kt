package com.chrisjenx.compose2pdf.internal

import kotlinx.cinterop.ExperimentalForeignApi
import platform.CoreGraphics.CGContextAddCurveToPoint
import platform.CoreGraphics.CGContextAddLineToPoint
import platform.CoreGraphics.CGContextClosePath
import platform.CoreGraphics.CGContextMoveToPoint
import platform.CoreGraphics.CGContextRef
import platform.CoreGraphics.CGFloat
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Parses SVG path data strings and emits corresponding Core Graphics path operations.
 *
 * Supports all SVG path commands: M/m, L/l, H/h, V/v, C/c, S/s, Q/q, T/t, A/a, Z/z.
 * Port of [SvgPathParser] from JVM (PDFBox) to iOS (Core Graphics).
 */
@OptIn(ExperimentalForeignApi::class)
internal object CoreGraphicsPathParser {

    private val PATH_TOKEN_RE =
        Regex("""[MmLlHhVvCcSsQqTtAaZz]|[+-]?(?:\d+\.?\d*|\.\d+)""")
    private const val PATH_COMMANDS = "MmLlHhVvCcSsQqTtAaZz"

    /**
     * Parses an SVG path data string and emits corresponding Core Graphics path operations.
     */
    fun parse(d: String, ctx: CGContextRef) {
        val tokens = PATH_TOKEN_RE.findAll(d).map { it.value }.toList()
        var i = 0
        var cx: CGFloat = 0.0; var cy: CGFloat = 0.0   // current point
        var sx: CGFloat = 0.0; var sy: CGFloat = 0.0   // subpath start (for Z)
        var lastCmd = ' '
        // Tracking for smooth curve reflection
        var lcp2x: CGFloat = 0.0; var lcp2y: CGFloat = 0.0 // last cubic control point 2 (for S/s)
        var lqpx: CGFloat = 0.0; var lqpy: CGFloat = 0.0   // last quadratic control point (for T/t)

        fun nf(): CGFloat = tokens[i++].toDouble()

        while (i < tokens.size) {
            val tok = tokens[i]
            val cmd: Char
            if (tok.length == 1 && tok[0] in PATH_COMMANDS) {
                cmd = tok[0]
                i++
            } else {
                // Implicit repeat: M->L, m->l, otherwise same command
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
                    CGContextMoveToPoint(ctx, cx, cy)
                }
                'm' -> {
                    cx += nf(); cy += nf(); sx = cx; sy = cy
                    CGContextMoveToPoint(ctx, cx, cy)
                }
                'L' -> { cx = nf(); cy = nf(); CGContextAddLineToPoint(ctx, cx, cy) }
                'l' -> { cx += nf(); cy += nf(); CGContextAddLineToPoint(ctx, cx, cy) }
                'H' -> { cx = nf(); CGContextAddLineToPoint(ctx, cx, cy) }
                'h' -> { cx += nf(); CGContextAddLineToPoint(ctx, cx, cy) }
                'V' -> { cy = nf(); CGContextAddLineToPoint(ctx, cx, cy) }
                'v' -> { cy += nf(); CGContextAddLineToPoint(ctx, cx, cy) }
                'C' -> {
                    val x1 = nf(); val y1 = nf()
                    val x2 = nf(); val y2 = nf()
                    cx = nf(); cy = nf()
                    CGContextAddCurveToPoint(ctx, x1, y1, x2, y2, cx, cy)
                    lcp2x = x2; lcp2y = y2
                }
                'c' -> {
                    val x1 = cx + nf(); val y1 = cy + nf()
                    val x2 = cx + nf(); val y2 = cy + nf()
                    cx += nf(); cy += nf()
                    CGContextAddCurveToPoint(ctx, x1, y1, x2, y2, cx, cy)
                    lcp2x = x2; lcp2y = y2
                }
                'S' -> {
                    val x1 = 2 * cx - lcp2x; val y1 = 2 * cy - lcp2y
                    val x2 = nf(); val y2 = nf()
                    cx = nf(); cy = nf()
                    CGContextAddCurveToPoint(ctx, x1, y1, x2, y2, cx, cy)
                    lcp2x = x2; lcp2y = y2
                }
                's' -> {
                    val x1 = 2 * cx - lcp2x; val y1 = 2 * cy - lcp2y
                    val x2 = cx + nf(); val y2 = cy + nf()
                    cx += nf(); cy += nf()
                    CGContextAddCurveToPoint(ctx, x1, y1, x2, y2, cx, cy)
                    lcp2x = x2; lcp2y = y2
                }
                'Q' -> {
                    val qx = nf(); val qy = nf()
                    val x = nf(); val y = nf()
                    quadToCubic(ctx, cx, cy, qx, qy, x, y)
                    lqpx = qx; lqpy = qy; cx = x; cy = y
                }
                'q' -> {
                    val qx = cx + nf(); val qy = cy + nf()
                    val x = cx + nf(); val y = cy + nf()
                    quadToCubic(ctx, cx, cy, qx, qy, x, y)
                    lqpx = qx; lqpy = qy; cx = x; cy = y
                }
                'T' -> {
                    val qx = 2 * cx - lqpx; val qy = 2 * cy - lqpy
                    val x = nf(); val y = nf()
                    quadToCubic(ctx, cx, cy, qx, qy, x, y)
                    lqpx = qx; lqpy = qy; cx = x; cy = y
                }
                't' -> {
                    val qx = 2 * cx - lqpx; val qy = 2 * cy - lqpy
                    val x = cx + nf(); val y = cy + nf()
                    quadToCubic(ctx, cx, cy, qx, qy, x, y)
                    lqpx = qx; lqpy = qy; cx = x; cy = y
                }
                'A' -> {
                    val rx = nf(); val ry = nf(); val rot = nf()
                    val la = nf().toInt() != 0; val sw = nf().toInt() != 0
                    val x = nf(); val y = nf()
                    arcToCubic(ctx, cx, cy, rx, ry, rot, la, sw, x, y)
                    cx = x; cy = y
                }
                'a' -> {
                    val rx = nf(); val ry = nf(); val rot = nf()
                    val la = nf().toInt() != 0; val sw = nf().toInt() != 0
                    val x = cx + nf(); val y = cy + nf()
                    arcToCubic(ctx, cx, cy, rx, ry, rot, la, sw, x, y)
                    cx = x; cy = y
                }
                'Z', 'z' -> {
                    CGContextClosePath(ctx); cx = sx; cy = sy
                }
            }

            // Reset smooth curve control points when previous wasn't a matching type
            if (cmd !in "CcSs") { lcp2x = cx; lcp2y = cy }
            if (cmd !in "QqTt") { lqpx = cx; lqpy = cy }
            lastCmd = cmd
        }
    }

    /** Converts a quadratic Bezier to cubic Bezier (Core Graphics only supports cubic). */
    private fun quadToCubic(
        ctx: CGContextRef,
        x0: CGFloat, y0: CGFloat, qx: CGFloat, qy: CGFloat, x: CGFloat, y: CGFloat,
    ) {
        val c1x = x0 + 2.0 / 3.0 * (qx - x0)
        val c1y = y0 + 2.0 / 3.0 * (qy - y0)
        val c2x = x + 2.0 / 3.0 * (qx - x)
        val c2y = y + 2.0 / 3.0 * (qy - y)
        CGContextAddCurveToPoint(ctx, c1x, c1y, c2x, c2y, x, y)
    }

    /**
     * Converts an SVG arc to one or more cubic Bezier curves.
     * Implements the SVG spec endpoint-to-center arc parameterization (F.6).
     */
    private fun arcToCubic(
        ctx: CGContextRef,
        x1: CGFloat, y1: CGFloat,
        rxIn: CGFloat, ryIn: CGFloat,
        xRotDeg: CGFloat,
        largeArc: Boolean,
        sweep: Boolean,
        x2: CGFloat, y2: CGFloat,
    ) {
        // Degenerate: same point -> no-op
        if (x1 == x2 && y1 == y2) return
        // Degenerate: zero radius -> straight line
        var rx = abs(rxIn)
        var ry = abs(ryIn)
        if (rx == 0.0 || ry == 0.0) { CGContextAddLineToPoint(ctx, x2, y2); return }

        val phi = xRotDeg * PI / 180.0
        val cp = cos(phi); val sp = sin(phi)

        // Step 1: Compute (x1', y1') in rotated frame
        val dx = (x1 - x2) / 2.0
        val dy = (y1 - y2) / 2.0
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
        val mx = (x1 + x2) / 2.0
        val my = (y1 + y2) / 2.0
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

        // Step 5: Split into <=90deg segments, approximate each as cubic Bezier
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
            fun wx(ex: Double, ey: Double): CGFloat = cp * ex - sp * ey + ccx
            fun wy(ex: Double, ey: Double): CGFloat = sp * ex + cp * ey + ccy

            CGContextAddCurveToPoint(
                ctx,
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
