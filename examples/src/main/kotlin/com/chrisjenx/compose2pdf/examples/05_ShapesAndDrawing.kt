package com.chrisjenx.compose2pdf.examples

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Divider
import androidx.compose.material.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chrisjenx.compose2pdf.PdfPageConfig
import com.chrisjenx.compose2pdf.PdfRoundedCornerShape
import com.chrisjenx.compose2pdf.renderToPdf

// --- snippet start ---
fun shapesAndDrawing() = listOf(
    ExampleOutput(
        name = "05-shapes-and-drawing",
        sourceFile = "05_ShapesAndDrawing.kt",
        pdfBytes = renderToPdf(config = PdfPageConfig.A4WithMargins) {
            Column(Modifier.fillMaxSize()) {
                Text("Shapes via Modifiers", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // Rounded rectangle
                    Box(
                        Modifier
                            .size(80.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF1976D2)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("12dp", color = Color.White, fontSize = 11.sp)
                    }
                    // Circle
                    Box(
                        Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF388E3C)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("Circle", color = Color.White, fontSize = 11.sp)
                    }
                    // Non-uniform corners — use PdfRoundedCornerShape for correct PDF output
                    Box(
                        Modifier
                            .size(80.dp)
                            .clip(PdfRoundedCornerShape(topStart = 24.dp, bottomEnd = 24.dp))
                            .background(Color(0xFFF57C00)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("Asym", color = Color.White, fontSize = 11.sp)
                    }
                    // Border only
                    Box(
                        Modifier
                            .size(80.dp)
                            .border(2.dp, Color(0xFFD32F2F), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("Border", fontSize = 11.sp)
                    }
                }

                Spacer(Modifier.height(8.dp))
                // Tip callout
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFFFF3E0))
                        .padding(12.dp)
                ) {
                    Text(
                        "Tip: Use PdfRoundedCornerShape() for non-uniform corner radii. " +
                            "Standard RoundedCornerShape works for uniform corners.",
                        fontSize = 11.sp,
                        color = Color(0xFFE65100),
                    )
                }

                Spacer(Modifier.height(24.dp))
                Divider()
                Spacer(Modifier.height(24.dp))

                Text("Canvas Drawing", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))

                Canvas(Modifier.fillMaxWidth().height(200.dp)) {
                    // Filled circle
                    drawCircle(Color(0xFF1976D2), radius = 40f, center = Offset(60f, 80f))

                    // Stroked circle
                    drawCircle(
                        Color(0xFF388E3C),
                        radius = 40f,
                        center = Offset(160f, 80f),
                        style = Stroke(width = 3f),
                    )

                    // Filled rectangle
                    drawRect(Color(0xFFF57C00), topLeft = Offset(220f, 40f), size = Size(80f, 80f))

                    // Lines
                    drawLine(Color.Black, Offset(330f, 40f), Offset(420f, 120f), strokeWidth = 2f)
                    drawLine(Color.Gray, Offset(330f, 120f), Offset(420f, 40f), strokeWidth = 2f)

                    // Arc
                    drawArc(
                        Color(0xFFD32F2F),
                        startAngle = 0f,
                        sweepAngle = 270f,
                        useCenter = true,
                        topLeft = Offset(60f, 140f),
                        size = Size(80f, 80f),
                    )

                    // Path: triangle
                    val triangle = Path().apply {
                        moveTo(240f, 150f)
                        lineTo(200f, 230f)
                        lineTo(280f, 230f)
                        close()
                    }
                    drawPath(triangle, Color(0xFF7B1FA2))
                }
            }
        },
    )
)
// --- snippet end ---
