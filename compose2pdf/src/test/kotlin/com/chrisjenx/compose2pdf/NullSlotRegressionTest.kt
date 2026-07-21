package com.chrisjenx.compose2pdf

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.apache.pdfbox.Loader
import java.io.File
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.fail

/**
 * Pins the header/footer-less (null-slot) render path against golden content streams.
 * The goldens were captured on this branch AFTER the raster last-slice stretch fix but
 * BEFORE the header/footer slot code was added — NOT a pre-any-change baseline. Their
 * purpose is to guard the no-slot path against unintended regressions from later slot-code
 * changes (e.g. this test catching whether the imageCache fix or the stamp-loop dedup
 * accidentally altered null-slot output), not to prove parity with pre-branch behavior.
 * Compares decoded page content streams because PDFBox writes a time-seeded document ID
 * into every save. Shapes-only fixture: no text, so the streams are platform-deterministic.
 *
 * If this fails after an INTENTIONAL rendering change, delete the golden files and
 * re-run the test twice to regenerate and verify.
 */
class NullSlotRegressionTest {

    private val goldenDir = File("src/test/resources/golden")

    private val fixture: @Composable () -> Unit = {
        Box(Modifier.fillMaxWidth().height(400.dp).background(Color(0xFF1565C0)))
        Box(Modifier.fillMaxWidth().height(400.dp).background(Color(0xFFE3F2FD)))
        Box(Modifier.fillMaxWidth().height(1047.dp).background(Color(0xFFB71C1C))) // forces a partial last slice
    }

    private fun check(mode: RenderMode, goldenName: String) {
        val bytes = renderToPdf(config = PdfPageConfig.A4WithMargins, mode = mode) { fixture() }
        val goldenFile = File(goldenDir, goldenName)
        if (!goldenFile.exists()) {
            goldenDir.mkdirs()
            goldenFile.writeBytes(bytes)
            fail("Golden $goldenName was missing — generated it. Commit the file and re-run.")
        }
        Loader.loadPDF(goldenFile.readBytes()).use { golden ->
            Loader.loadPDF(bytes).use { fresh ->
                assertEquals(golden.numberOfPages, fresh.numberOfPages, "$mode page count changed")
                for (i in 0 until golden.numberOfPages) {
                    assertContentEquals(
                        golden.getPage(i).contents.readBytes(),
                        fresh.getPage(i).contents.readBytes(),
                        "$mode page $i content stream differs from pre-slots baseline",
                    )
                }
            }
        }
    }

    @Test
    fun `null-slot vector output matches baseline content streams`() = check(RenderMode.VECTOR, "null-slot-vector.pdf")

    @Test
    fun `null-slot raster output matches baseline content streams`() = check(RenderMode.RASTER, "null-slot-raster.pdf")
}
