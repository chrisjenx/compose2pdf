package com.chrisjenx.compose2pdf.internal

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Density
import com.chrisjenx.compose2pdf.Compose2PdfException
import com.chrisjenx.compose2pdf.PdfPageConfig
import com.chrisjenx.compose2pdf.PdfPagination

/**
 * iOS-specific PDF renderer using Core Graphics (CGPDFContext).
 *
 * On iOS, Compose Multiplatform renders via Skiko, which provides access to
 * Skia's SVGCanvas. The rendering pipeline is:
 *
 * ```
 * Compose content → CanvasLayersComposeScene → Skia SVGCanvas → SVG string
 *   → Core Graphics CGPDFContext rendering → PDF bytes
 * ```
 *
 * This shares the SVG generation step with the JVM target but uses Core Graphics
 * instead of PDFBox for the SVG-to-PDF conversion.
 */
internal object IosPdfRenderer {

    fun render(
        config: PdfPageConfig,
        density: Density,
        defaultFontFamily: FontFamily?,
        pagination: PdfPagination,
        content: @Composable () -> Unit,
    ): ByteArray {
        // TODO: Implement iOS PDF rendering pipeline:
        // 1. Render Compose content to SVG via Skia SVGCanvas (verify availability on iOS Skiko)
        // 2. Parse SVG
        // 3. Draw SVG content to CGPDFContext using Core Graphics APIs:
        //    - CGPDFContextCreateWithURL / CGPDFContextBeginPage
        //    - CGContextMoveToPoint, CGContextAddLineToPoint, CGContextAddCurveToPoint
        //    - Core Text for text rendering (CTFontCreateWithName, CTLineDraw)
        //    - CGContextDrawImage for images
        //    - CGPDFContextSetURLForRect for link annotations
        throw Compose2PdfException(
            "iOS PDF rendering is not yet implemented. " +
                "The iOS target requires Core Graphics PDF rendering support " +
                "which is under development."
        )
    }
}
