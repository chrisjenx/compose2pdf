package com.chrisjenx.compose2pdf

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import com.chrisjenx.compose2pdf.internal.PaginatedColumn as InternalPaginatedColumn

/**
 * A Column-like layout that prevents direct children from being split across
 * PDF page boundaries.
 *
 * When a child would straddle a page boundary, padding is inserted to push it
 * to the next page. A single child taller than a page flows continuously across
 * pages without special treatment.
 *
 * This composable reads the page configuration from [LocalPdfPageConfig]
 * automatically and must be used inside [renderToPdf].
 *
 * ## When to use
 *
 * [renderToPdf] with [PdfPagination.AUTO] already wraps your content in a
 * `PaginatedColumn` internally. Use this composable explicitly when you need
 * finer control — for example, if your content is wrapped in a provider,
 * `Column`, or `Box` that hides individual children from the automatic
 * pagination:
 *
 * ```kotlin
 * renderToPdf(config = PdfPageConfig.A4WithMargins) {
 *     MyThemeProvider {          // wraps everything — auto-pagination sees 1 child
 *         PaginatedColumn {      // restores per-child page breaking
 *             items.forEach { item ->
 *                 ItemCard(item) // each card is a "keep-together" unit
 *             }
 *         }
 *     }
 * }
 * ```
 *
 * @param modifier Modifier applied to the layout.
 * @param content The composable children. Each direct child is treated as a
 *   "keep-together" unit for page breaking.
 * @throws IllegalStateException if used outside of [renderToPdf].
 * @see LocalPdfPageConfig
 * @see PdfPagination.AUTO
 */
@Composable
fun PaginatedColumn(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val pageConfig = LocalPdfPageConfig.current
        ?: error(
            "PaginatedColumn must be used inside renderToPdf { }. " +
                "No PdfPageConfig found in the composition."
        )
    val density = LocalDensity.current
    val contentHeightPx = (pageConfig.contentHeight.value * density.density).toInt()
    InternalPaginatedColumn(
        contentHeightPx = contentHeightPx,
        modifier = modifier,
        content = content,
    )
}
