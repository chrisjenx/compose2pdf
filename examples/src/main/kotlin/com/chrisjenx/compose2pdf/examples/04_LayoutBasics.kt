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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Divider
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chrisjenx.compose2pdf.PdfPageConfig
import com.chrisjenx.compose2pdf.renderToPdf

// --- snippet start ---
fun layoutBasics() = listOf(
    ExampleOutput(
        name = "04-layout-basics",
        sourceFile = "04_LayoutBasics.kt",
        pdfBytes = renderToPdf(config = PdfPageConfig.A4WithMargins) {
            Column(Modifier.fillMaxSize()) {
                // Section 1: Profile card with Row
                Text("Profile Card", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                ProfileCard()

                Spacer(Modifier.height(24.dp))
                Divider()
                Spacer(Modifier.height(24.dp))

                // Section 2: Two-column layout
                Text("Two-Column Layout", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                TwoColumnLayout()

                Spacer(Modifier.height(24.dp))
                Divider()
                Spacer(Modifier.height(24.dp))

                // Section 3: Stat boxes
                Text("Evenly Spaced Stats", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                StatBoxes()
            }
        },
    )
)
// --- snippet end ---

@Composable
private fun ProfileCard() {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Color(0xFFF5F5F5))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Avatar placeholder
        Box(
            Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(Color(0xFF1976D2)),
            contentAlignment = Alignment.Center,
        ) {
            Text("AJ", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }
        Spacer(Modifier.width(16.dp))
        Column {
            Text("Alex Johnson", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text("Senior Engineer", fontSize = 14.sp, color = Color.Gray)
            Text("alex@example.com", fontSize = 12.sp, color = Color(0xFF1976D2))
        }
    }
}

@Composable
private fun TwoColumnLayout() {
    Row(Modifier.fillMaxWidth().height(200.dp)) {
        // Sidebar
        Column(
            Modifier
                .weight(1f)
                .background(Color(0xFF263238))
                .padding(12.dp),
        ) {
            Text("Menu", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(Modifier.height(12.dp))
            for (item in listOf("Dashboard", "Reports", "Settings", "Help")) {
                Text(item, color = Color(0xFFB0BEC5), fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
            }
        }
        // Content
        Column(
            Modifier
                .weight(3f)
                .padding(16.dp),
        ) {
            Text("Dashboard", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(
                "Welcome back! Here's an overview of your recent activity. " +
                    "This area demonstrates a weighted column layout where the sidebar " +
                    "takes 1/4 and content takes 3/4 of the width.",
                fontSize = 12.sp,
                color = Color.DarkGray,
            )
        }
    }
}

@Composable
private fun StatBoxes() {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        for ((label, value, color) in listOf(
            Triple("Revenue", "$12,450", Color(0xFF1976D2)),
            Triple("Orders", "384", Color(0xFF388E3C)),
            Triple("Customers", "1,247", Color(0xFFF57C00)),
        )) {
            Column(
                Modifier
                    .weight(1f)
                    .background(color.copy(alpha = 0.1f))
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(value, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = color)
                Spacer(Modifier.height(4.dp))
                Text(label, fontSize = 12.sp, color = Color.Gray, textAlign = TextAlign.Center)
            }
        }
    }
}
