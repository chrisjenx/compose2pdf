package com.chrisjenx.compose2pdf

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Density
import com.chrisjenx.compose2pdf.internal.IosPdfRenderer

/**
 * Renders Compose content to a PDF as a ByteArray.
 *
 * On iOS, this uses Skia SVGCanvas (via Skiko) to convert Compose content to SVG,
 * then renders the SVG to PDF using Core Graphics (CGPDFContext).
 *
 * @param config Page size and margins. Defaults to A4.
 * @param density Controls the pixel resolution used during Compose layout. 2f is a good default.
 * @param defaultFontFamily The default text font family. Pass null to use system fonts.
 * @param pagination Controls page splitting. Defaults to [PdfPagination.AUTO].
 * @param content The composable content to render.
 * @return A valid PDF as a ByteArray.
 * @throws Compose2PdfException if rendering fails.
 */
fun renderToPdf(
    config: PdfPageConfig = PdfPageConfig.A4,
    density: Density = Density(2f),
    defaultFontFamily: FontFamily? = null,
    pagination: PdfPagination = PdfPagination.AUTO,
    content: @Composable () -> Unit,
): ByteArray {
    try {
        return IosPdfRenderer.render(config, density, defaultFontFamily, pagination, content)
    } catch (e: Compose2PdfException) {
        throw e
    } catch (e: IllegalArgumentException) {
        throw e
    } catch (e: Exception) {
        throw Compose2PdfException("Failed to render PDF: ${e.message}", e)
    }
}
