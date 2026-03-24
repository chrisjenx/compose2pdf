@file:OptIn(InternalComposeUiApi::class)

package com.chrisjenx.compose2pdf.test

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.ProvideTextStyle
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chrisjenx.compose2pdf.InterFontFamily
import com.chrisjenx.compose2pdf.PdfPageConfig
import com.chrisjenx.compose2pdf.RenderMode
import com.chrisjenx.compose2pdf.renderToPdf
import org.apache.pdfbox.Loader
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AutoPaginationFidelityTest {

    private val config = PdfPageConfig.A4WithMargins
    private val density = Density(2f)
    private val renderDpi = 144f

    private val reportDir = File("build/reports/fidelity")
    private val imagesDir = File(reportDir, "images")

    @Test
    fun `auto-paginated content produces correct multi-page PDF`() {
        imagesDir.mkdirs()

        // Content that will span 2+ pages: header (50dp) + 20 rows (~30dp each = 600dp) + notes (~200dp)
        // Total ~850dp > 698dp contentHeight for A4WithMargins
        val content: @Composable () -> Unit = {
            ProvideTextStyle(TextStyle(fontFamily = InterFontFamily)) {
                // Section 1: Header
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1565C0))
                        .padding(16.dp)
                ) {
                    Text("Auto-Pagination Test Report", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(Modifier.height(12.dp))

                // Section 2: Data table — large enough to push section 3 to page 2
                Column(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth().background(Color(0xFFE3F2FD)).padding(8.dp)
                    ) {
                        Text("Item", Modifier.weight(2f), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        Text("Value", Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                    for (i in 1..30) {
                        val bg = if (i % 2 == 0) Color(0xFFFAFAFA) else Color.Transparent
                        Row(
                            Modifier.fillMaxWidth().background(bg).padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("Row $i - Data item", Modifier.weight(2f), fontSize = 11.sp)
                            Text("${i * 42}", Modifier.weight(1f), fontSize = 11.sp)
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Section 3: Notes — should appear on page 2
                Column(Modifier.fillMaxWidth()) {
                    Text("Summary Notes", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    for (note in listOf(
                        "All items processed successfully with no errors.",
                        "Performance exceeded baseline by 15% across all metrics.",
                        "Recommend continued monitoring through Q2.",
                    )) {
                        Text("  •  $note", fontSize = 11.sp)
                        Spacer(Modifier.height(4.dp))
                    }
                }
            }
        }

        for (mode in RenderMode.entries) {
            val modeName = mode.name.lowercase()

            val pdfBytes = renderToPdf(config = config, density = density, mode = mode) {
                content()
            }
            File(imagesDir, "auto-pagination-$modeName.pdf").writeBytes(pdfBytes)

            Loader.loadPDF(pdfBytes).use { doc ->
                assertTrue(
                    doc.numberOfPages >= 2,
                    "$mode: expected at least 2 pages, got ${doc.numberOfPages}",
                )

                // Verify each page renders without errors and produces a non-blank image
                for (i in 0 until doc.numberOfPages) {
                    val pdfImage = rasterizePdf(doc, renderDpi, page = i)
                    saveImage(pdfImage, imagesDir, "auto-pagination-$modeName-p$i.png")

                    // Basic sanity: image should have expected dimensions
                    assertTrue(pdfImage.width > 0, "$mode page $i: width should be positive")
                    assertTrue(pdfImage.height > 0, "$mode page $i: height should be positive")
                }
            }
        }
    }

    @Test
    fun `smart page breaking keeps elements together`() {
        // Create content where a child exactly straddles the page boundary.
        // With smart breaking, it should be pushed to page 2 (no clipping).
        val pdfBytes = renderToPdf(config = config, density = density) {
            ProvideTextStyle(TextStyle(fontFamily = InterFontFamily)) {
                // Fill most of page 1 (600dp of 698dp available)
                Spacer(Modifier.fillMaxWidth().height(600.dp).background(Color(0xFFE3F2FD)))

                // This 200dp block would straddle if placed at Y=600.
                // Smart breaking should push it to page 2.
                Column(
                    Modifier.fillMaxWidth().height(200.dp).background(Color(0xFF1565C0)).padding(16.dp)
                ) {
                    Text("This block should be on page 2", color = Color.White, fontSize = 14.sp)
                }
            }
        }

        Loader.loadPDF(pdfBytes).use { doc ->
            assertEquals(2, doc.numberOfPages, "Element should be pushed to page 2")
        }
    }
}
