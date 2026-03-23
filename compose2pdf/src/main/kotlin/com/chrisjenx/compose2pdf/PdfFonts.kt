package com.chrisjenx.compose2pdf

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font

/** Bundled Inter font family for PDF rendering. Uses static (non-variable) Inter fonts. */
val InterFontFamily: FontFamily = FontFamily(
    Font(resource = "fonts/Inter-Regular.ttf", weight = FontWeight.Normal, style = FontStyle.Normal),
    Font(resource = "fonts/Inter-Bold.ttf", weight = FontWeight.Bold, style = FontStyle.Normal),
    Font(resource = "fonts/Inter-Italic.ttf", weight = FontWeight.Normal, style = FontStyle.Italic),
    Font(resource = "fonts/Inter-BoldItalic.ttf", weight = FontWeight.Bold, style = FontStyle.Italic),
)
