package com.chrisjenx.compose2pdf

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PdfLinkCollectorTest {

    @Test
    fun `empty collector has no links`() {
        val collector = PdfLinkCollector()
        assertTrue(collector.links.isEmpty())
    }

    @Test
    fun `add stores annotation`() {
        val collector = PdfLinkCollector()
        collector.add(PdfLinkAnnotation("https://example.com", 10f, 20f, 100f, 30f))
        assertEquals(1, collector.links.size)
        assertEquals("https://example.com", collector.links[0].href)
    }

    @Test
    fun `add multiple annotations preserves order`() {
        val collector = PdfLinkCollector()
        collector.add(PdfLinkAnnotation("https://a.com", 0f, 0f, 50f, 20f))
        collector.add(PdfLinkAnnotation("https://b.com", 0f, 30f, 50f, 20f))
        collector.add(PdfLinkAnnotation("https://c.com", 0f, 60f, 50f, 20f))
        assertEquals(3, collector.links.size)
        assertEquals("https://a.com", collector.links[0].href)
        assertEquals("https://b.com", collector.links[1].href)
        assertEquals("https://c.com", collector.links[2].href)
    }

    @Test
    fun `clear removes all annotations`() {
        val collector = PdfLinkCollector()
        collector.add(PdfLinkAnnotation("https://a.com", 0f, 0f, 50f, 20f))
        collector.add(PdfLinkAnnotation("https://b.com", 0f, 30f, 50f, 20f))
        assertEquals(2, collector.links.size)
        collector.clear()
        assertTrue(collector.links.isEmpty())
    }

    @Test
    fun `annotation stores all coordinate fields`() {
        val annotation = PdfLinkAnnotation("https://test.com", 15.5f, 25.3f, 200.0f, 50.7f)
        assertEquals("https://test.com", annotation.href)
        assertEquals(15.5f, annotation.x)
        assertEquals(25.3f, annotation.y)
        assertEquals(200.0f, annotation.width)
        assertEquals(50.7f, annotation.height)
    }
}
