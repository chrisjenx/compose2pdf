package com.chrisjenx.compose2pdf

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PdfPageInfoTest {

    @Test
    fun `pageNumber is one-based`() {
        assertEquals(1, PdfPageInfo(pageIndex = 0, pageCount = 3).pageNumber)
        assertEquals(3, PdfPageInfo(pageIndex = 2, pageCount = 3).pageNumber)
    }

    @Test
    fun `rejects invalid values`() {
        assertFailsWith<IllegalArgumentException> { PdfPageInfo(pageIndex = -1, pageCount = 1) }
        assertFailsWith<IllegalArgumentException> { PdfPageInfo(pageIndex = 0, pageCount = 0) }
        assertFailsWith<IllegalArgumentException> { PdfPageInfo(pageIndex = 2, pageCount = 2) }
    }
}
