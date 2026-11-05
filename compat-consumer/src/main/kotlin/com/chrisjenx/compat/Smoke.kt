package com.chrisjenx.compat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.chrisjenx.compose2pdf.RenderMode
import com.chrisjenx.compose2pdf.renderToPdf
import org.apache.pdfbox.Loader

/**
 * Renders small PDFs using the PUBLISHED compose2pdf jar against whatever Compose runtime this
 * build resolved. Exits non-zero (via check) on failure, failing the CI job.
 *
 * Beyond the vector scene driver, it exercises the other version-sensitive seams a single binary
 * must span: raster mode, manual pages, and system-font embedding — the latter only succeeds if the
 * reflective Skia FontStyle access works (FontStyle became an inline value class in skiko 0.156 /
 * CMP 1.13, changing every JVM signature that touches it). A broken seam degrades silently to a
 * non-embedded standard-14 font, so that case asserts embedding rather than just "no exception".
 */
fun main() {
    val pdf = renderToPdf {
        Column {
            Text("compose2pdf compatibility smoke test")
            Box(Modifier.size(48.dp).background(Color.Red))
        }
    }
    checkPdf("vector", pdf)

    checkPdf("raster", renderToPdf(mode = RenderMode.RASTER) { Text("raster smoke") })
    checkPdf("manual pages", renderToPdf(pages = 2) { i -> Text("page $i") })

    // Generic system families (no bundled Inter) resolve through Skia's FontCollection/FontMgr.
    val systemPdf = renderToPdf(defaultFontFamily = null) {
        // Regular weight only: a BOLD instance of a variable-only system font (e.g. macOS SF) is a
        // known non-embeddable case independent of the CMP version, so it would make this flaky.
        Text("system sans-serif", style = TextStyle(fontFamily = FontFamily.SansSerif))
    }
    checkPdf("system fonts", systemPdf)
    val notEmbedded = Loader.loadPDF(systemPdf).use { doc ->
        doc.pages.flatMap { page ->
            page.resources.fontNames.map { page.resources.getFont(it) }.filterNot { it.isEmbedded }.map { it.name }
        }
    }
    check(notEmbedded.isEmpty()) { "system-font text fell back to non-embedded fonts $notEmbedded (Skia FontStyle seam broken?)" }

    println("compat-consumer OK: vector, raster, manual pages and embedded system fonts")
}

private fun checkPdf(label: String, pdf: ByteArray) {
    check(pdf.size > 100) { "$label: PDF suspiciously small: ${pdf.size} bytes" }
    val header = pdf.copyOfRange(0, 5).toString(Charsets.US_ASCII)
    check(header == "%PDF-") { "$label: not a PDF - header was '$header'" }
}
