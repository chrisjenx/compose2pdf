package com.chrisjenx.compose2pdf.examples

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chrisjenx.compose2pdf.PdfPageConfig
import com.chrisjenx.compose2pdf.renderToPdf

private const val TOTAL_PAGES = 3

// --- snippet start ---
fun multiPageDocument() = listOf(
    ExampleOutput(
        name = "08-multi-page",
        sourceFile = "08_MultiPageDocument.kt",
        pdfBytes = renderToPdf(
            pages = TOTAL_PAGES,
            config = PdfPageConfig.A4WithMargins,
        ) { pageIndex ->
            Column(Modifier.fillMaxSize()) {
                // Shared header
                PageHeader("Quarterly Report — Q1 2025")

                Spacer(Modifier.height(24.dp))

                // Page-specific content
                when (pageIndex) {
                    0 -> CoverPage()
                    1 -> DataPage()
                    2 -> SummaryPage()
                }

                // Push footer to bottom
                Spacer(Modifier.weight(1f))

                // Shared footer with page number
                PageFooter(pageIndex + 1, TOTAL_PAGES)
            }
        },
    )
)
// --- snippet end ---

@Composable
private fun PageHeader(title: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Color(0xFF1565C0))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

@Composable
private fun PageFooter(current: Int, total: Int) {
    Divider(color = Color(0xFFBDBDBD))
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text("Acme Corp — Confidential", fontSize = 10.sp, color = Color.Gray)
        Text("Page $current of $total", fontSize = 10.sp, color = Color.Gray)
    }
}

@Composable
private fun CoverPage() {
    Column(
        Modifier.fillMaxWidth().padding(top = 80.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Quarterly Report", fontSize = 36.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("Q1 2025 — January to March", fontSize = 18.sp, color = Color.Gray)
        Spacer(Modifier.height(32.dp))
        Text("Prepared by the Analytics Team", fontSize = 14.sp)
        Text("March 31, 2025", fontSize = 14.sp, color = Color.Gray)
    }
}

@Composable
private fun DataPage() {
    Column {
        Text("Key Metrics", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))

        // Table header
        Row(
            Modifier
                .fillMaxWidth()
                .background(Color(0xFFE3F2FD))
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text("Metric", Modifier.weight(2f), fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Text("Jan", Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 12.sp, textAlign = TextAlign.End)
            Text("Feb", Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 12.sp, textAlign = TextAlign.End)
            Text("Mar", Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 12.sp, textAlign = TextAlign.End)
        }

        val data = listOf(
            listOf("Revenue", "$41,200", "$38,900", "$45,600"),
            listOf("New Customers", "124", "108", "156"),
            listOf("Churn Rate", "2.1%", "1.8%", "1.5%"),
            listOf("NPS Score", "72", "74", "78"),
            listOf("Support Tickets", "342", "298", "275"),
        )

        for ((index, row) in data.withIndex()) {
            val bg = if (index % 2 == 0) Color.Transparent else Color(0xFFFAFAFA)
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(bg)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(row[0], Modifier.weight(2f), fontSize = 12.sp)
                Text(row[1], Modifier.weight(1f), fontSize = 12.sp, textAlign = TextAlign.End)
                Text(row[2], Modifier.weight(1f), fontSize = 12.sp, textAlign = TextAlign.End)
                Text(row[3], Modifier.weight(1f), fontSize = 12.sp, textAlign = TextAlign.End)
            }
        }
    }
}

@Composable
private fun SummaryPage() {
    Column {
        Text("Summary & Outlook", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))

        Text("Key Highlights", fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Spacer(Modifier.height(8.dp))
        for (highlight in listOf(
            "Revenue grew 10.7% from January to March",
            "Customer acquisition improved 25.8% in March",
            "Churn rate decreased from 2.1% to 1.5%",
            "NPS score reached all-time high of 78",
        )) {
            Text("  •  $highlight", fontSize = 12.sp)
            Spacer(Modifier.height(4.dp))
        }

        Spacer(Modifier.height(24.dp))
        Text("Q2 Priorities", fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Spacer(Modifier.height(8.dp))
        for ((i, priority) in listOf(
            "Launch self-service portal to reduce support tickets by 20%",
            "Expand into APAC market with localized onboarding",
            "Target $50K monthly revenue by June",
        ).withIndex()) {
            Text("  ${i + 1}. $priority", fontSize = 12.sp)
            Spacer(Modifier.height(4.dp))
        }
    }
}
