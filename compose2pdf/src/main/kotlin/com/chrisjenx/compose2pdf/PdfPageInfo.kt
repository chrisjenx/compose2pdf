package com.chrisjenx.compose2pdf

/**
 * Page information passed to `header`/`footer` slots during [renderToPdf].
 *
 * Deliberately a plain class (not a `data class`) so fields can be added later
 * without breaking binary compatibility.
 *
 * @property pageIndex Zero-based index of the page being rendered.
 * @property pageCount Total number of emitted pages. If auto-pagination truncates
 *   at its page cap, this is the emitted (truncated) count.
 */
class PdfPageInfo(
    val pageIndex: Int,
    val pageCount: Int,
) {
    /** One-based page number, for display: `"Page $pageNumber of $pageCount"`. */
    val pageNumber: Int get() = pageIndex + 1

    init {
        require(pageIndex >= 0) { "pageIndex must be non-negative, was $pageIndex" }
        require(pageCount > 0) { "pageCount must be positive, was $pageCount" }
        require(pageIndex < pageCount) { "pageIndex ($pageIndex) must be < pageCount ($pageCount)" }
    }

    override fun toString(): String = "PdfPageInfo(pageIndex=$pageIndex, pageCount=$pageCount)"
}
