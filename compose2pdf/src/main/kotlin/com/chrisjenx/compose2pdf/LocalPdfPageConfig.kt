package com.chrisjenx.compose2pdf

import androidx.compose.runtime.compositionLocalOf

/**
 * CompositionLocal providing the current [PdfPageConfig] during PDF rendering.
 * Used by internal pagination layout to determine page dimensions.
 * Null outside of [renderToPdf] calls.
 */
internal val LocalPdfPageConfig = compositionLocalOf<PdfPageConfig?> { null }
