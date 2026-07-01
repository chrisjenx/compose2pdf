package com.chrisjenx.compat

import androidx.compose.material.Text
import com.chrisjenx.compose2pdf.renderToPdf

/**
 * Renders a trivial PDF using the PUBLISHED compose2pdf jar against whatever Compose
 * runtime this build resolved. Exits non-zero (via check) on any failure, which fails
 * the Gradle `run` task and the CI job.
 */
fun main() {
    val pdf = renderToPdf {
        Text("compose2pdf compatibility smoke test")
    }
    check(pdf.size > 100) { "PDF suspiciously small: ${pdf.size} bytes" }
    val header = pdf.copyOfRange(0, 5).toString(Charsets.US_ASCII)
    check(header == "%PDF-") { "Not a PDF - header was '$header'" }
    println("compat-consumer OK: rendered ${pdf.size}-byte PDF")
}
