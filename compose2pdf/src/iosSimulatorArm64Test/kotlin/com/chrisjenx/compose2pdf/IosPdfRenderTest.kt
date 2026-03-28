package com.chrisjenx.compose2pdf

import androidx.compose.material3.Text
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
    fun renderAllSharedFixtures() {
        val failures = mutableListOf<String>()

        for (fixture in sharedFixtures) {
            try {
                val bytes = renderToPdf(config = fixture.config) {
                    fixture.content()
                }
                assertTrue(bytes.size > 50, "${fixture.name}: PDF too small (${bytes.size} bytes)")
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
        assertEquals("%PDF", header, "$label: not a valid PDF: $header")
        println("$label: ${bytes.size} bytes OK")
    }
}
