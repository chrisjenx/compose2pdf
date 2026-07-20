package com.chrisjenx.compose2pdf.test

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chrisjenx.compose2pdf.PdfPageConfig
import com.chrisjenx.compose2pdf.PdfPageInfo
import com.chrisjenx.compose2pdf.RenderMode
import com.chrisjenx.compose2pdf.renderToPdf
import org.apache.pdfbox.Loader
import java.awt.image.BufferedImage
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class HeaderFooterFidelityTest {

    private val config = PdfPageConfig.A4WithMargins
    private val density = Density(2f)
    private val renderDpi = 144f // 2px per pt

    private val reportDir = File("build/reports/fidelity")
    private val imagesDir = File(reportDir, "images")

    private val header: @Composable (PdfPageInfo) -> Unit = {
        Row(
            Modifier.fillMaxWidth().background(Color(0xFF1565C0)).padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Acme Corp", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }

    private val footer: @Composable (PdfPageInfo) -> Unit = { info ->
        Row(
            Modifier.fillMaxWidth().background(Color(0xFFE3F2FD)).padding(6.dp),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        ) {
            Text("Page ${info.pageNumber} of ${info.pageCount}", fontSize = 9.sp, color = Color(0xFF555555))
        }
    }

    @Test
    fun `header and footer bands render on every page in both modes`() {
        imagesDir.mkdirs()

        for (mode in RenderMode.entries) {
            val modeName = mode.name.lowercase()
            val pdfBytes = renderToPdf(
                config = config, density = density, mode = mode,
                header = header, footer = footer,
            ) {
                for (i in 1..40) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
                        Text("Row $i - fidelity data item", Modifier.weight(2f), fontSize = 11.sp)
                        Text("${i * 42}", Modifier.weight(1f), fontSize = 11.sp)
                    }
                }
            }
            File(imagesDir, "header-footer-$modeName.pdf").writeBytes(pdfBytes)

            Loader.loadPDF(pdfBytes).use { doc ->
                assertTrue(doc.numberOfPages >= 2, "$mode: expected multi-page output, got ${doc.numberOfPages}")
                for (i in 0 until doc.numberOfPages) {
                    val img = rasterizePdf(doc, renderDpi, page = i)
                    saveImage(img, imagesDir, "header-footer-$modeName-p$i.png")

                    // Bands now sit within the page margins rather than being added on top of
                    // them, so their exact offset depends on band height. Scan the whole top
                    // margin band (rows above the 72pt body-top line, i.e. y in [0, 144) at
                    // 144 DPI) and the whole bottom margin band (rows below the 72pt body-bottom
                    // line) for any non-whitish ink, instead of hardcoding an exact sample point.
                    val xMid = img.width / 2
                    assertTrue(
                        img.hasInkInRows(xMid, 0, 144),
                        "$mode page $i: header band should be drawn in the top margin",
                    )
                    assertTrue(
                        img.hasInkInRows(xMid, img.height - 144, img.height),
                        "$mode page $i: footer band should be drawn in the bottom margin",
                    )
                }
            }
        }
    }
}

/** True if any pixel in column [xCol], rows [yStart, yEnd), is not whitish. */
private fun BufferedImage.hasInkInRows(xCol: Int, yStart: Int, yEnd: Int): Boolean {
    for (y in yStart until yEnd) if (!isWhitishAt(xCol, y)) return true
    return false
}
