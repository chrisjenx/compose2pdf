package com.chrisjenx.compose2pdf.examples

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Divider
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chrisjenx.compose2pdf.PdfPageConfig
import com.chrisjenx.compose2pdf.RenderMode
import com.chrisjenx.compose2pdf.renderToPdf

// --- snippet start ---
fun vectorVsRaster(): List<ExampleOutput> {
    val source = "09_VectorVsRaster.kt"

    // Vector mode: selectable text, smaller file size, infinite scaling
    val vectorPdf = renderToPdf(
        config = PdfPageConfig.A4WithMargins,
        mode = RenderMode.VECTOR,
    ) { ComparisonContent("VECTOR") }

    // Raster mode: pixel-perfect, no SVG conversion, larger file size
    val rasterPdf = renderToPdf(
        config = PdfPageConfig.A4WithMargins,
        mode = RenderMode.RASTER,
        density = Density(3f), // higher density for better raster quality
    ) { ComparisonContent("RASTER") }

    return listOf(
        ExampleOutput("09-vector-mode", source, vectorPdf),
        ExampleOutput("09-raster-mode", source, rasterPdf),
    )
}
// --- snippet end ---

@Composable
private fun ComparisonContent(modeName: String) {
    Column(Modifier.fillMaxSize()) {
        Text(
            "Render Mode: $modeName",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = if (modeName == "VECTOR") Color(0xFF1565C0) else Color(0xFFD32F2F),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            when (modeName) {
                "VECTOR" -> "Text is selectable in PDF viewer. Scales to any zoom level. Smaller file size."
                else -> "Pixel-perfect rendering. Text is not selectable. Larger file size."
            },
            fontSize = 12.sp,
            color = Color.Gray,
        )

        Spacer(Modifier.height(24.dp))
        Divider()
        Spacer(Modifier.height(24.dp))

        // Text at various sizes to show rendering differences
        Text("Text Rendering", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("Large heading — 20sp", fontSize = 20.sp)
        Text("Body text — 14sp. Try selecting this text in your PDF viewer.", fontSize = 14.sp)
        Text("Small print — 9sp. Fine details test.", fontSize = 9.sp)
        Text("Bold + Italic test", fontWeight = FontWeight.Bold, fontSize = 14.sp)

        Spacer(Modifier.height(24.dp))
        Divider()
        Spacer(Modifier.height(24.dp))

        // Fine lines to show scaling differences
        Text("Fine Lines", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Canvas(Modifier.fillMaxWidth().height(80.dp)) {
            val widths = listOf(0.5f, 1f, 2f, 4f)
            for ((i, w) in widths.withIndex()) {
                val y = 10f + i * 20f
                drawLine(
                    Color.Black,
                    Offset(0f, y),
                    Offset(size.width * 0.7f, y),
                    strokeWidth = w,
                )
            }
        }

        Spacer(Modifier.height(24.dp))
        Divider()
        Spacer(Modifier.height(24.dp))

        // Shapes
        Text("Shapes", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Canvas(Modifier.fillMaxWidth().height(100.dp)) {
            drawCircle(Color(0xFF1976D2), radius = 40f, center = Offset(50f, 50f))
            drawCircle(
                Color(0xFF388E3C),
                radius = 40f,
                center = Offset(150f, 50f),
                style = Stroke(2f),
            )
            drawRect(Color(0xFFF57C00), topLeft = Offset(210f, 10f), size = androidx.compose.ui.geometry.Size(80f, 80f))
        }
    }
}
