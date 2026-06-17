package com.chrisjenx.compose2pdf

import com.chrisjenx.compose2pdf.internal.calculatePageBreakPositions
import kotlin.test.Test
import kotlin.test.assertEquals

class PaginatedColumnTest {

    @Test
    fun `children that fit on one page - no padding`() {
        val positions = calculatePageBreakPositions(listOf(100, 200, 300), pageHeightPx = 700)

        assertEquals(0, positions[0]) // child 0 at Y=0
        assertEquals(100, positions[1]) // child 1 at Y=100
        assertEquals(300, positions[2]) // child 2 at Y=300
    }

    @Test
    fun `child that straddles boundary is pushed to next page`() {
        // Page height = 700. Child 0 = 600, child 1 = 200.
        // 600 + 200 = 800 > 700, so child 1 is pushed to Y=700 (next page).
        val positions = calculatePageBreakPositions(listOf(600, 200), pageHeightPx = 700)

        assertEquals(0, positions[0]) // child 0 at Y=0
        assertEquals(700, positions[1]) // child 1 pushed to page 2
    }

    @Test
    fun `oversized child is placed normally - no push`() {
        // Child is 1500px, page is 700px. Can't fit on any single page, so place normally.
        val positions = calculatePageBreakPositions(listOf(1500), pageHeightPx = 700)

        assertEquals(0, positions[0]) // placed at Y=0, spans pages
    }

    @Test
    fun `oversized child after normal children`() {
        // Child 0 = 300, child 1 = 1500 (oversized). Child 1 can't fit on a fresh page,
        // so it's placed at Y=300, spanning across pages.
        val positions = calculatePageBreakPositions(listOf(300, 1500), pageHeightPx = 700)

        assertEquals(0, positions[0])
        assertEquals(300, positions[1]) // oversized, placed normally
    }

    @Test
    fun `elements fit exactly on one page`() {
        val positions = calculatePageBreakPositions(listOf(300, 400), pageHeightPx = 700)

        assertEquals(0, positions[0])
        assertEquals(300, positions[1]) // total = 700 = pageHeight exactly
    }

    @Test
    fun `multiple page breaks`() {
        // Page height = 500. Children: 400, 400, 400.
        // Page 1: child 0 (400). Child 1 would be 400+400=800>500 → push to page 2.
        // Page 2: child 1 (400). Child 2 would be 400+400=800>500 → push to page 3.
        val positions = calculatePageBreakPositions(listOf(400, 400, 400), pageHeightPx = 500)

        assertEquals(0, positions[0]) // page 1, Y=0
        assertEquals(500, positions[1]) // page 2, Y=500
        assertEquals(1000, positions[2]) // page 3, Y=1000
    }

    @Test
    fun `empty list`() {
        val positions = calculatePageBreakPositions(emptyList(), pageHeightPx = 700)
        assertEquals(0, positions.size)
    }

    @Test
    fun `first element exactly page height`() {
        // Child 0 fills entire page. Child 1 goes to page 2.
        val positions = calculatePageBreakPositions(listOf(700, 100), pageHeightPx = 700)

        assertEquals(0, positions[0])
        assertEquals(700, positions[1]) // next page
    }

    @Test
    fun `small elements packing across pages`() {
        // Page = 100. Children: 40, 40, 40, 40, 40.
        // Page 1: 40+40=80, next 40 would be 80+40=120>100 → push.
        // Page 2: 40+40=80, next 40 → push.
        // Page 3: 40.
        val positions = calculatePageBreakPositions(listOf(40, 40, 40, 40, 40), pageHeightPx = 100)

        assertEquals(0, positions[0])   // page 1
        assertEquals(40, positions[1])  // page 1
        assertEquals(100, positions[2]) // page 2 (pushed)
        assertEquals(140, positions[3]) // page 2
        assertEquals(200, positions[4]) // page 3 (pushed)
    }
}
