package com.chrisjenx.compose2pdf.examples

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Divider
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chrisjenx.compose2pdf.PaginatedColumn
import com.chrisjenx.compose2pdf.PdfPageConfig
import com.chrisjenx.compose2pdf.renderToPdf

// --- snippet start ---
fun paginatedColumnExample() = listOf(
    ExampleOutput(
        name = "12-paginated-column",
        sourceFile = "12_PaginatedColumn.kt",
        pdfBytes = renderToPdf(
            config = PdfPageConfig.A4WithMargins,
        ) {
            // When content is wrapped in a provider or Column, auto-pagination
            // sees only one child. Use PaginatedColumn inside the wrapper to
            // restore per-child page breaking.
            MyStyleProvider {
                PaginatedColumn {
                    InvoiceHeader()

                    Spacer(Modifier.height(16.dp))

                    // Each row is a keep-together unit
                    for (i in 1..20) {
                        InvoiceRow(i, "Professional services — line item $i", i * 150.0)
                    }

                    Spacer(Modifier.height(16.dp))

                    InvoiceSummary(total = (1..20).sumOf { it * 150 }.toDouble())
                }
            }
        },
    )
)
// --- snippet end ---

// Simulates a user-defined theme/style provider that wraps content
private val LocalInvoiceStyle = compositionLocalOf { Color(0xFF1565C0) }

@Composable
private fun MyStyleProvider(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalInvoiceStyle provides Color(0xFF1565C0)) {
        Column(Modifier.fillMaxWidth()) {
            content()
        }
    }
}

@Composable
private fun InvoiceHeader() {
    Column(
        Modifier
            .fillMaxWidth()
            .background(LocalInvoiceStyle.current)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text("INVOICE #2025-042", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text("PaginatedColumn Example", color = Color(0xBBFFFFFF), fontSize = 12.sp)
    }
}

@Composable
private fun InvoiceRow(index: Int, description: String, amount: Double) {
    val bg = if (index % 2 == 0) Color.Transparent else Color(0xFFFAFAFA)
    Row(
        Modifier
            .fillMaxWidth()
            .background(bg)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(description, Modifier.weight(1f), fontSize = 11.sp)
        Text("$%,.2f".format(amount), fontSize = 11.sp)
    }
}

@Composable
private fun InvoiceSummary(total: Double) {
    Divider()
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text("Total", fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Text("$%,.2f".format(total), fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}
