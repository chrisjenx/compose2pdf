package com.chrisjenx.compose2pdf.internal

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout

/**
 * A Column-like layout that inserts padding at page boundaries so that no direct child
 * is split across pages.
 *
 * Children are measured and placed top-to-bottom. When a child would straddle a page
 * boundary, padding is inserted to push it to the next page start. This means
 * fixed-interval page slicing produces clean breaks.
 *
 * If a single child is taller than a page, it is placed normally and will flow
 * continuously across pages (clipped at each boundary).
 *
 * @param contentHeightPx The page content area height in pixels. When <= 0, behaves
 *   like a simple vertical stack (no pagination logic).
 */
@Composable
internal fun PaginatedColumn(
    contentHeightPx: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Layout(
        content = content,
        modifier = modifier,
    ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints.copy(minHeight = 0)) }
        val heights = placeables.map { it.height }

        if (contentHeightPx <= 0) {
            // No pagination — simple vertical stack
            val totalHeight = heights.sum()
            return@Layout layout(constraints.maxWidth, totalHeight) {
                var y = 0
                for (placeable in placeables) {
                    placeable.placeRelative(0, y)
                    y += placeable.height
                }
            }
        }

        // Calculate Y positions with page-break padding
        val yPositions = calculatePageBreakPositions(heights, contentHeightPx)
        val totalHeight = if (yPositions.isEmpty()) 0 else {
            yPositions.indices.maxOf { i -> yPositions[i] + heights[i] }
        }

        layout(constraints.maxWidth, totalHeight) {
            for (i in placeables.indices) {
                placeables[i].placeRelative(0, yPositions[i])
            }
        }
    }
}

/**
 * Calculates Y positions for children, inserting padding at page boundaries.
 *
 * @param childHeights Heights of each child in pixels.
 * @param pageHeightPx Page content area height in pixels.
 * @return List of Y positions (one per child).
 */
internal fun calculatePageBreakPositions(
    childHeights: List<Int>,
    pageHeightPx: Int,
): List<Int> {
    val positions = mutableListOf<Int>()
    var currentY = 0

    for (height in childHeights) {
        val pageUsed = currentY % pageHeightPx
        val remainingOnPage = pageHeightPx - pageUsed

        // Push to next page if:
        // 1. The child would straddle the boundary (doesn't fit in remaining space)
        // 2. There's already content on this page (pageUsed > 0)
        // 3. The child actually fits on a fresh page (height <= pageHeightPx)
        if (height > remainingOnPage && pageUsed > 0 && height <= pageHeightPx) {
            currentY += remainingOnPage // skip to next page boundary
        }

        positions.add(currentY)
        currentY += height
    }

    return positions
}
