@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package com.chrisjenx.compose2pdf

import androidx.compose.material3.Text
import androidx.compose.ui.unit.sp
import com.chrisjenx.compose2pdf.fixtures.sharedFixtures
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSProcessInfo
import platform.Foundation.create
import platform.Foundation.writeToFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IosPdfRenderTest {

    // Output dir for PDFs — picked up by the fidelity test report.
    // Uses IOS_PDF_OUTPUT_DIR env var if set, otherwise falls back to tmp.
    private val outputDir: String by lazy {
        val envDir = NSProcessInfo.processInfo.environment["IOS_PDF_OUTPUT_DIR"] as? String
        val dir = envDir ?: "/tmp/compose2pdf-ios-test-output"
        NSFileManager.defaultManager.createDirectoryAtPath(
            dir, withIntermediateDirectories = true, attributes = null, error = null,
        )
        dir
    }

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
                savePdf("${fixture.name}-ios.pdf", bytes)
            } catch (e: Exception) {
                failures.add("${fixture.name}: ${e.message}")
            }
        }

        if (failures.isNotEmpty()) {
            println("\n=== iOS Fixture Failures ===")
            failures.forEach { println("  - $it") }
            println("============================\n")
        }
        println("iOS PDFs saved to: $outputDir")
        assertTrue(failures.isEmpty(), "${failures.size} fixtures failed:\n${failures.joinToString("\n")}")
    }

    private fun savePdf(name: String, bytes: ByteArray) {
        bytes.usePinned { pinned ->
            val nsData = NSData.create(bytes = pinned.addressOf(0), length = bytes.size.toULong())
            nsData.writeToFile("$outputDir/$name", atomically = true)
        }
    }

    private fun assertValidPdf(bytes: ByteArray, label: String) {
        assertTrue(bytes.size > 50, "$label: PDF too small (${bytes.size} bytes)")
        val header = bytes.sliceArray(0..3).decodeToString()
        assertEquals("%PDF", header, "$label: not a valid PDF: $header")
        println("$label: ${bytes.size} bytes OK")
    }
}
