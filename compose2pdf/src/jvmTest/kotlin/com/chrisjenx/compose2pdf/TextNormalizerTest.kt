package com.chrisjenx.compose2pdf

import com.chrisjenx.compose2pdf.internal.TextNormalizer
import kotlin.test.Test
import kotlin.test.assertEquals

class TextNormalizerTest {

    @Test
    fun `text without ligatures passes through unchanged`() {
        val result = TextNormalizer.normalize("Hello world", listOf(0f, 5f, 10f), 12f)
        assertEquals("Hello world", result.text)
        assertEquals(listOf(0f, 5f, 10f), result.xPositions)
    }

    @Test
    fun `single ff ligature is decomposed`() {
        // "eﬀect" (5 chars) → "effect" (6 chars)
        val positions = listOf(0f, 10f, 20f, 30f, 40f)
        val result = TextNormalizer.normalize("e\uFB00ect", positions, 12f)
        assertEquals("effect", result.text)
        assertEquals(6, result.xPositions.size)
        // 'e' keeps original position
        assertEquals(0f, result.xPositions[0])
        // first 'f' gets position of ligature, second 'f' interpolated to midpoint
        assertEquals(10f, result.xPositions[1])
        assertEquals(15f, result.xPositions[2])
        // remaining chars keep original positions
        assertEquals(20f, result.xPositions[3])
        assertEquals(30f, result.xPositions[4])
        assertEquals(40f, result.xPositions[5])
    }

    @Test
    fun `ffi ligature decomposes to three characters`() {
        // "\uFB03x" (2 chars) → "ffix" (4 chars)
        val positions = listOf(0f, 30f)
        val result = TextNormalizer.normalize("\uFB03x", positions, 12f)
        assertEquals("ffix", result.text)
        assertEquals(4, result.xPositions.size)
        assertEquals(0f, result.xPositions[0])
        assertEquals(10f, result.xPositions[1])
        assertEquals(20f, result.xPositions[2])
        assertEquals(30f, result.xPositions[3])
    }

    @Test
    fun `ligature at end of string uses fallback spacing`() {
        val positions = listOf(0f, 10f)
        val result = TextNormalizer.normalize("a\uFB00", positions, 12f)
        assertEquals("aff", result.text)
        assertEquals(3, result.xPositions.size)
        assertEquals(0f, result.xPositions[0])
        assertEquals(10f, result.xPositions[1])
        // fallback: startX + fontSize * 0.3 * 1 / 2 = 10 + 12*0.3*1/2 = 10 + 1.8
        val expectedSecondF = 10f + (10f + 12f * 0.3f * 2 - 10f) / 2f
        assertEquals(expectedSecondF, result.xPositions[2], 0.01f)
    }

    @Test
    fun `no x-positions decomposes text but returns positions unchanged`() {
        val result = TextNormalizer.normalize("e\uFB00ect", listOf(10f), 12f)
        assertEquals("effect", result.text)
        assertEquals(listOf(10f), result.xPositions)
    }

    @Test
    fun `empty x-positions decomposes text only`() {
        val result = TextNormalizer.normalize("\uFB01nd", emptyList(), 12f)
        assertEquals("find", result.text)
        assertEquals(emptyList(), result.xPositions)
    }

    @Test
    fun `multiple ligatures in one string`() {
        // "\uFB01\uFB02" (2 chars) → "fifl" (4 chars)
        val positions = listOf(0f, 20f)
        val result = TextNormalizer.normalize("\uFB01\uFB02", positions, 12f)
        assertEquals("fifl", result.text)
        assertEquals(4, result.xPositions.size)
        assertEquals(0f, result.xPositions[0])
        assertEquals(10f, result.xPositions[1])
        // second ligature at end uses fallback
        assertEquals(20f, result.xPositions[2])
    }

    @Test
    fun `all seven ligatures decompose correctly`() {
        assertEquals("ff", TextNormalizer.normalize("\uFB00", emptyList(), 12f).text)
        assertEquals("fi", TextNormalizer.normalize("\uFB01", emptyList(), 12f).text)
        assertEquals("fl", TextNormalizer.normalize("\uFB02", emptyList(), 12f).text)
        assertEquals("ffi", TextNormalizer.normalize("\uFB03", emptyList(), 12f).text)
        assertEquals("ffl", TextNormalizer.normalize("\uFB04", emptyList(), 12f).text)
        assertEquals("st", TextNormalizer.normalize("\uFB05", emptyList(), 12f).text)
        assertEquals("st", TextNormalizer.normalize("\uFB06", emptyList(), 12f).text)
    }
}
