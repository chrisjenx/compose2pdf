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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Divider
import androidx.compose.material.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chrisjenx.compose2pdf.PdfLink
import com.chrisjenx.compose2pdf.PdfPageConfig
import com.chrisjenx.compose2pdf.renderToPdf

// --- snippet start ---
fun linksInPdf() = listOf(
    ExampleOutput(
        name = "07-links",
        sourceFile = "07_LinksInPdf.kt",
        pdfBytes = renderToPdf(config = PdfPageConfig.A4WithMargins) {
            Column(Modifier.fillMaxSize()) {
                // Header with navigation links
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1565C0))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Acme Corp", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        PdfLink(href = "https://example.com/docs") {
                            Text("Docs", color = Color(0xFFBBDEFB), fontSize = 13.sp)
                        }
                        PdfLink(href = "https://example.com/pricing") {
                            Text("Pricing", color = Color(0xFFBBDEFB), fontSize = 13.sp)
                        }
                        PdfLink(href = "https://example.com/support") {
                            Text("Support", color = Color(0xFFBBDEFB), fontSize = 13.sp)
                        }
                    }
                }

                Spacer(Modifier.height(32.dp))

                // Simple text link
                Text("Simple Text Link", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                PdfLink(href = "https://github.com/nickcjx/compose2pdf") {
                    Text(
                        "View on GitHub →",
                        color = Color(0xFF1565C0),
                        textDecoration = TextDecoration.Underline,
                    )
                }

                Spacer(Modifier.height(24.dp))
                Divider()
                Spacer(Modifier.height(24.dp))

                // Button-style link
                Text("Button-Style Link", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                PdfLink(href = "https://example.com/get-started") {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF1565C0))
                            .padding(horizontal = 24.dp, vertical = 12.dp),
                    ) {
                        Text("Get Started", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(Modifier.height(24.dp))
                Divider()
                Spacer(Modifier.height(24.dp))

                // Inline links in content
                Text("Inline Links", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Row {
                    Text("Read our ", fontSize = 14.sp)
                    PdfLink(href = "https://example.com/terms") {
                        Text("Terms of Service", color = Color(0xFF1565C0), textDecoration = TextDecoration.Underline, fontSize = 14.sp)
                    }
                    Text(" and ", fontSize = 14.sp)
                    PdfLink(href = "https://example.com/privacy") {
                        Text("Privacy Policy", color = Color(0xFF1565C0), textDecoration = TextDecoration.Underline, fontSize = 14.sp)
                    }
                    Text(".", fontSize = 14.sp)
                }

                // Push footer to bottom
                Spacer(Modifier.weight(1f))

                // Footer with links
                Divider()
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    PdfLink(href = "mailto:contact@example.com") {
                        Text("contact@example.com", fontSize = 11.sp, color = Color(0xFF1565C0))
                    }
                    PdfLink(href = "https://twitter.com/example") {
                        Text("Twitter", fontSize = 11.sp, color = Color(0xFF1565C0))
                    }
                    PdfLink(href = "https://linkedin.com/company/example") {
                        Text("LinkedIn", fontSize = 11.sp, color = Color(0xFF1565C0))
                    }
                }
            }
        },
    )
)
// --- snippet end ---
