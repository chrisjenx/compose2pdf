package com.chrisjenx.compose2pdf.examples

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chrisjenx.compose2pdf.PdfMargins
import com.chrisjenx.compose2pdf.PdfPageConfig
import com.chrisjenx.compose2pdf.renderToPdf

// --- snippet start ---
fun pageConfiguration(): List<ExampleOutput> {
    val source = "02_PageConfiguration.kt"
    val a4 = PdfPageConfig.A4
    val letter = PdfPageConfig.LetterWithMargins
    val a3Landscape = PdfPageConfig.A3.landscape()
    val custom = PdfPageConfig(
        width = 360.dp,
        height = 504.dp,
        margins = PdfMargins(top = 36.dp, bottom = 48.dp, left = 24.dp, right = 24.dp),
    )
    return listOf(
        ExampleOutput("02-a4-default", source, renderToPdf(config = a4) {
            PageDemo("A4 Default", "595 × 842 dp — no margins", a4)
        }),
        ExampleOutput("02-letter-with-margins", source, renderToPdf(config = letter) {
            PageDemo("Letter + Normal Margins", "612 × 792 dp — 72dp margins", letter)
        }),
        ExampleOutput("02-a3-landscape", source, renderToPdf(config = a3Landscape) {
            PageDemo("A3 Landscape", "1191 × 842 dp — landscape()", a3Landscape)
        }),
        ExampleOutput("02-custom-page", source, renderToPdf(config = custom) {
            PageDemo("Custom 5×7\"", "360 × 504 dp — asymmetric margins (36/48/24/24)", custom)
        }),
    )
}

@Composable
private fun PageDemo(title: String, subtitle: String, config: PdfPageConfig) {
    Box(
        Modifier
            .fillMaxSize()
            .border(2.dp, Color(0xFF2196F3))
            .padding(16.dp)
    ) {
        Column {
            Text(title, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(subtitle, fontSize = 12.sp, color = Color.Gray)
            Spacer(Modifier.height(16.dp))
            Text("Content area: ${config.contentWidth} × ${config.contentHeight}", fontSize = 14.sp)
            Text("Margins: T=${config.margins.top} B=${config.margins.bottom} L=${config.margins.left} R=${config.margins.right}", fontSize = 12.sp, color = Color.DarkGray)
        }
    }
}
// --- snippet end ---
