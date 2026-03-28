package com.chrisjenx.compose2pdf.test

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Divider
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chrisjenx.compose2pdf.PdfPageConfig
import com.chrisjenx.compose2pdf.fixtures.*
import org.jetbrains.skia.Color as SkColor
import org.jetbrains.skia.Paint as SkPaint
import org.jetbrains.skia.Rect as SkRect
import org.jetbrains.skia.Surface as SkSurface

data class Fixture(
    val name: String,
    val category: String = "basic",
    val description: String = "",
    val vectorThreshold: Double = 0.15,
    val config: PdfPageConfig = PdfPageConfig.A4,
    val content: @Composable () -> Unit,
)

val fidelityFixtures: List<Fixture> = sharedFixtures.map { sf ->
    Fixture(sf.name, sf.category, sf.description, sf.vectorThreshold, sf.config, sf.content)
} + listOf(
    // Skia-only fixtures (not in shared module)
    Fixture("with-image", "basic", "Embedded bitmap image with text") { WithImageFixture() },
    Fixture("mixed-content", "composite", "Dashboard card with image, text, shapes, and backgrounds", 0.20) { MixedContentFixture() },
    Fixture("transparent-image", "basic", "Overlapping semi-transparent circles on white + colored backgrounds") { TransparentImageFixture() },
    Fixture("gradient-image", "basic", "Horizontal gradient via thin vertical strips") { GradientImageFixture() },
    Fixture("multiple-images", "basic", "6 images in a 2x3 grid") { MultipleImagesFixture() },
    Fixture("scaled-image", "basic", "32x32 checkerboard at 32dp, 64dp, 128dp sizes") { ScaledImageFixture() },
)

// -- Skia-only fixtures (require org.jetbrains.skia for bitmap creation) --

@Composable
private fun WithImageFixture() {
    val imageBitmap = remember {
        val w = 120
        val h = 80
        val surface = SkSurface.makeRasterN32Premul(w, h)
        val canvas = surface.canvas
        val halfW = w / 2f
        val halfH = h / 2f
        fun fill(x: Float, y: Float, fw: Float, fh: Float, r: Int, g: Int, b: Int) {
            canvas.drawRect(
                SkRect.makeXYWH(x, y, fw, fh),
                SkPaint().apply { color = SkColor.makeARGB(255, r, g, b) },
            )
        }
        fill(0f, 0f, halfW, halfH, 220, 50, 50)
        fill(halfW, 0f, halfW, halfH, 50, 100, 220)
        fill(0f, halfH, halfW, halfH, 50, 180, 80)
        fill(halfW, halfH, halfW, halfH, 240, 200, 50)
        surface.makeImageSnapshot().toComposeImageBitmap()
    }
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Text("Image Rendering Test", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Image(
            bitmap = imageBitmap,
            contentDescription = "Test pattern",
            modifier = Modifier.size(120.dp, 80.dp),
        )
        Spacer(Modifier.height(8.dp))
        Text("Programmatic bitmap with four colored quadrants.")
    }
}

@Composable
private fun MixedContentFixture() {
    val imageBitmap = remember {
        val surface = SkSurface.makeRasterN32Premul(60, 60)
        val canvas = surface.canvas
        val p = SkPaint()
        p.color = SkColor.makeARGB(255, 70, 130, 180)
        canvas.drawRect(SkRect.makeXYWH(0f, 0f, 60f, 60f), p)
        p.color = SkColor.makeARGB(255, 255, 255, 255)
        canvas.drawCircle(30f, 30f, 20f, p)
        surface.makeImageSnapshot().toComposeImageBitmap()
    }

    Column(Modifier.fillMaxSize().padding(24.dp)) {
        // Card header
        Box(
            Modifier.fillMaxWidth()
                .background(Color(0xFF2196F3), RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                .padding(16.dp),
        ) {
            Text("Dashboard Card", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
        // Card body
        Column(
            Modifier.fillMaxWidth()
                .border(1.dp, Color(0xFFE0E0E0), RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp))
                .padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    bitmap = imageBitmap,
                    contentDescription = "Avatar",
                    modifier = Modifier.size(48.dp).clip(CircleShape),
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("John Doe", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("Software Engineer", fontSize = 12.sp, color = Color.Gray)
                }
            }
            Spacer(Modifier.height(12.dp))
            Divider()
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("42", fontWeight = FontWeight.Bold, fontSize = 24.sp, color = Color(0xFF4CAF50))
                    Text("Tasks", fontSize = 11.sp, color = Color.Gray)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("8", fontWeight = FontWeight.Bold, fontSize = 24.sp, color = Color(0xFFFF9800))
                    Text("Pending", fontSize = 11.sp, color = Color.Gray)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("97%", fontWeight = FontWeight.Bold, fontSize = 24.sp, color = Color(0xFF2196F3))
                    Text("Score", fontSize = 11.sp, color = Color.Gray)
                }
            }
            Spacer(Modifier.height(12.dp))
            // Progress bar
            Box(
                Modifier.fillMaxWidth().height(8.dp)
                    .background(Color(0xFFE0E0E0), RoundedCornerShape(4.dp)),
            ) {
                Box(
                    Modifier.fillMaxWidth(0.75f).height(8.dp)
                        .background(Color(0xFF4CAF50), RoundedCornerShape(4.dp)),
                )
            }
        }
    }
}

@Composable
private fun TransparentImageFixture() {
    val imageBitmap = remember {
        val w = 160
        val h = 120
        val surface = SkSurface.makeRasterN32Premul(w, h)
        val canvas = surface.canvas
        // White background
        canvas.drawRect(
            SkRect.makeXYWH(0f, 0f, w.toFloat(), h.toFloat()),
            SkPaint().apply { color = SkColor.makeARGB(255, 255, 255, 255) },
        )
        // Semi-transparent overlapping circles
        canvas.drawCircle(50f, 50f, 40f, SkPaint().apply { color = SkColor.makeARGB(128, 255, 0, 0) })
        canvas.drawCircle(90f, 50f, 40f, SkPaint().apply { color = SkColor.makeARGB(128, 0, 0, 255) })
        canvas.drawCircle(70f, 80f, 40f, SkPaint().apply { color = SkColor.makeARGB(128, 0, 180, 0) })
        surface.makeImageSnapshot().toComposeImageBitmap()
    }
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Text("Transparent Image Test", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        // On white background
        Image(bitmap = imageBitmap, contentDescription = "Transparent on white", modifier = Modifier.size(160.dp, 120.dp))
        Spacer(Modifier.height(12.dp))
        // On colored background
        Box(Modifier.background(Color(0xFFFFE0B2)).padding(8.dp)) {
            Image(bitmap = imageBitmap, contentDescription = "Transparent on orange", modifier = Modifier.size(160.dp, 120.dp))
        }
    }
}

@Composable
private fun GradientImageFixture() {
    val imageBitmap = remember {
        val w = 256
        val h = 60
        val surface = SkSurface.makeRasterN32Premul(w, h)
        val canvas = surface.canvas
        // Draw thin vertical strips to simulate a gradient
        for (x in 0 until w) {
            val ratio = x.toFloat() / (w - 1)
            val r = (255 * (1f - ratio)).toInt()
            val g = (255 * ratio).toInt()
            val b = 128
            canvas.drawRect(
                SkRect.makeXYWH(x.toFloat(), 0f, 1f, h.toFloat()),
                SkPaint().apply { color = SkColor.makeARGB(255, r, g, b) },
            )
        }
        surface.makeImageSnapshot().toComposeImageBitmap()
    }
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Text("Gradient Image Test", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Image(bitmap = imageBitmap, contentDescription = "Gradient", modifier = Modifier.fillMaxWidth().height(60.dp))
        Spacer(Modifier.height(8.dp))
        Text("Red to Green horizontal gradient with constant blue channel", fontSize = 12.sp, color = Color.Gray)
    }
}

@Composable
private fun MultipleImagesFixture() {
    val images = remember {
        val colors = listOf(
            Triple(220, 50, 50), Triple(50, 100, 220), Triple(50, 180, 80),
            Triple(240, 200, 50), Triple(180, 50, 220), Triple(50, 200, 200),
        )
        colors.map { (r, g, b) ->
            val surface = SkSurface.makeRasterN32Premul(60, 60)
            val canvas = surface.canvas
            canvas.drawRect(
                SkRect.makeXYWH(0f, 0f, 60f, 60f),
                SkPaint().apply { color = SkColor.makeARGB(255, r, g, b) },
            )
            // Draw a white diagonal line
            canvas.drawLine(0f, 0f, 60f, 60f, SkPaint().apply {
                color = SkColor.makeARGB(200, 255, 255, 255)
                strokeWidth = 3f
            })
            surface.makeImageSnapshot().toComposeImageBitmap()
        }
    }
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Text("Multiple Images (2x3 Grid)", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        for (row in 0 until 2) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (col in 0 until 3) {
                    Image(
                        bitmap = images[row * 3 + col],
                        contentDescription = "Image ${row * 3 + col}",
                        modifier = Modifier.size(80.dp),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ScaledImageFixture() {
    val checkerboard = remember {
        val size = 32
        val surface = SkSurface.makeRasterN32Premul(size, size)
        val canvas = surface.canvas
        val cellSize = 4f
        for (row in 0 until (size / cellSize.toInt())) {
            for (col in 0 until (size / cellSize.toInt())) {
                val isWhite = (row + col) % 2 == 0
                canvas.drawRect(
                    SkRect.makeXYWH(col * cellSize, row * cellSize, cellSize, cellSize),
                    SkPaint().apply {
                        color = if (isWhite) SkColor.makeARGB(255, 255, 255, 255)
                        else SkColor.makeARGB(255, 0, 0, 0)
                    },
                )
            }
        }
        surface.makeImageSnapshot().toComposeImageBitmap()
    }
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Text("Scaled Image Test", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Image(bitmap = checkerboard, contentDescription = "32dp", modifier = Modifier.size(32.dp))
                Text("32dp", fontSize = 10.sp)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Image(bitmap = checkerboard, contentDescription = "64dp", modifier = Modifier.size(64.dp))
                Text("64dp", fontSize = 10.sp)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Image(bitmap = checkerboard, contentDescription = "128dp", modifier = Modifier.size(128.dp))
                Text("128dp", fontSize = 10.sp)
            }
        }
    }
}
