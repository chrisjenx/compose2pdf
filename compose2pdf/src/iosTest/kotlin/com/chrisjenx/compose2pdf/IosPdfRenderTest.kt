package com.chrisjenx.compose2pdf

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
import com.chrisjenx.compose2pdf.fixtures.sharedFixtures
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IosPdfRenderTest {

    @Test
    fun basicRender_producesValidPdf() {
        val bytes = renderToPdf {
            Text("Hello from iOS!", fontSize = 24.sp)
        }
        assertValidPdf(bytes, "basicRender")
    }

    @Test
    fun renderWithMargins_producesValidPdf() {
        val bytes = renderToPdf(config = PdfPageConfig.A4WithMargins) {
            Text("PDF with margins", fontSize = 20.sp)
        }
        assertValidPdf(bytes, "margins")
    }

    @Test
    fun renderSinglePage_producesValidPdf() {
        val bytes = renderToPdf(pagination = PdfPagination.SINGLE_PAGE) {
            Text("Single page content", fontSize = 16.sp)
        }
        assertValidPdf(bytes, "singlePage")
    }

    @Test
    fun renderShapes_producesValidPdf() {
        val bytes = renderToPdf {
            Column(Modifier.fillMaxWidth().padding(24.dp)) {
                Box(Modifier.fillMaxWidth().height(80.dp).background(Color.Blue))
                Spacer(Modifier.height(16.dp))
                Box(Modifier.fillMaxWidth().height(60.dp).background(Color.Red))
                Spacer(Modifier.height(16.dp))
                Text("Below the shapes", fontSize = 18.sp)
            }
        }
        assertValidPdf(bytes, "shapes")
    }

    @Test
    fun renderLetterConfig_producesValidPdf() {
        val bytes = renderToPdf(config = PdfPageConfig.Letter) {
            Text("US Letter size PDF", fontSize = 20.sp)
        }
        assertValidPdf(bytes, "letter")
    }

    @Test
    fun renderAllSharedFixtures() {
        val failures = mutableListOf<String>()

        for (fixture in sharedFixtures) {
            try {
                val bytes = renderToPdf(config = fixture.config) {
                    fixture.content()
                }
                assertTrue(bytes.size > 50, "${fixture.name}: PDF too small (${bytes.size} bytes)")
                // Check PDF magic bytes
                val header = bytes.sliceArray(0..3).decodeToString()
                assertEquals("%PDF", header, "${fixture.name}: not a valid PDF")
            } catch (e: Exception) {
                failures.add("${fixture.name}: ${e.message}")
            }
        }

        if (failures.isNotEmpty()) {
            println("\n=== iOS Fixture Failures ===")
            failures.forEach { println("  - $it") }
            println("============================\n")
        }
        assertTrue(failures.isEmpty(), "${failures.size} fixtures failed:\n${failures.joinToString("\n")}")
    }

    private fun assertValidPdf(bytes: ByteArray, label: String) {
        assertTrue(bytes.size > 50, "$label: PDF too small (${bytes.size} bytes)")
        val header = bytes.sliceArray(0..3).decodeToString()
        assertEquals("%PDF", header, "$label: not a valid PDF (header: $header)")
        println("$label: ${bytes.size} bytes OK")
    }
}
