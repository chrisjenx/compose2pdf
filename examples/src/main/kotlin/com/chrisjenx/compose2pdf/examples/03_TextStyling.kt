package com.chrisjenx.compose2pdf.examples

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Divider
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chrisjenx.compose2pdf.PdfPageConfig
import com.chrisjenx.compose2pdf.renderToPdf

// --- snippet start ---
fun textStyling() = listOf(
    ExampleOutput(
        name = "03-text-styling",
        sourceFile = "03_TextStyling.kt",
        pdfBytes = renderToPdf(config = PdfPageConfig.A4WithMargins) {
            Column(Modifier.fillMaxSize()) {
                // Font weights
                SectionHeader("Font Weights")
                Text("Light text", fontWeight = FontWeight.Light)
                Text("Normal text", fontWeight = FontWeight.Normal)
                Text("Medium text", fontWeight = FontWeight.Medium)
                Text("Bold text", fontWeight = FontWeight.Bold)

                SectionDivider()

                // Font sizes
                SectionHeader("Font Sizes")
                Text("10sp — Fine print", fontSize = 10.sp)
                Text("14sp — Body text", fontSize = 14.sp)
                Text("20sp — Subheading", fontSize = 20.sp)
                Text("28sp — Heading", fontSize = 28.sp)

                SectionDivider()

                // Styles and colors
                SectionHeader("Styles & Colors")
                Text("Italic text", fontStyle = FontStyle.Italic)
                Text("Bold + Italic", fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)
                Text("Primary blue", color = Color(0xFF1976D2))
                Text("Success green", color = Color(0xFF388E3C))
                Text("Error red", color = Color(0xFFD32F2F))

                SectionDivider()

                // Decorations
                SectionHeader("Decorations")
                Text("Underlined text", textDecoration = TextDecoration.Underline)
                Text("Strikethrough text", textDecoration = TextDecoration.LineThrough)
                Text(
                    "Both underline and strikethrough",
                    textDecoration = TextDecoration.Underline + TextDecoration.LineThrough,
                )

                SectionDivider()

                // Alignment
                SectionHeader("Alignment")
                Text("Left aligned (default)", Modifier.fillMaxWidth())
                Text("Center aligned", Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                Text("Right aligned", Modifier.fillMaxWidth(), textAlign = TextAlign.End)

                SectionDivider()

                // Overflow
                SectionHeader("Overflow")
                Text(
                    "This is a very long line of text that should be truncated with an ellipsis when it exceeds the available width of the page content area.",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "This paragraph wraps to two lines maximum. The quick brown fox jumps over the lazy dog. " +
                        "Pack my box with five dozen liquor jugs. How vexingly quick daft zebras jump.",
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
    )
)
// --- snippet end ---

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
        color = Color(0xFF424242),
    )
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun SectionDivider() {
    Spacer(Modifier.height(12.dp))
    Divider(color = Color(0xFFE0E0E0))
    Spacer(Modifier.height(12.dp))
}
