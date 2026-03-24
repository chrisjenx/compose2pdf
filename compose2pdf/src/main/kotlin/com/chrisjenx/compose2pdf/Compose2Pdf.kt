package com.chrisjenx.compose2pdf

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Density
import com.chrisjenx.compose2pdf.internal.PdfRenderer

/**
 * Exception thrown when PDF rendering fails.
 */
class Compose2PdfException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/**
 * Renders Compose content to a PDF, automatically paginating when content overflows.
 *
 * With [PdfPagination.AUTO] (the default), content is automatically split across multiple
 * pages. Direct children of [content] are treated as "keep-together" units — if a child
 * would straddle a page boundary, it is pushed to the next page. For best results, place
 * content items as direct children rather than wrapping in a single `Column`.
 *
 * @param config Page size and margins. Defaults to A4.
 * @param density Controls the pixel resolution used during Compose layout. A density of 2f means
 *   each PDF point maps to 2x2 pixels during rendering. Higher values improve anti-aliasing
 *   quality (especially for raster mode) at the cost of memory. 2f is a good default that
 *   balances quality and performance.
 * @param mode Vector (SVG-based) or raster rendering. Defaults to VECTOR.
 * @param defaultFontFamily The font family applied as the default text style during rendering.
 *   When non-null, content is wrapped in the given font family so both Compose and PDFBox use
 *   the same font, eliminating rendering mismatch. Defaults to [InterFontFamily] (bundled Inter).
 *   Pass null to use system fonts instead, or supply your own [FontFamily].
 * @param pagination Controls page splitting. [PdfPagination.AUTO] automatically paginates
 *   overflowing content. [PdfPagination.SINGLE_PAGE] clips to a single page.
 * @param content The composable content to render.
 * @return A valid PDF as a ByteArray.
 * @throws Compose2PdfException if rendering fails.
 *
 * **Thread safety**: This function is not thread-safe. Concurrent calls should be
 * serialized externally (e.g., via a mutex or single-threaded dispatcher).
 */
fun renderToPdf(
    config: PdfPageConfig = PdfPageConfig.A4,
    density: Density = Density(2f),
    mode: RenderMode = RenderMode.VECTOR,
    defaultFontFamily: FontFamily? = InterFontFamily,
    pagination: PdfPagination = PdfPagination.AUTO,
    content: @Composable () -> Unit,
): ByteArray {
    try {
        return PdfRenderer.renderSinglePage(config, density, mode, defaultFontFamily, pagination, content)
    } catch (e: Compose2PdfException) {
        throw e
    } catch (e: IllegalArgumentException) {
        throw e // Don't wrap precondition failures
    } catch (e: Exception) {
        throw Compose2PdfException("Failed to render PDF: ${e.message}", e)
    }
}

/**
 * Renders multiple pages of Compose content to a single PDF.
 *
 * Note: the page count must be known upfront. For dynamic pagination (content-driven page
 * breaks), compute your page count before calling this function.
 *
 * @param pages Number of pages to render.
 * @param config Page size and margins (applied uniformly to all pages). Defaults to A4.
 * @param density Controls the pixel resolution used during Compose layout. A density of 2f means
 *   each PDF point maps to 2x2 pixels during rendering. Higher values improve anti-aliasing
 *   quality (especially for raster mode) at the cost of memory. 2f is a good default that
 *   balances quality and performance.
 * @param mode Vector (SVG-based) or raster rendering. Defaults to VECTOR.
 * @param defaultFontFamily The font family applied as the default text style during rendering.
 *   When non-null, content is wrapped in the given font family so both Compose and PDFBox use
 *   the same font, eliminating rendering mismatch. Defaults to [InterFontFamily] (bundled Inter).
 *   Pass null to use system fonts instead, or supply your own [FontFamily].
 * @param content The composable content for each page. Receives the zero-based page index.
 * @return A valid PDF as a ByteArray.
 * @throws Compose2PdfException if rendering fails.
 * @throws IllegalArgumentException if [pages] is not positive.
 *
 * **Thread safety**: This function is not thread-safe. Concurrent calls should be
 * serialized externally (e.g., via a mutex or single-threaded dispatcher).
 */
fun renderToPdf(
    pages: Int,
    config: PdfPageConfig = PdfPageConfig.A4,
    density: Density = Density(2f),
    mode: RenderMode = RenderMode.VECTOR,
    defaultFontFamily: FontFamily? = InterFontFamily,
    content: @Composable (pageIndex: Int) -> Unit,
): ByteArray {
    try {
        return PdfRenderer.renderMultiPage(pages, config, density, mode, defaultFontFamily, content)
    } catch (e: Compose2PdfException) {
        throw e
    } catch (e: IllegalArgumentException) {
        throw e // Don't wrap precondition failures
    } catch (e: Exception) {
        throw Compose2PdfException("Failed to render multi-page PDF: ${e.message}", e)
    }
}
