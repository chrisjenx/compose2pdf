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
 * When [renderToPdf] is called with `header`/`footer` slots, the bands render inside the
 * page margins, anchored ~18pt (0.25in) from the physical page edge — the config seen by
 * the BODY `content` reflects the *effective* content area, which equals the configured
 * margins unless a band (edge inset + band height + a 10pt gap) is taller than the margin
 * allows, in which case that side's inset grows just enough to fit it. In the common case
 * (a band that fits within the configured margin), `contentHeight` is unchanged from the
 * no-slots value. This is what keeps [PaginatedColumn] breaking pages at the right height
 * without needing to know about the bands itself. The `header`/`footer` slot content, by
 * contrast, sees the original, unreduced page config.
 *
 * @see PdfPageConfig
 * @see renderToPdf
 */
val LocalPdfPageConfig = compositionLocalOf<PdfPageConfig?> { null }
