@file:OptIn(InternalComposeUiApi::class)

package com.chrisjenx.compose2pdf.test

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ProvideTextStyle
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
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
import org.apache.pdfbox.rendering.PDFRenderer
import java.awt.RenderingHints
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MultipageTest {

    private val config = PdfPageConfig.A4
    private val density = Density(2f)
    private val renderDpi = 144f

    private val reportDir = File("build/reports/fidelity")
    private val imagesDir = File(reportDir, "images")

    @Test
    fun `multipage PDF renders all pages`() {
        imagesDir.mkdirs()
        val pageCount = 3
        val pageContents: List<@Composable () -> Unit> = listOf(
            {
                Column(Modifier.fillMaxSize().padding(24.dp)) {
                    Text("Page 1: Cover", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1A237E))
                    Spacer(Modifier.height(16.dp))
                    Box(Modifier.fillMaxWidth().height(80.dp).background(Color(0xFF1A237E), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                        Text("Multi-Page Document", color = Color.White, fontSize = 20.sp)
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("This tests that each page in a multi-page PDF renders correctly.", fontSize = 14.sp)
                }
            },
            {
                Column(Modifier.fillMaxSize().padding(24.dp)) {
                    Text("Page 2: Data", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    for (i in 1..8) {
                        Row(Modifier.fillMaxWidth().background(if (i % 2 == 0) Color(0xFFF5F5F5) else Color.White).padding(8.dp)) {
                            Text("Row $i", Modifier.weight(1f), fontSize = 12.sp)
                            Text("Value ${i * 42}", Modifier.weight(1f), fontSize = 12.sp)
                            Text("${i * 10}%", Modifier.weight(1f), fontSize = 12.sp)
                        }
                    }
                }
            },
            {
                Column(Modifier.fillMaxSize().padding(24.dp)) {
                    Text("Page 3: Summary", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))
                    Box(Modifier.fillMaxWidth().height(60.dp).background(Color(0xFF2E7D32), RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                        Text("Status: Complete", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("All items processed successfully.", fontSize = 14.sp)
                    Spacer(Modifier.height(4.dp))
                    Text("Total: $12,757.97", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            },
        )

        for (mode in RenderMode.entries) {
            val modeName = mode.name.lowercase()

            val pdfBytes = renderToPdf(pages = pageCount, config = config, density = density, mode = mode) { pageIndex ->
                ProvideTextStyle(TextStyle(fontFamily = InterFontFamily)) {
                    pageContents[pageIndex]()
                }
            }
            File(imagesDir, "multipage-$modeName.pdf").writeBytes(pdfBytes)

            Loader.loadPDF(pdfBytes).use { doc ->
                assertEquals(pageCount, doc.numberOfPages, "$mode: wrong page count")
                val renderer = PDFRenderer(doc)
                renderer.setRenderingHints(RenderingHints(mapOf(
                    RenderingHints.KEY_ANTIALIASING to RenderingHints.VALUE_ANTIALIAS_ON,
                    RenderingHints.KEY_TEXT_ANTIALIASING to RenderingHints.VALUE_TEXT_ANTIALIAS_ON,
                    RenderingHints.KEY_FRACTIONALMETRICS to RenderingHints.VALUE_FRACTIONALMETRICS_ON,
                    RenderingHints.KEY_STROKE_CONTROL to RenderingHints.VALUE_STROKE_PURE,
                    RenderingHints.KEY_RENDERING to RenderingHints.VALUE_RENDER_QUALITY,
                    RenderingHints.KEY_INTERPOLATION to RenderingHints.VALUE_INTERPOLATION_BICUBIC,
                )))

                for (i in 0 until pageCount) {
                    val pageW = (config.width.value * density.density).toInt()
                    val pageH = (config.height.value * density.density).toInt()
                    val contentW = (config.contentWidth.value * density.density).toInt()
                    val contentH = (config.contentHeight.value * density.density).toInt()

                    val contentImage = renderComposeReference(contentW, contentH, density) {
                        ProvideTextStyle(TextStyle(fontFamily = InterFontFamily)) {
                            pageContents[i]()
                        }
                    }
                    val composeImage = compositeOnPage(contentImage, pageW, pageH, config, density)
                    saveImage(ImageMetrics.flattenOnWhite(composeImage), imagesDir, "multipage-$modeName-p${i}-compose.png")

                    val pdfImage = renderer.renderImageWithDPI(i, renderDpi)
                    saveImage(pdfImage, imagesDir, "multipage-$modeName-p${i}-pdf.png")

                    val rmse = ImageMetrics.computeRmse(composeImage, pdfImage)
                    val diff = ImageMetrics.generateDiffImage(composeImage, pdfImage)
                    saveImage(diff, imagesDir, "multipage-$modeName-p${i}-diff.png")

                    val threshold = if (mode == RenderMode.VECTOR) 0.15 else 0.001
                    assertTrue(
                        rmse <= threshold,
                        "$mode page $i: RMSE ${"%.4f".format(rmse)} > threshold $threshold",
                    )
                }
            }
        }
    }
}
