package com.chrisjenx.compose2pdf.examples

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Divider
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chrisjenx.compose2pdf.InterFontFamily
import com.chrisjenx.compose2pdf.PdfPageConfig
import com.chrisjenx.compose2pdf.renderToPdf

// --- snippet start ---
// Any font Compose lays text out with is captured and embedded in the PDF
// automatically — the font does NOT need to be installed on the machine.
// Declare each weight/style you use from a static .ttf/.otf file.
val MontserratFamily = FontFamily(
    Font(resource = "fonts/Montserrat-Regular.ttf", weight = FontWeight.Normal),
    Font(resource = "fonts/Montserrat-Bold.ttf", weight = FontWeight.Bold),
)

fun customFonts() = listOf(
    ExampleOutput(
    name = "14-custom-fonts",
    sourceFile = "14_CustomFonts.kt",
    // Pass a family as the document default…
    pdfBytes = renderToPdf(
        config = PdfPageConfig.A4WithMargins,
        defaultFontFamily = MontserratFamily,
    ) {
        Column(Modifier.fillMaxSize()) {
            SectionHeader("Document default: Montserrat via Font(resource)")
            Text("Every weight declared in the FontFamily embeds from its own file.")
            Text("Bold weight comes from Montserrat-Bold.ttf.", fontWeight = FontWeight.Bold)
            Text("Large heading, still Montserrat", fontSize = 28.sp)

            SectionDivider()

            // …or set fontFamily per Text (theme typography works the same way)
            SectionHeader("Per-text families")
            Text("Bundled InterFontFamily — zero setup, works everywhere", fontFamily = InterFontFamily)
            Text(
                "Inter bold italic from the bundled static files",
                fontFamily = InterFontFamily,
                fontWeight = FontWeight.Bold,
                fontStyle = FontStyle.Italic,
            )

            SectionDivider()

            SectionHeader("Generic families (resolved by the platform, then embedded)")
            Text("FontFamily.SansSerif — the platform's sans", fontFamily = FontFamily.SansSerif)
            Text("FontFamily.Serif — the platform's serif", fontFamily = FontFamily.Serif)
            Text("FontFamily.Monospace — the platform's mono", fontFamily = FontFamily.Monospace)
            Text(
                "Generic families depend on the host's installed fonts — bundle a " +
                    "Font(resource) family instead when output must be identical everywhere.",
                fontSize = 10.sp,
                color = Color(0xFF757575),
            )

            SectionDivider()

            SectionHeader("Spacing-sensitive sample")
            Text("Late Fee - 2.5% (\$34.69) + \$5.00 fixed — glyph positions and widths come from the same font, so spacing is exact.")
            }
        },
    ),
)
// --- snippet end ---

@Composable
private fun SectionHeader(title: String) {
    Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF37474F))
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun SectionDivider() {
    Spacer(Modifier.height(14.dp))
    Divider(color = Color(0xFFE0E0E0), thickness = 1.dp)
    Spacer(Modifier.height(14.dp))
}
