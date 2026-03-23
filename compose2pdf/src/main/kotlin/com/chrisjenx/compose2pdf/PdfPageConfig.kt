package com.chrisjenx.compose2pdf

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Page dimensions and margins for PDF output.
 * Width and height represent the full page size (margins are inset from these).
 */
data class PdfPageConfig(
    val width: Dp,
    val height: Dp,
    val margins: PdfMargins = PdfMargins.None,
) {
    /** Content area width after margins. */
    val contentWidth: Dp get() = width - margins.left - margins.right

    /** Content area height after margins. */
    val contentHeight: Dp get() = height - margins.top - margins.bottom

    companion object {
        /** ISO A4: 210mm x 297mm */
        val A4 = PdfPageConfig(width = 595.dp, height = 842.dp)

        /** US Letter: 8.5in x 11in */
        val Letter = PdfPageConfig(width = 612.dp, height = 792.dp)

        /** ISO A3: 297mm x 420mm */
        val A3 = PdfPageConfig(width = 842.dp, height = 1191.dp)
    }
}
