package com.chrisjenx.compose2pdf

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Margins applied to each page of the PDF, in PDF points (1pt = 1/72 inch).
 *
 * [Dp] is used as the unit type because Compose Dp and PDF points share the
 * same physical definition (1/72 inch).
 */
data class PdfMargins(
    val top: Dp = 0.dp,
    val bottom: Dp = 0.dp,
    val left: Dp = 0.dp,
    val right: Dp = 0.dp,
) {
    companion object {
        /** No margins. */
        val None = PdfMargins()

        /** Narrow margins: 24pt (~8.5mm / ~1/3 inch) on all sides. */
        val Normal = PdfMargins(top = 24.dp, bottom = 24.dp, left = 24.dp, right = 24.dp)

        /** Standard margins: 72pt (1 inch / 25.4mm) on all sides. */
        val Standard = PdfMargins(top = 72.dp, bottom = 72.dp, left = 72.dp, right = 72.dp)
    }
}
