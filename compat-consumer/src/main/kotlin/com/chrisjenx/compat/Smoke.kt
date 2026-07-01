package com.chrisjenx.compat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.chrisjenx.compose2pdf.renderToPdf

/**
 * Renders a small PDF (text + a filled shape) using the PUBLISHED compose2pdf jar against whatever
 * Compose runtime this build resolved. Exits non-zero (via check) on failure, failing the CI job.
 */
fun main() {
    val pdf = renderToPdf {
        Column {
            Text("compose2pdf compatibility smoke test")
            Box(Modifier.size(48.dp).background(Color.Red))
        }
    }
    check(pdf.size > 100) { "PDF suspiciously small: ${pdf.size} bytes" }
    val header = pdf.copyOfRange(0, 5).toString(Charsets.US_ASCII)
    check(header == "%PDF-") { "Not a PDF - header was '$header'" }
    println("compat-consumer OK: rendered ${pdf.size}-byte PDF")
}
