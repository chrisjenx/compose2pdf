package com.chrisjenx.compose2pdf.examples

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chrisjenx.compose2pdf.PdfPageConfig
import com.chrisjenx.compose2pdf.renderToPdf

fun headerFooter() = listOf(
    ExampleOutput(
        name = "13-header-footer",
        sourceFile = "13_HeaderFooter.kt",
        pdfBytes = run {
            // --- snippet start ---
            renderToPdf(
                config = PdfPageConfig.A4WithMargins,
                // Stamped at the top of every page; height is measured once and reserved uniformly
                header = {
                    Row(
                        Modifier.fillMaxWidth().background(Color(0xFF1565C0)).padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Acme Corp — Q2 Report", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                },
                // Receives PdfPageInfo for page numbering
                footer = { info ->
                    Row(
                        Modifier.fillMaxWidth().padding(6.dp),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Text("Page ${info.pageNumber} of ${info.pageCount}", fontSize = 9.sp, color = Color.Gray)
                    }
                },
            ) {
                for (i in 1..60) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
                        Text("Line item $i", Modifier.weight(2f), fontSize = 11.sp)
                        Text("$${i * 42}.00", Modifier.weight(1f), fontSize = 11.sp)
                    }
                }
            }
            // --- snippet end ---
        }
    )
)
