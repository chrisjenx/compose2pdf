package com.chrisjenx.compose2pdf

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer as AndroidPdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.services.storage.TestStorage
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AndroidPdfRenderTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun basicRender_producesValidPdf() = runBlocking {
        val bytes = renderToPdf(context) {
            Text("Hello from Compose2PDF on Android!", fontSize = 24.sp)
        }

        assertValidPdf(bytes)
        saveTestOutput("basic_render.pdf", bytes)
    }

    @Test
    fun renderWithMargins_producesValidPdf() = runBlocking {
        val bytes = renderToPdf(
            context,
            config = PdfPageConfig.A4WithMargins,
        ) {
            Text("PDF with margins", fontSize = 20.sp)
        }

        assertValidPdf(bytes)
        saveTestOutput("margins_render.pdf", bytes)
    }

    @Test
    fun renderSinglePage_hasOnePage() = runBlocking {
        val bytes = renderToPdf(
            context,
            pagination = PdfPagination.SINGLE_PAGE,
        ) {
            Text("Single page content", fontSize = 16.sp)
        }

        assertValidPdf(bytes)
        val pageCount = countPdfPages(bytes)
        assertEquals(1, pageCount, "SINGLE_PAGE should produce exactly 1 page")
        saveTestOutput("single_page.pdf", bytes)
    }

    @Test
    fun renderAutoPagination_tallContent_producesMultiplePages() = runBlocking {
        val bytes = renderToPdf(
            context,
            config = PdfPageConfig.A4WithMargins,
            pagination = PdfPagination.AUTO,
        ) {
            Column {
                // Generate content taller than one A4 page (~700pt content height)
                repeat(50) { i ->
                    Text(
                        text = "Line $i: The quick brown fox jumps over the lazy dog",
                        fontSize = 14.sp,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
            }
        }

        assertValidPdf(bytes)
        val pageCount = countPdfPages(bytes)
        assertTrue(pageCount > 1, "Tall content should produce multiple pages, got $pageCount")
        saveTestOutput("auto_pagination.pdf", bytes)
    }

    @Test
    fun renderWithComposableContent_producesNonEmptyPages() = runBlocking {
        val bytes = renderToPdf(context) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                        .background(Color.Blue),
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text("Below the blue box", fontSize = 18.sp)
            }
        }

        assertValidPdf(bytes)

        // Render first page to bitmap and verify it has non-white pixels
        val bitmap = renderFirstPageToBitmap(bytes)
        assertTrue(bitmap.width > 0 && bitmap.height > 0)
        assertTrue(hasNonWhitePixels(bitmap), "Rendered page should have visible content")
        saveTestOutput("composable_content.pdf", bytes)
    }

    @Test
    fun renderLetterConfig_producesValidPdf() = runBlocking {
        val bytes = renderToPdf(
            context,
            config = PdfPageConfig.Letter,
        ) {
            Text("US Letter size PDF", fontSize = 20.sp)
        }

        assertValidPdf(bytes)
        saveTestOutput("letter_size.pdf", bytes)
    }

    // --- Helpers ---

    private fun assertValidPdf(bytes: ByteArray) {
        assertTrue(bytes.size > 100, "PDF should be non-trivial size, was ${bytes.size} bytes")
        // PDF magic bytes: %PDF
        assertEquals('%'.code.toByte(), bytes[0], "PDF should start with %PDF")
        assertEquals('P'.code.toByte(), bytes[1])
        assertEquals('D'.code.toByte(), bytes[2])
        assertEquals('F'.code.toByte(), bytes[3])
    }

    private fun countPdfPages(bytes: ByteArray): Int {
        val file = writeTempFile(bytes)
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            AndroidPdfRenderer(fd).use { renderer ->
                renderer.pageCount
            }
        }
    }

    private fun renderFirstPageToBitmap(bytes: ByteArray): Bitmap {
        val file = writeTempFile(bytes)
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            AndroidPdfRenderer(fd).use { renderer ->
                renderer.openPage(0).use { page ->
                    val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                    page.render(bitmap, null, null, AndroidPdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmap
                }
            }
        }
    }

    private fun hasNonWhitePixels(bitmap: Bitmap): Boolean {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val white = android.graphics.Color.WHITE
        return pixels.any { it != white && it != 0 }
    }

    private fun writeTempFile(bytes: ByteArray): File {
        val file = File.createTempFile("compose2pdf_test", ".pdf", context.cacheDir)
        file.writeBytes(bytes)
        file.deleteOnExit()
        return file
    }

    private fun saveTestOutput(name: String, bytes: ByteArray) {
        try {
            TestStorage().openOutputFile(name).use { it.write(bytes) }
        } catch (_: Exception) {
            // TestStorage may not be available in all environments
        }
    }
}
