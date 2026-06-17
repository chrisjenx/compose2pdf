package com.chrisjenx.compose2pdf

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer as AndroidPdfRenderer
import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.services.storage.TestStorage
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Renders all shared fidelity fixtures on Android and saves PDFs via TestStorage
 * for cross-platform comparison with the JVM fidelity report.
 *
 * PDF output naming convention: `{fixture-name}-android.pdf`
 * These are extracted to:
 *   build/outputs/managed_device_android_test_additional_output/debug/{device}/
 */
class AndroidFidelityTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun renderAllFidelityFixtures() = runBlocking {
        val results = mutableListOf<String>()

        for (fixture in androidFidelityFixtures) {
            try {
                val bytes = renderToPdf(
                    context = context,
                    config = fixture.config,
                ) {
                    fixture.content()
                }

                // Validate PDF
                assertTrue(bytes.size > 100, "${fixture.name}: PDF too small (${bytes.size} bytes)")
                assertTrue(
                    bytes[0] == '%'.code.toByte() && bytes[1] == 'P'.code.toByte(),
                    "${fixture.name}: not a valid PDF",
                )

                // Verify we can open and read the PDF
                val pageCount = countPdfPages(bytes)
                assertTrue(pageCount >= 1, "${fixture.name}: PDF has no pages")

                // Verify rendered page has content (not blank)
                val bitmap = renderFirstPageToBitmap(bytes)
                assertTrue(
                    hasNonWhitePixels(bitmap),
                    "${fixture.name}: rendered page appears blank",
                )

                // Save via TestStorage for extraction
                saveTestOutput("${fixture.name}-android.pdf", bytes)
                results.add("PASS: ${fixture.name} (${bytes.size} bytes, $pageCount pages)")
            } catch (e: Exception) {
                results.add("FAIL: ${fixture.name} - ${e.message}")
            }
        }

        // Print summary
        println("\n=== Android Fidelity Fixtures ===")
        results.forEach { println(it) }
        println("=================================\n")

        val failures = results.filter { it.startsWith("FAIL") }
        assertTrue(failures.isEmpty(), "Fixture failures:\n${failures.joinToString("\n")}")
    }

    // --- Helpers ---

    private fun countPdfPages(bytes: ByteArray): Int {
        val file = writeTempFile(bytes)
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            AndroidPdfRenderer(fd).use { it.pageCount }
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
        val file = File.createTempFile("compose2pdf_fidelity", ".pdf", context.cacheDir)
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
