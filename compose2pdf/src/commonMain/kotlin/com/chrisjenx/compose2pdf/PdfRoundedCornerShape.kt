package com.chrisjenx.compose2pdf

import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * A Shape that produces correct vector PDF output for rounded rectangles with
 * per-corner radii.
 *
 * Skia's SVGCanvas serializes RoundRect clip paths as `<rect rx="..." ry="...">`,
 * which only supports uniform corner radii. When corners differ (e.g., only topStart
 * and bottomEnd are rounded), the SVG loses this information and all corners are
 * rendered identically.
 *
 * This shape detects non-uniform corners and returns [Outline.Generic] with an
 * explicit bezier path instead of [Outline.Rounded]. SVGCanvas then emits
 * `<path d="...">` which preserves the full geometry.
 *
 * For uniform corners, it delegates to the standard [RoundedCornerShape] behavior.
 *
 * Usage:
 * ```
 * Box(Modifier.clip(PdfRoundedCornerShape(topStart = 24.dp, bottomEnd = 24.dp)))
 * ```
 */
fun PdfRoundedCornerShape(
    topStart: Dp = 0.dp,
    topEnd: Dp = 0.dp,
    bottomEnd: Dp = 0.dp,
    bottomStart: Dp = 0.dp,
): Shape = PdfSafeShape(RoundedCornerShape(topStart, topEnd, bottomEnd, bottomStart))

/**
 * Wraps any [Shape] to ensure asymmetric rounded rect outlines are emitted as
 * explicit bezier paths for correct SVG/PDF rendering.
 */
fun Shape.asPdfSafe(): Shape = if (this is PdfSafeShape) this else PdfSafeShape(this)

private class PdfSafeShape(private val delegate: Shape) : Shape {

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: androidx.compose.ui.unit.Density,
    ): Outline {
        val outline = delegate.createOutline(size, layoutDirection, density)
        if (outline is Outline.Rounded) {
            val rr = outline.roundRect
            if (hasNonUniformCorners(rr)) {
                return Outline.Generic(roundRectToPath(rr))
            }
        }
        return outline
    }

    companion object {
        private const val KAPPA = 0.5522847498f

        private fun hasNonUniformCorners(rr: androidx.compose.ui.geometry.RoundRect): Boolean {
            val tlx = rr.topLeftCornerRadius.x
            val tly = rr.topLeftCornerRadius.y
            val trx = rr.topRightCornerRadius.x
            val try_ = rr.topRightCornerRadius.y
            val brx = rr.bottomRightCornerRadius.x
            val bry = rr.bottomRightCornerRadius.y
            val blx = rr.bottomLeftCornerRadius.x
            val bly = rr.bottomLeftCornerRadius.y
            return !(approxEqual(tlx, trx) && approxEqual(tlx, brx) && approxEqual(tlx, blx) &&
                approxEqual(tly, try_) && approxEqual(tly, bry) && approxEqual(tly, bly))
        }

        private fun approxEqual(a: Float, b: Float) = abs(a - b) < 0.01f

        private fun roundRectToPath(rr: androidx.compose.ui.geometry.RoundRect): Path {
            val x = rr.left
            val y = rr.top
            val w = rr.width
            val h = rr.height

            val tlrx = rr.topLeftCornerRadius.x
            val tlry = rr.topLeftCornerRadius.y
            val trrx = rr.topRightCornerRadius.x
            val trry = rr.topRightCornerRadius.y
            val brrx = rr.bottomRightCornerRadius.x
            val brry = rr.bottomRightCornerRadius.y
            val blrx = rr.bottomLeftCornerRadius.x
            val blry = rr.bottomLeftCornerRadius.y

            return Path().apply {
                // Start at top edge, after top-left corner
                moveTo(x + tlrx, y)

                // Top edge → top-right corner
                lineTo(x + w - trrx, y)
                if (trrx > 0f || trry > 0f) {
                    val kx = trrx * KAPPA; val ky = trry * KAPPA
                    cubicTo(
                        x + w - trrx + kx, y,
                        x + w, y + trry - ky,
                        x + w, y + trry,
                    )
                }

                // Right edge → bottom-right corner
                lineTo(x + w, y + h - brry)
                if (brrx > 0f || brry > 0f) {
                    val kx = brrx * KAPPA; val ky = brry * KAPPA
                    cubicTo(
                        x + w, y + h - brry + ky,
                        x + w - brrx + kx, y + h,
                        x + w - brrx, y + h,
                    )
                }

                // Bottom edge → bottom-left corner
                lineTo(x + blrx, y + h)
                if (blrx > 0f || blry > 0f) {
                    val kx = blrx * KAPPA; val ky = blry * KAPPA
                    cubicTo(
                        x + blrx - kx, y + h,
                        x, y + h - blry + ky,
                        x, y + h - blry,
                    )
                }

                // Left edge → top-left corner
                lineTo(x, y + tlry)
                if (tlrx > 0f || tlry > 0f) {
                    val kx = tlrx * KAPPA; val ky = tlry * KAPPA
                    cubicTo(
                        x, y + tlry - ky,
                        x + tlrx - kx, y,
                        x + tlrx, y,
                    )
                }

                close()
            }
        }
    }
}
