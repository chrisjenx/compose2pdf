package com.chrisjenx.compose2pdf

import androidx.compose.runtime.compositionLocalOf

/**
 * CompositionLocal providing the current [PdfPageConfig] during PDF rendering.
 *
 * Inside [renderToPdf], this provides the active page configuration including
 * page dimensions, margins, and computed content area. Outside of [renderToPdf],
 * the value is `null`.
 *
 * ```kotlin
 * renderToPdf(config = PdfPageConfig.A4WithMargins) {
 *     val pageConfig = LocalPdfPageConfig.current
 *     // pageConfig?.contentWidth, pageConfig?.contentHeight, etc.
 * }
 * ```
 *
 * @see PdfPageConfig
 * @see renderToPdf
 */
val LocalPdfPageConfig = compositionLocalOf<PdfPageConfig?> { null }
