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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chrisjenx.compose2pdf.PdfPageConfig
import com.chrisjenx.compose2pdf.renderToPdf

// --- snippet start ---
fun autoPagination() = listOf(
    ExampleOutput(
        name = "11-auto-pagination",
        sourceFile = "11_AutoPagination.kt",
        pdfBytes = renderToPdf(
            config = PdfPageConfig.A4WithMargins,
        ) {
            // Direct children are "keep-together" units.
            // If a child would straddle a page boundary, it's pushed to the next page.

            ReportTitle()

            Spacer(Modifier.height(16.dp))

            // This table is long enough to push subsequent content to page 2
            LargeDataTable()

            Spacer(Modifier.height(24.dp))

            // This section flows automatically to the next page
            NotesSection()
        },
    )
)
// --- snippet end ---

@Composable
private fun ReportTitle() {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Color(0xFF1565C0))
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text("Sales Report — Q1 2025", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text("Auto-paginated document", color = Color(0xBBFFFFFF), fontSize = 12.sp)
    }
}

@Composable
private fun LargeDataTable() {
    Column {
        Text("Monthly Sales Data", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))

        // Header row
        Row(
            Modifier
                .fillMaxWidth()
                .background(Color(0xFFE3F2FD))
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text("Month", Modifier.weight(2f), fontWeight = FontWeight.Bold, fontSize = 11.sp)
            Text("Revenue", Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 11.sp, textAlign = TextAlign.End)
            Text("Units", Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 11.sp, textAlign = TextAlign.End)
            Text("Growth", Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 11.sp, textAlign = TextAlign.End)
        }

        val data = listOf(
            listOf("January", "$41,200", "824", "+5.2%"),
            listOf("February", "$38,900", "778", "-5.6%"),
            listOf("March", "$45,600", "912", "+17.2%"),
            listOf("April", "$43,100", "862", "-5.5%"),
            listOf("May", "$47,800", "956", "+10.9%"),
            listOf("June", "$51,200", "1,024", "+7.1%"),
            listOf("July", "$49,500", "990", "-3.3%"),
            listOf("August", "$52,800", "1,056", "+6.7%"),
            listOf("September", "$55,100", "1,102", "+4.4%"),
            listOf("October", "$53,400", "1,068", "-3.1%"),
            listOf("November", "$58,200", "1,164", "+9.0%"),
            listOf("December", "$62,500", "1,250", "+7.4%"),
        )

        for ((index, row) in data.withIndex()) {
            val bg = if (index % 2 == 0) Color.Transparent else Color(0xFFFAFAFA)
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(bg)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(row[0], Modifier.weight(2f), fontSize = 11.sp)
                Text(row[1], Modifier.weight(1f), fontSize = 11.sp, textAlign = TextAlign.End)
                Text(row[2], Modifier.weight(1f), fontSize = 11.sp, textAlign = TextAlign.End)
                Text(row[3], Modifier.weight(1f), fontSize = 11.sp, textAlign = TextAlign.End)
            }
        }

        Spacer(Modifier.height(8.dp))
        Divider()
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Total", fontWeight = FontWeight.Bold, fontSize = 11.sp)
            Text("$599,300", fontWeight = FontWeight.Bold, fontSize = 11.sp)
        }
    }
}

@Composable
private fun NotesSection() {
    Column {
        Text("Notes & Analysis", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        for (note in listOf(
            "Strong year-over-year growth with consistent upward trend through H2.",
            "December performance exceeded targets by 12%, driven by holiday promotions.",
            "Customer acquisition cost decreased 8% while retention improved to 94%.",
            "Q4 accounted for 35% of annual revenue — plan for similar seasonal patterns in 2026.",
            "Recommend increasing marketing budget for Q1 2026 to capitalize on momentum.",
        )) {
            Text("  •  $note", fontSize = 11.sp)
            Spacer(Modifier.height(4.dp))
        }
    }
}
