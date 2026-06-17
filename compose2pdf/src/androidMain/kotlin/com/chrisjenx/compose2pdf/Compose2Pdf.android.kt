package com.chrisjenx.compose2pdf

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Density
import com.chrisjenx.compose2pdf.internal.AndroidPdfRenderer
import java.io.ByteArrayOutputStream
import java.io.OutputStream

/**
 * Renders Compose content to a PDF and writes it to [outputStream].
 *
 * Uses Android's native [android.graphics.pdf.PdfDocument] (zero external dependencies).
 * Compose content is rendered off-screen via a headless virtual display, then drawn
 * directly onto PdfDocument's Skia-backed Canvas — producing vector PDF output
 * (selectable text, resolution-independent paths).
 *
 * This is a suspend function because off-screen Compose rendering on Android requires
 * the main thread and asynchronous composition. Call from any coroutine scope.
 *
 * Note: link annotations ([PdfLink]) are not supported on Android because
 * [android.graphics.pdf.PdfDocument] does not expose annotation APIs.
 *
 * @param context Android context. Does not need to be an Activity — any Context works.
 * @param outputStream The stream to write the PDF to. Not closed by this function.
 * @param config Page size and margins. Defaults to A4.
 * @param density Controls the pixel resolution used during Compose layout. 2f is a good default.
 * @param defaultFontFamily The default text font family. Pass null to use system fonts.
 * @param pagination Controls page splitting. Defaults to [PdfPagination.AUTO].
 * @param content The composable content to render.
 * @throws Compose2PdfException if rendering fails.
 */
suspend fun renderToPdf(
    context: Context,
    outputStream: OutputStream,
    config: PdfPageConfig = PdfPageConfig.A4,
    density: Density = Density(2f),
    defaultFontFamily: FontFamily? = null,
    pagination: PdfPagination = PdfPagination.AUTO,
    content: @Composable () -> Unit,
) {
    try {
        AndroidPdfRenderer.render(context, config, density, defaultFontFamily, pagination, content, outputStream)
    } catch (e: Compose2PdfException) {
        throw e
    } catch (e: IllegalArgumentException) {
        throw e
    } catch (e: Exception) {
        throw Compose2PdfException("Failed to render PDF: ${e.message}", e)
    }
}

/**
 * Renders Compose content to a PDF as a ByteArray.
 *
 * Uses Android's native [android.graphics.pdf.PdfDocument] (zero external dependencies).
 * Compose content is rendered off-screen via a headless virtual display, then drawn
 * directly onto PdfDocument's Skia-backed Canvas — producing vector PDF output.
 *
 * @param context Android context. Does not need to be an Activity — any Context works.
 * @param config Page size and margins. Defaults to A4.
 * @param density Controls the pixel resolution used during Compose layout. 2f is a good default.
 * @param defaultFontFamily The default text font family. Pass null to use system fonts.
 * @param pagination Controls page splitting. Defaults to [PdfPagination.AUTO].
 * @param content The composable content to render.
 * @return A valid PDF as a ByteArray.
 * @throws Compose2PdfException if rendering fails.
 */
suspend fun renderToPdf(
    context: Context,
    config: PdfPageConfig = PdfPageConfig.A4,
    density: Density = Density(2f),
    defaultFontFamily: FontFamily? = null,
    pagination: PdfPagination = PdfPagination.AUTO,
    content: @Composable () -> Unit,
): ByteArray {
    val baos = ByteArrayOutputStream()
    renderToPdf(context, baos, config, density, defaultFontFamily, pagination, content)
    return baos.toByteArray()
}
