package com.chrisjenx.compose2pdf.examples

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.material.Divider
import androidx.compose.material.Text
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chrisjenx.compose2pdf.PdfPageConfig
import com.chrisjenx.compose2pdf.renderToPdf
import org.jetbrains.skia.Color as SkColor
import org.jetbrains.skia.Paint as SkPaint
import org.jetbrains.skia.Surface as SkSurface

// --- snippet start ---
fun imagesInPdf() = listOf(
    ExampleOutput(
        name = "06-images",
        sourceFile = "06_ImagesInPdf.kt",
        pdfBytes = renderToPdf(config = PdfPageConfig.A4WithMargins) {
            // Create a sample image programmatically (in real usage, load from file/resource)
            val bitmap = remember {
                val w = 128
                val h = 128
                val surface = SkSurface.makeRasterN32Premul(w, h)
                val canvas = surface.canvas
                // Blue gradient background
                canvas.drawRect(org.jetbrains.skia.Rect.makeWH(w.toFloat(), h.toFloat()),
                    SkPaint().apply { color = SkColor.makeRGB(25, 118, 210) })
                // Yellow circle
                canvas.drawCircle(64f, 64f, 40f,
                    SkPaint().apply { color = SkColor.makeRGB(255, 193, 7) })
                // White inner circle
                canvas.drawCircle(64f, 64f, 20f,
                    SkPaint().apply { color = SkColor.makeRGB(255, 255, 255) })
                surface.makeImageSnapshot().toComposeImageBitmap()
            }

            Column(Modifier.fillMaxSize()) {
                Text("Images in PDF", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(16.dp))

                // Natural size
                Text("Natural size (128 × 128 dp)", fontSize = 12.sp, color = Color.Gray)
                Spacer(Modifier.height(4.dp))
                Image(BitmapPainter(bitmap), "Sample image")

                Spacer(Modifier.height(24.dp))
                Divider()
                Spacer(Modifier.height(24.dp))

                // Scaled sizes
                Text("Scaled sizes", fontSize = 12.sp, color = Color.Gray)
                Spacer(Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Image(BitmapPainter(bitmap), "48dp", Modifier.size(48.dp))
                        Text("48dp", fontSize = 10.sp)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Image(BitmapPainter(bitmap), "80dp", Modifier.size(80.dp))
                        Text("80dp", fontSize = 10.sp)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Image(BitmapPainter(bitmap), "128dp", Modifier.size(128.dp))
                        Text("128dp", fontSize = 10.sp)
                    }
                }

                Spacer(Modifier.height(24.dp))
                Divider()
                Spacer(Modifier.height(24.dp))

                // Clipped to circle (avatar pattern)
                Text("Circle-clipped (avatar pattern)", fontSize = 12.sp, color = Color.Gray)
                Spacer(Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Image(
                        BitmapPainter(bitmap),
                        "Avatar",
                        Modifier.size(64.dp).clip(CircleShape),
                    )
                    Column {
                        Text("Jane Smith", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text("Product Designer", fontSize = 12.sp, color = Color.Gray)
                    }
                }

                Spacer(Modifier.height(24.dp))

                // Note about real-world usage
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFE3F2FD))
                        .padding(12.dp)
                ) {
                    Text(
                        "In real usage, load images via standard Compose APIs: " +
                            "painterResource(\"image.png\") for classpath resources, " +
                            "or create ImageBitmap from any BufferedImage/InputStream.",
                        fontSize = 11.sp,
                        color = Color(0xFF0D47A1),
                    )
                }
            }
        },
    )
)
// --- snippet end ---
