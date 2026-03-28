package com.chrisjenx.compose2pdf.internal

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Density
import com.chrisjenx.compose2pdf.LocalPdfPageConfig
import com.chrisjenx.compose2pdf.PdfPageConfig
import com.chrisjenx.compose2pdf.PdfPagination

/**
 * iOS-specific PDF renderer using Core Graphics (CGPDFContext).
 *
 * On iOS, Compose Multiplatform renders via Skiko, which provides access to
 * Skia's SVGCanvas. The rendering pipeline is:
 *
 * ```
 * Compose content -> CanvasLayersComposeScene -> Skia SVGCanvas -> SVG string
 *   -> SvgParser (NSXMLParser) -> SvgElement tree
 *   -> CoreGraphicsPdfConverter (CGPDFContext) -> PDF bytes
 * ```
 *
 * This shares the SVG generation step with the JVM target (via skikoMain's
 * [ComposeToSvg]) but uses Core Graphics instead of PDFBox for the SVG-to-PDF
 * conversion.
 */
internal object IosPdfRenderer {

    private const val MAX_AUTO_PAGES = 100
    // Compose Constraints max dimension is ~262143px. Stay well under that limit.
    private const val MAX_MEASURE_HEIGHT_PX = 200_000

    fun render(
        config: PdfPageConfig,
        density: Density,
        defaultFontFamily: FontFamily?,
        pagination: PdfPagination,
        content: @Composable () -> Unit,
    ): ByteArray {
        return when (pagination) {
            PdfPagination.SINGLE_PAGE -> renderSinglePage(config, density, defaultFontFamily, content)
            PdfPagination.AUTO -> renderAuto(config, density, defaultFontFamily, content)
        }
    }

    private fun renderSinglePage(
        config: PdfPageConfig,
        density: Density,
        defaultFontFamily: FontFamily?,
        content: @Composable () -> Unit,
    ): ByteArray {
        val pxW = config.contentWidthPx(density)
        val pxH = config.contentHeightPx(density)

        val svg = ComposeToSvg.render(pxW, pxH, density) {
            WrapContent(defaultFontFamily, config) {
                content()
            }
        }
        return CoreGraphicsPdfConverter.renderSinglePage(
            svg = svg,
            pageWidthPt = config.width.value,
            pageHeightPt = config.height.value,
        )
    }

    private fun renderAuto(
        config: PdfPageConfig,
        density: Density,
        defaultFontFamily: FontFamily?,
        content: @Composable () -> Unit,
    ): ByteArray {
        val contentWidthPx = config.contentWidthPx(density)
        val contentHeightPx = config.contentHeightPx(density)

        // Measurement pass: determine if content needs pagination
        val measuredHeightPx = ComposeToSvg.measureContentHeight(
            contentWidthPx, MAX_MEASURE_HEIGHT_PX, density,
        ) {
            WrapContent(defaultFontFamily, config) {
                PaginatedColumn(contentHeightPx = contentHeightPx) {
                    content()
                }
            }
        }

        // Fits on one page or uses fillMaxHeight -> single-page fallback
        if (measuredHeightPx <= contentHeightPx || measuredHeightPx >= MAX_MEASURE_HEIGHT_PX) {
            return renderSinglePage(config, density, defaultFontFamily, content)
        }

        // Multi-page: render full content and slice
        val result = ComposeToSvg.renderWithMeasurement(
            contentWidthPx, MAX_MEASURE_HEIGHT_PX, density,
        ) {
            WrapContent(defaultFontFamily, config) {
                PaginatedColumn(contentHeightPx = contentHeightPx) {
                    content()
                }
            }
        }

        val pageLayout = PageLayout.from(config)
        val totalContentHeightPt = result.measuredHeightPx.coerceAtLeast(1) / density.density

        return CoreGraphicsPdfConverter.renderAutoPages(
            svg = result.svg,
            layout = pageLayout,
            totalContentHeightPt = totalContentHeightPt,
            density = density.density,
            maxPages = MAX_AUTO_PAGES,
        )
    }

    // -- Content wrapping --

    @Composable
    private fun WrapContent(
        @Suppress("UNUSED_PARAMETER") defaultFontFamily: FontFamily?,
        config: PdfPageConfig? = null,
        content: @Composable () -> Unit,
    ) {
        // Note: JVM uses ProvideTextStyle from compose.material to set default font.
        // On iOS, compose.material is not available; defaultFontFamily is accepted for
        // API parity. When compose.material is added to iosMain dependencies, replace
        // this with the same ProvideTextStyle wrapping as the JVM renderer.
        CompositionLocalProvider(
            LocalPdfPageConfig provides config,
        ) {
            content()
        }
    }

    // -- Helpers --

    private fun PdfPageConfig.contentWidthPx(density: Density): Int =
        (contentWidth.value * density.density).toInt()

    private fun PdfPageConfig.contentHeightPx(density: Density): Int =
        (contentHeight.value * density.density).toInt()
}
