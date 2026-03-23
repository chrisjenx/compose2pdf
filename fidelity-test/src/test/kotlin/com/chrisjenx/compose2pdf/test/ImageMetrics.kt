package com.chrisjenx.compose2pdf.test

import java.awt.image.BufferedImage
import java.awt.image.ConvolveOp
import java.awt.image.Kernel
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sqrt

/**
 * Image comparison utilities for fidelity testing.
 * All methods handle transparency by flattening images onto white before comparison.
 */
object ImageMetrics {

    private const val WINDOW_SIZE = 11
    private const val SIGMA = 1.5
    private val C1 = (0.01 * 255).let { it * it } // 6.5025
    private val C2 = (0.03 * 255).let { it * it } // 58.5225

    private val gaussianKernel: Array<DoubleArray> = run {
        val half = WINDOW_SIZE / 2
        val kernel = Array(WINDOW_SIZE) { DoubleArray(WINDOW_SIZE) }
        var sum = 0.0
        for (y in 0 until WINDOW_SIZE) {
            for (x in 0 until WINDOW_SIZE) {
                val dx = (x - half).toDouble()
                val dy = (y - half).toDouble()
                val value = exp(-(dx * dx + dy * dy) / (2.0 * SIGMA * SIGMA))
                kernel[y][x] = value
                sum += value
            }
        }
        for (y in 0 until WINDOW_SIZE) {
            for (x in 0 until WINDOW_SIZE) {
                kernel[y][x] /= sum
            }
        }
        kernel
    }

    fun flattenOnWhite(src: BufferedImage): BufferedImage {
        val result = BufferedImage(src.width, src.height, BufferedImage.TYPE_INT_RGB)
        val g = result.createGraphics()
        g.color = java.awt.Color.WHITE
        g.fillRect(0, 0, src.width, src.height)
        g.drawImage(src, 0, 0, null)
        g.dispose()
        return result
    }

    fun gaussianBlur(src: BufferedImage, sigma: Double = 1.5): BufferedImage {
        val radius = (sigma * 3).toInt().coerceAtLeast(1)
        val size = 2 * radius + 1
        val kernel = FloatArray(size * size)
        var sum = 0f
        for (y in 0 until size) {
            for (x in 0 until size) {
                val dx = (x - radius).toFloat()
                val dy = (y - radius).toFloat()
                val value = exp(-(dx * dx + dy * dy).toDouble() / (2.0 * sigma * sigma)).toFloat()
                kernel[y * size + x] = value
                sum += value
            }
        }
        for (i in kernel.indices) kernel[i] /= sum
        val op = ConvolveOp(Kernel(size, size, kernel), ConvolveOp.EDGE_NO_OP, null)
        return op.filter(src, null)
    }

    fun computeRmse(reference: BufferedImage, candidate: BufferedImage): Double {
        val ref = gaussianBlur(flattenOnWhite(reference))
        val cand = gaussianBlur(flattenOnWhite(candidate))
        val w = minOf(ref.width, cand.width)
        val h = minOf(ref.height, cand.height)
        if (w == 0 || h == 0) return 1.0

        var sumSqError = 0.0
        var pixelCount = 0L

        for (y in 0 until h) {
            for (x in 0 until w) {
                val refRgb = ref.getRGB(x, y)
                val candRgb = cand.getRGB(x, y)

                val rr = ((refRgb shr 16) and 0xFF) / 255.0
                val rg = ((refRgb shr 8) and 0xFF) / 255.0
                val rb = (refRgb and 0xFF) / 255.0

                val cr = ((candRgb shr 16) and 0xFF) / 255.0
                val cg = ((candRgb shr 8) and 0xFF) / 255.0
                val cb = (candRgb and 0xFF) / 255.0

                sumSqError += (rr - cr) * (rr - cr) + (rg - cg) * (rg - cg) + (rb - cb) * (rb - cb)
                pixelCount++
            }
        }

        return sqrt(sumSqError / (3.0 * pixelCount))
    }

    /**
     * Fraction of pixels where all RGB channels match within tolerance (+-[tolerance] per channel).
     * Returns value in [0, 1] where 1.0 means all pixels match.
     */
    fun computeExactMatchPercent(reference: BufferedImage, candidate: BufferedImage, tolerance: Int = 2): Double {
        val ref = flattenOnWhite(reference)
        val cand = flattenOnWhite(candidate)
        val w = minOf(ref.width, cand.width)
        val h = minOf(ref.height, cand.height)
        if (w == 0 || h == 0) return 0.0

        var matchCount = 0L
        var totalCount = 0L

        for (y in 0 until h) {
            for (x in 0 until w) {
                val refRgb = ref.getRGB(x, y)
                val candRgb = cand.getRGB(x, y)

                val dr = abs(((refRgb shr 16) and 0xFF) - ((candRgb shr 16) and 0xFF))
                val dg = abs(((refRgb shr 8) and 0xFF) - ((candRgb shr 8) and 0xFF))
                val db = abs((refRgb and 0xFF) - (candRgb and 0xFF))

                if (dr <= tolerance && dg <= tolerance && db <= tolerance) {
                    matchCount++
                }
                totalCount++
            }
        }

        return matchCount.toDouble() / totalCount
    }

    /**
     * Worst-case single-pixel RGB Euclidean distance, normalized to [0, 1].
     * Catches catastrophic errors that RMSE averages away.
     */
    fun computeMaxPixelError(reference: BufferedImage, candidate: BufferedImage): Double {
        val ref = flattenOnWhite(reference)
        val cand = flattenOnWhite(candidate)
        val w = minOf(ref.width, cand.width)
        val h = minOf(ref.height, cand.height)
        if (w == 0 || h == 0) return 1.0

        var maxError = 0.0

        for (y in 0 until h) {
            for (x in 0 until w) {
                val refRgb = ref.getRGB(x, y)
                val candRgb = cand.getRGB(x, y)

                val dr = (((refRgb shr 16) and 0xFF) - ((candRgb shr 16) and 0xFF)) / 255.0
                val dg = (((refRgb shr 8) and 0xFF) - ((candRgb shr 8) and 0xFF)) / 255.0
                val db = ((refRgb and 0xFF) - (candRgb and 0xFF)) / 255.0

                val error = sqrt((dr * dr + dg * dg + db * db) / 3.0)
                if (error > maxError) maxError = error
            }
        }

        return maxError
    }

    /**
     * Structural Similarity Index (Wang et al. 2004).
     * Uses 11x11 Gaussian window, sigma=1.5, standard constants.
     * Operates on luminance channel (Y = 0.2126R + 0.7152G + 0.0722B).
     * Returns mean SSIM across all windows (1.0 = identical).
     */
    fun computeSsim(reference: BufferedImage, candidate: BufferedImage): Double {
        val ref = flattenOnWhite(reference)
        val cand = flattenOnWhite(candidate)
        val w = minOf(ref.width, cand.width)
        val h = minOf(ref.height, cand.height)
        if (w < WINDOW_SIZE || h < WINDOW_SIZE) return 0.0

        // Pre-extract luminance arrays for performance (avoids repeated getRGB in inner loop)
        val refLum = DoubleArray(w * h)
        val candLum = DoubleArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                refLum[y * w + x] = toLuminance(ref.getRGB(x, y))
                candLum[y * w + x] = toLuminance(cand.getRGB(x, y))
            }
        }

        val half = WINDOW_SIZE / 2
        var ssimSum = 0.0
        var windowCount = 0

        for (wy in half until h - half) {
            for (wx in half until w - half) {
                var muX = 0.0
                var muY = 0.0
                var sigmaX2 = 0.0
                var sigmaY2 = 0.0
                var sigmaXY = 0.0

                for (ky in 0 until WINDOW_SIZE) {
                    for (kx in 0 until WINDOW_SIZE) {
                        val weight = gaussianKernel[ky][kx]
                        val idx = (wy + ky - half) * w + (wx + kx - half)
                        val lx = refLum[idx]
                        val ly = candLum[idx]
                        muX += weight * lx
                        muY += weight * ly
                        sigmaX2 += weight * lx * lx
                        sigmaY2 += weight * ly * ly
                        sigmaXY += weight * lx * ly
                    }
                }

                sigmaX2 -= muX * muX
                sigmaY2 -= muY * muY
                sigmaXY -= muX * muY

                val numerator = (2.0 * muX * muY + C1) * (2.0 * sigmaXY + C2)
                val denominator = (muX * muX + muY * muY + C1) * (sigmaX2 + sigmaY2 + C2)
                ssimSum += numerator / denominator
                windowCount++
            }
        }

        return if (windowCount > 0) ssimSum / windowCount else 0.0
    }

    /**
     * Generates a per-pixel heat map diff image.
     * Gray (#EEEEEE) = identical pixels, green->yellow = small deltas, yellow->red = large deltas.
     * Amplification factor makes subtle differences visible.
     */
    fun generateDiffImage(reference: BufferedImage, candidate: BufferedImage, amplification: Float = 10f): BufferedImage {
        val ref = flattenOnWhite(reference)
        val cand = flattenOnWhite(candidate)
        val w = minOf(ref.width, cand.width)
        val h = minOf(ref.height, cand.height)
        val result = BufferedImage(maxOf(w, 1), maxOf(h, 1), BufferedImage.TYPE_INT_RGB)

        for (y in 0 until h) {
            for (x in 0 until w) {
                val refRgb = ref.getRGB(x, y)
                val candRgb = cand.getRGB(x, y)

                val dr = (((refRgb shr 16) and 0xFF) - ((candRgb shr 16) and 0xFF)) / 255.0
                val dg = (((refRgb shr 8) and 0xFF) - ((candRgb shr 8) and 0xFF)) / 255.0
                val db = ((refRgb and 0xFF) - (candRgb and 0xFF)) / 255.0

                val distance = sqrt((dr * dr + dg * dg + db * db) / 3.0)
                val amplified = (distance * amplification).coerceIn(0.0, 1.0)

                val rgb = if (amplified == 0.0) {
                    (0xEE shl 16) or (0xEE shl 8) or 0xEE
                } else if (amplified <= 0.5) {
                    val t = amplified / 0.5
                    val r = (t * 255).toInt().coerceIn(0, 255)
                    val g = (204 + t * 51).toInt().coerceIn(0, 255)
                    (r shl 16) or (g shl 8)
                } else {
                    val t = (amplified - 0.5) / 0.5
                    val g = ((1.0 - t) * 255).toInt().coerceIn(0, 255)
                    (0xFF shl 16) or (g shl 8)
                }

                result.setRGB(x, y, rgb)
            }
        }

        return result
    }

    /**
     * Structural RMSE: uses heavy gaussian blur (sigma=5) to eliminate text anti-aliasing
     * differences and focus on layout/shape correctness. A missing shape, wrong position,
     * or wrong color will still produce high RMSE, but text rendering differences between
     * Skia and Chromium are blurred away.
     */
    fun computeStructuralRmse(reference: BufferedImage, candidate: BufferedImage): Double {
        // sigma=5 blurs away text anti-aliasing differences while preserving
        // shape/layout mismatches (missing elements, wrong positions, wrong colors)
        val ref = gaussianBlur(flattenOnWhite(reference), sigma = 5.0)
        val cand = gaussianBlur(flattenOnWhite(candidate), sigma = 5.0)
        val w = minOf(ref.width, cand.width)
        val h = minOf(ref.height, cand.height)
        if (w == 0 || h == 0) return 1.0

        var sumSqError = 0.0
        var pixelCount = 0L

        for (y in 0 until h) {
            for (x in 0 until w) {
                val refRgb = ref.getRGB(x, y)
                val candRgb = cand.getRGB(x, y)

                val rr = ((refRgb shr 16) and 0xFF) / 255.0
                val rg = ((refRgb shr 8) and 0xFF) / 255.0
                val rb = (refRgb and 0xFF) / 255.0

                val cr = ((candRgb shr 16) and 0xFF) / 255.0
                val cg = ((candRgb shr 8) and 0xFF) / 255.0
                val cb = (candRgb and 0xFF) / 255.0

                sumSqError += (rr - cr) * (rr - cr) + (rg - cg) * (rg - cg) + (rb - cb) * (rb - cb)
                pixelCount++
            }
        }

        return sqrt(sumSqError / (3.0 * pixelCount))
    }

    private fun toLuminance(rgb: Int): Double {
        val r = ((rgb shr 16) and 0xFF).toDouble()
        val g = ((rgb shr 8) and 0xFF).toDouble()
        val b = (rgb and 0xFF).toDouble()
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }
}
