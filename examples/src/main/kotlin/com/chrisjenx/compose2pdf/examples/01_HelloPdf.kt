package com.chrisjenx.compose2pdf.examples

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chrisjenx.compose2pdf.renderToPdf

// --- snippet start ---
fun helloPdf() = listOf(
    ExampleOutput(
        name = "01-hello",
        sourceFile = "01_HelloPdf.kt",
        pdfBytes = renderToPdf {
            Column(Modifier.fillMaxSize().padding(32.dp)) {
                Text("Hello, PDF!", fontSize = 28.sp)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Generated with compose2pdf",
                    fontSize = 14.sp,
                    color = Color.Gray,
                )
            }
        },
    )
)
// --- snippet end ---
