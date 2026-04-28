package com.chrisjenx.compose2pdf.internal

import com.chrisjenx.compose2pdf.PdfPageConfig

/**
 * Internal data class bundling the page dimension parameters needed by [SvgToPdfConverter].
 * Avoids passing 6 individual float parameters derived from [PdfPageConfig].
 */
internal data class PageLayout(
    val pageWidthPt: Float,
    val pageHeightPt: Float,
    val contentWidthPt: Float,
    val contentHeightPt: Float,
    val marginLeftPt: Float,
    val marginTopPt: Float,
) {
    companion object {
        fun from(config: PdfPageConfig) = PageLayout(
            pageWidthPt = config.width.value,
            pageHeightPt = config.height.value,
            contentWidthPt = config.contentWidth.value,
            contentHeightPt = config.contentHeight.value,
            marginLeftPt = config.margins.left.value,
            marginTopPt = config.margins.top.value,
        )

        fun full(widthPt: Float, heightPt: Float) = PageLayout(
            pageWidthPt = widthPt,
            pageHeightPt = heightPt,
            contentWidthPt = widthPt,
            contentHeightPt = heightPt,
            marginLeftPt = 0f,
            marginTopPt = 0f,
        )
    }
}
