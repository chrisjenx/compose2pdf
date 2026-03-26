package com.chrisjenx.compose2pdf

/**
 * Controls how content is distributed across PDF pages.
 */
enum class PdfPagination {
    /**
     * Automatically split content across multiple pages when it overflows.
     *
     * Direct children of the content block are treated as "keep-together" units —
     * if a child would straddle a page boundary, it is pushed to the next page.
     * A single child taller than a page flows continuously across pages.
     *
     * For best results, place content items as direct children in `renderToPdf { }`
     * rather than wrapping in a single `Column`.
     */
    AUTO,

    /**
     * Render all content on a single page, clipping anything that overflows.
     */
    SINGLE_PAGE,
}
