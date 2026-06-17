package com.chrisjenx.compose2pdf.internal

import android.content.Context
import android.graphics.pdf.PdfDocument
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Density
import com.chrisjenx.compose2pdf.PdfPageConfig
import com.chrisjenx.compose2pdf.PdfPagination
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.ceil

/**
 * Android-specific PDF renderer using [android.graphics.pdf.PdfDocument].
 *
 * Uses [OffScreenComposeRenderer] to render Compose content in a headless virtual display,
 * then draws the composed View directly onto [PdfDocument]'s Canvas. Because the PdfDocument
 * Canvas is Skia-backed, Canvas draw operations (text, paths, shapes) produce vector PDF
 * primitives — text is selectable and paths are resolution-independent.
 *
 * All rendering (Compose composition + View.draw) happens on the main thread to avoid
 * RenderNode recording conflicts.
 *
 * Limitation: [android.graphics.pdf.PdfDocument] does not support link annotations,
 * so [PdfLink] annotations are ignored on Android.
 */
internal object AndroidPdfRenderer {

    suspend fun render(
        context: Context,
        config: PdfPageConfig,
        density: Density,
        defaultFontFamily: FontFamily?,
        pagination: PdfPagination,
        content: @Composable () -> Unit,
        outputStream: OutputStream,
    ) {
        val contentWidthPt = config.contentWidth.value
        val contentHeightPt = config.contentHeight.value
        val widthPx = (contentWidthPt * density.density).toInt()

        val renderer = OffScreenComposeRenderer(context)
        try {
            val composeView = renderer.render(widthPx, density, config, defaultFontFamily, content)

            // Build the PDF on the main thread. View.draw() must run on the same
            // thread that owns the Compose RenderNode to avoid "recording in progress" errors.
            val pdfBytes = buildPdfOnMainThread(
                composeView, config, density, pagination,
            )
            outputStream.write(pdfBytes)
        } finally {
            renderer.close()
        }
    }

    private suspend fun buildPdfOnMainThread(
        composeView: View,
        config: PdfPageConfig,
        density: Density,
        pagination: PdfPagination,
    ): ByteArray = suspendCancellableCoroutine { continuation ->
        Handler(Looper.getMainLooper()).post {
            try {
                val totalHeightPx = composeView.measuredHeight
                val contentWidthPt = config.contentWidth.value
                val contentHeightPt = config.contentHeight.value
                val pageWidthPt = config.width.value.toInt()
                val pageHeightPt = config.height.value.toInt()
                val marginLeftPt = config.margins.left.value
                val marginTopPt = config.margins.top.value
                val scale = 1f / density.density

                val pdfDocument = PdfDocument()
                try {
                    when (pagination) {
                        PdfPagination.SINGLE_PAGE -> {
                            addPage(
                                pdfDocument, composeView, 1,
                                pageWidthPt, pageHeightPt,
                                contentWidthPt, contentHeightPt,
                                marginLeftPt, marginTopPt,
                                scale, yOffsetPx = 0f,
                            )
                        }
                        PdfPagination.AUTO -> {
                            val contentHeightPx = (contentHeightPt * density.density).toInt()
                            val numPages = if (totalHeightPx <= contentHeightPx) 1
                            else ceil(totalHeightPx.toFloat() / contentHeightPx).toInt()

                            for (i in 0 until numPages) {
                                addPage(
                                    pdfDocument, composeView, i + 1,
                                    pageWidthPt, pageHeightPt,
                                    contentWidthPt, contentHeightPt,
                                    marginLeftPt, marginTopPt,
                                    scale, yOffsetPx = i * contentHeightPx.toFloat(),
                                )
                            }
                        }
                    }

                    val baos = ByteArrayOutputStream()
                    pdfDocument.writeTo(baos)
                    continuation.resume(baos.toByteArray())
                } finally {
                    pdfDocument.close()
                }
            } catch (e: Exception) {
                continuation.resumeWithException(e)
            }
        }
    }

    private fun addPage(
        document: PdfDocument,
        composeView: View,
        pageNumber: Int,
        pageWidthPt: Int,
        pageHeightPt: Int,
        contentWidthPt: Float,
        contentHeightPt: Float,
        marginLeftPt: Float,
        marginTopPt: Float,
        scale: Float,
        yOffsetPx: Float,
    ) {
        val pageInfo = PdfDocument.PageInfo.Builder(pageWidthPt, pageHeightPt, pageNumber).create()
        val page = document.startPage(pageInfo)
        val canvas = page.canvas

        canvas.save()
        canvas.clipRect(
            marginLeftPt,
            marginTopPt,
            marginLeftPt + contentWidthPt,
            marginTopPt + contentHeightPt,
        )
        canvas.translate(marginLeftPt, marginTopPt)
        canvas.scale(scale, scale)
        canvas.translate(0f, -yOffsetPx)
        composeView.draw(canvas)
        canvas.restore()

        document.finishPage(page)
    }
}
