package com.chrisjenx.compose2pdf

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Margins applied to each page of the PDF.
 */
data class PdfMargins(
    val top: Dp = 0.dp,
    val bottom: Dp = 0.dp,
    val left: Dp = 0.dp,
    val right: Dp = 0.dp,
) {
    companion object {
        val None = PdfMargins()
        val Normal = PdfMargins(top = 24.dp, bottom = 24.dp, left = 24.dp, right = 24.dp)
    }
}
