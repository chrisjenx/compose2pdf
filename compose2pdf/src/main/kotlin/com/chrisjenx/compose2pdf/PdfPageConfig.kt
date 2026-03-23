package com.chrisjenx.compose2pdf

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Page dimensions and margins for PDF output.
 *
 * Width and height represent the full page size in PDF points (1pt = 1/72 inch).
 * Margins are inset from these dimensions. [Dp] is used as the unit type because
 * Compose Dp and PDF points share the same physical definition (1/72 inch), which
 * allows seamless interop with Compose layout APIs. These values are NOT affected
 * by the render density parameter — density only controls pixel resolution during
 * Compose layout, not the physical page size.
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
        /** ISO A4: 210mm x 297mm, no margins. */
        val A4 = PdfPageConfig(width = 595.dp, height = 842.dp)

        /** ISO A4 with 1-inch (72pt) margins on all sides. */
        val A4WithMargins = A4.copy(margins = PdfMargins.Standard)

        /** US Letter: 8.5in x 11in, no margins. */
        val Letter = PdfPageConfig(width = 612.dp, height = 792.dp)

        /** US Letter with 1-inch (72pt) margins on all sides. */
        val LetterWithMargins = Letter.copy(margins = PdfMargins.Standard)

        /** ISO A3: 297mm x 420mm, no margins. */
        val A3 = PdfPageConfig(width = 842.dp, height = 1191.dp)
    }
}
