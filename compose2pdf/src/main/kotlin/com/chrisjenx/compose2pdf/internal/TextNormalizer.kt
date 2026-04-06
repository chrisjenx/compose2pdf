package com.chrisjenx.compose2pdf.internal

/**
 * Decomposes Unicode ligature characters that PDFBox fonts typically lack
 * cmap entries for, and adjusts per-character x-position arrays to match
 * the expanded text length.
 */
internal object TextNormalizer {

    private val LIGATURE_MAP: Map<Char, String> = mapOf(
        '\uFB00' to "ff",  // LATIN SMALL LIGATURE FF
        '\uFB01' to "fi",  // LATIN SMALL LIGATURE FI
        '\uFB02' to "fl",  // LATIN SMALL LIGATURE FL
        '\uFB03' to "ffi", // LATIN SMALL LIGATURE FFI
        '\uFB04' to "ffl", // LATIN SMALL LIGATURE FFL
        '\uFB05' to "st",  // LATIN SMALL LIGATURE LONG S T
        '\uFB06' to "st",  // LATIN SMALL LIGATURE ST
    )

    data class NormalizedText(
        val text: String,
        val xPositions: List<Float>,
    )

    fun normalize(
        text: String,
        xPositions: List<Float>,
        fontSize: Float,
    ): NormalizedText {
        if (!text.any { it in LIGATURE_MAP }) {
            return NormalizedText(text, xPositions)
        }

        val hasPositions = xPositions.size > 1 && xPositions.size >= text.length
        val newText = StringBuilder(text.length + 4)
        val newPositions = if (hasPositions) mutableListOf<Float>() else null

        for (i in text.indices) {
            val ch = text[i]
            val decomposition = LIGATURE_MAP[ch]

            if (decomposition == null) {
                newText.append(ch)
                newPositions?.add(xPositions[i])
            } else {
                newText.append(decomposition)
                if (newPositions != null) {
                    val startX = xPositions[i]
                    val endX = if (i + 1 < xPositions.size) {
                        xPositions[i + 1]
                    } else {
                        startX + fontSize * 0.3f * decomposition.length
                    }
                    val n = decomposition.length
                    for (j in 0 until n) {
                        newPositions.add(startX + (endX - startX) * j / n)
                    }
                }
            }
        }

        return NormalizedText(
            text = newText.toString(),
            xPositions = newPositions ?: xPositions,
        )
    }
}
