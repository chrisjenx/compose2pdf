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

                    val pdfImage = rasterizePdf(doc, renderDpi, page = i)
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

    @Test
    fun `multipage PDF renders all pages with margins`() {
        imagesDir.mkdirs()
        val marginConfig = PdfPageConfig.A4WithMargins
        val pageCount = 2
        val pageContents: List<@Composable () -> Unit> = listOf(
            {
                Column(Modifier.fillMaxSize().background(Color(0xFFFFF3E0)).padding(24.dp)) {
                    Text("Page 1", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))
                    Text("With Normal margins (72dp). The orange background must occupy only the content area.", fontSize = 14.sp)
                }
            },
            {
                Column(Modifier.fillMaxSize().background(Color(0xFFE8F5E9)).padding(24.dp)) {
                    Text("Page 2", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))
                    Text("Same margins on every page.", fontSize = 14.sp)
                }
            },
        )

        for (mode in listOf(RenderMode.VECTOR, RenderMode.RASTER)) {
            val modeName = mode.name.lowercase()
            val pdfBytes = renderToPdf(pages = pageCount, config = marginConfig, density = density, mode = mode) { i ->
                pageContents[i]()
            }
            File(imagesDir, "multipage-margins-$modeName.pdf").writeBytes(pdfBytes)
            Loader.loadPDF(pdfBytes).use { doc ->
                assertEquals(pageCount, doc.numberOfPages)
                for (i in 0 until pageCount) {
                    val pdfImage = rasterizePdf(doc, renderDpi, page = i)
                    saveImage(pdfImage, imagesDir, "multipage-margins-$modeName-p${i}.png")

                    // 144 DPI -> 2 px per point. Top margin (72pt) = 144 px.
                    val pxPerPt = renderDpi / 72f
                    val marginPxTop = (marginConfig.margins.top.value * pxPerPt).toInt()
                    val marginPxLeft = (marginConfig.margins.left.value * pxPerPt).toInt()
                    // A pixel halfway into the top-left margin should be white (page background).
                    val rgbMargin = pdfImage.getRGB(marginPxLeft / 2, marginPxTop / 2) and 0xFFFFFF
                    assertTrue(
                        rgbMargin > 0xF0F0F0,
                        "Expected white in top-left margin at p=$i mode=$modeName, got ${"%06X".format(rgbMargin)}",
                    )
                    // A pixel just inside the content area should be coloured.
                    val rgbContent = pdfImage.getRGB(marginPxLeft + 4, marginPxTop + 4) and 0xFFFFFF
                    assertTrue(
                        rgbContent < 0xF0F0F0,
                        "Expected non-white inside content area at p=$i mode=$modeName, got ${"%06X".format(rgbContent)}",
                    )
                }
            }
        }
    }
}
