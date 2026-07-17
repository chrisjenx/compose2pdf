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
 * When [renderToPdf] is called with `header`/`footer` slots, the config seen by the
 * BODY `content` reflects the *effective* content area: margins are inflated by the
 * measured band heights, so `contentHeight` is already reduced by the header/footer
 * bands. This is what keeps [PaginatedColumn] breaking pages at the right height without
 * needing to know about the bands itself. The `header`/`footer` slot content, by
 * contrast, sees the original, unreduced page config.
 *
 * @see PdfPageConfig
 * @see renderToPdf
 */
val LocalPdfPageConfig = compositionLocalOf<PdfPageConfig?> { null }
