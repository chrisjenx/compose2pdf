package com.chrisjenx.compose2pdf.examples

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Divider
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chrisjenx.compose2pdf.PdfLink
import com.chrisjenx.compose2pdf.PdfPageConfig
import com.chrisjenx.compose2pdf.PdfRoundedCornerShape
import com.chrisjenx.compose2pdf.renderToPdf

// Invoice data model
private data class LineItem(
    val description: String,
    val quantity: Int,
    val rate: Double,
) {
    val amount: Double get() = quantity * rate
}

private val invoiceItems = listOf(
    LineItem("Website Design & Development", 1, 4500.00),
    LineItem("UI/UX Consultation (hours)", 12, 150.00),
    LineItem("Logo & Brand Identity Package", 1, 1200.00),
    LineItem("SEO Optimization Setup", 1, 800.00),
    LineItem("Content Writing (pages)", 8, 125.00),
    LineItem("Hosting Setup & Configuration", 1, 350.00),
)

private val navy = Color(0xFF1A237E)
private val lightGray = Color(0xFFF5F5F5)
private val amber = Color(0xFFFFF8E1)
private val amberBorder = Color(0xFFFFB300)

// --- snippet start ---
fun professionalInvoice() = listOf(
    ExampleOutput(
        name = "10-invoice",
        sourceFile = "10_ProfessionalInvoice.kt",
        pdfBytes = renderToPdf(config = PdfPageConfig.LetterWithMargins) {
            Column(Modifier.fillMaxSize()) {
                InvoiceHeader()
                Spacer(Modifier.height(24.dp))
                AddressSection()
                Spacer(Modifier.height(24.dp))
                LineItemsTable()
                Spacer(Modifier.height(16.dp))
                TotalsSection()
                Spacer(Modifier.height(24.dp))
                PaymentTerms()
                Spacer(Modifier.weight(1f))
                InvoiceFooter()
            }
        },
    )
)
// --- snippet end ---

@Composable
private fun InvoiceHeader() {
    Row(
        Modifier
            .fillMaxWidth()
            .background(navy)
            .padding(20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Company logo placeholder
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(PdfRoundedCornerShape(topStart = 12.dp, bottomEnd = 12.dp))
                    .background(Color.White),
                contentAlignment = Alignment.Center,
            ) {
                Text("AC", color = navy, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Acme Creative", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Text("Design & Development Studio", color = Color(0xFFB0BEC5), fontSize = 11.sp)
            }
        }
        // Invoice number + date
        Column(horizontalAlignment = Alignment.End) {
            Text("INVOICE", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 24.sp)
            Text("#INV-2025-0042", color = Color(0xFFB0BEC5), fontSize = 12.sp)
            Text("March 15, 2025", color = Color(0xFFB0BEC5), fontSize = 12.sp)
        }
    }
}

@Composable
private fun AddressSection() {
    Row(Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            Text("FROM", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
            Spacer(Modifier.height(4.dp))
            Text("Acme Creative LLC", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Text("123 Design Street", fontSize = 12.sp)
            Text("San Francisco, CA 94102", fontSize = 12.sp)
            Text("hello@acmecreative.com", fontSize = 12.sp)
        }
        Column(Modifier.weight(1f)) {
            Text("BILL TO", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
            Spacer(Modifier.height(4.dp))
            Text("Globex Corporation", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Text("456 Enterprise Ave", fontSize = 12.sp)
            Text("New York, NY 10001", fontSize = 12.sp)
            Text("billing@globex.com", fontSize = 12.sp)
        }
    }
}

@Composable
private fun LineItemsTable() {
    // Table header
    Row(
        Modifier
            .fillMaxWidth()
            .background(navy)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text("Description", Modifier.weight(3f), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
        Text("Qty", Modifier.weight(1f), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp, textAlign = TextAlign.Center)
        Text("Rate", Modifier.weight(1f), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp, textAlign = TextAlign.End)
        Text("Amount", Modifier.weight(1f), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp, textAlign = TextAlign.End)
    }

    // Table rows
    for ((index, item) in invoiceItems.withIndex()) {
        val bg = if (index % 2 == 0) Color.White else lightGray
        Row(
            Modifier
                .fillMaxWidth()
                .background(bg)
                .padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Text(item.description, Modifier.weight(3f), fontSize = 11.sp)
            Text("${item.quantity}", Modifier.weight(1f), fontSize = 11.sp, textAlign = TextAlign.Center)
            Text("$${"%,.2f".format(item.rate)}", Modifier.weight(1f), fontSize = 11.sp, textAlign = TextAlign.End)
            Text("$${"%,.2f".format(item.amount)}", Modifier.weight(1f), fontSize = 11.sp, textAlign = TextAlign.End)
        }
    }
}

@Composable
private fun TotalsSection() {
    val subtotal = invoiceItems.sumOf { it.amount }
    val discount = subtotal * 0.10
    val afterDiscount = subtotal - discount
    val tax = afterDiscount * 0.08
    val total = afterDiscount + tax

    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
        TotalRow("Subtotal", subtotal)
        TotalRow("Discount (10%)", -discount, Color(0xFF388E3C))
        TotalRow("Tax (8%)", tax)
        Spacer(Modifier.height(4.dp))
        Divider(Modifier.fillMaxWidth(0.4f))
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(0.4f), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Total Due", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text("$${"%,.2f".format(total)}", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = navy)
        }
    }
}

@Composable
private fun TotalRow(label: String, amount: Double, color: Color = Color.Unspecified) {
    Row(Modifier.fillMaxWidth(0.4f), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 12.sp, color = Color.Gray)
        val prefix = if (amount < 0) "-$" else "$"
        Text("$prefix${"%,.2f".format(kotlin.math.abs(amount))}", fontSize = 12.sp, color = color)
    }
}

@Composable
private fun PaymentTerms() {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(amber)
            .padding(16.dp)
    ) {
        Column {
            Text("Payment Terms", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFFF57F17))
            Spacer(Modifier.height(4.dp))
            Text("Payment due within 30 days of invoice date. Please include invoice number with payment.", fontSize = 11.sp)
            Spacer(Modifier.height(4.dp))
            Text("Bank transfer details provided upon request. Late payments subject to 1.5% monthly fee.", fontSize = 11.sp, color = Color.Gray)
        }
    }
}

@Composable
private fun InvoiceFooter() {
    Divider(color = Color(0xFFBDBDBD))
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text("Thank you for your business!", fontSize = 11.sp, color = Color.Gray)
        PdfLink(href = "https://pay.acmecreative.com/inv-2025-0042") {
            Text(
                "Pay Online →",
                fontSize = 11.sp,
                color = Color(0xFF1565C0),
                textDecoration = TextDecoration.Underline,
            )
        }
    }
}
