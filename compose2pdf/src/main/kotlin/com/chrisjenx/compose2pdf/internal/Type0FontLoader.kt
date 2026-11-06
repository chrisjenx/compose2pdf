package com.chrisjenx.compose2pdf.internal

import org.apache.fontbox.ttf.TTFParser
import org.apache.pdfbox.io.RandomAccessReadBuffer
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.font.PDType0Font
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream

/**
 * The single way compose2pdf turns TrueType/OpenType bytes into an embedded [PDType0Font].
 *
 * Text reaches PDFBox already shaped by Skia and positioned glyph-by-glyph, so PDFBox must
 * never apply the font's GSUB table again. Left intact, GSUB costs twice:
 *  - `TTFParser.parse` eagerly reads every table, building GSUB lookup data per document
 *    (with heavy fontbox DEBUG logging when the consumer's logging is unconfigured), and
 *  - `PDPageContentStream.showText` runs a GsubWorker (a regex compile per word) on every call.
 * Both are avoided by dropping GSUB from the sfnt bytes before parsing; `enableGsub = false`
 * is kept as a belt-and-braces guard. GSUB is never part of a PDFBox subset, so the embedded
 * font bytes are unchanged.
 *
 * Only bytes are cached across documents (by callers): a parsed `TrueTypeFont` wraps a
 * positioned, non-thread-safe reader and a `PDType0Font` belongs to one document, so neither
 * may be shared between concurrent renders.
 */
internal object Type0FontLoader {

    private val STRIPPED_TABLES = setOf("GSUB")

    /** Embeds [bytes] (a GSUB-free sfnt, see [withoutGsub]) into [doc] as a subset font. */
    fun load(doc: PDDocument, bytes: ByteArray): PDType0Font {
        val ttf = TTFParser().parse(RandomAccessReadBuffer(bytes))
        try {
            ttf.isEnableGsub = false
            val font = PDType0Font.load(doc, ttf, true)
            // Subsetting reads the TTF at save time — close it with the document, not before.
            doc.registerTrueTypeFontForClosing(ttf)
            return font
        } catch (t: Throwable) {
            ttf.close()
            throw t
        }
    }

    /**
     * Returns [bytes] with the GSUB table removed, or [bytes] unchanged when it has none or
     * isn't a single-font sfnt (TrueType `0x00010000`/`true` or CFF `OTTO`).
     */
    fun withoutGsub(bytes: ByteArray): ByteArray {
        if (bytes.size < 12) return bytes
        val version = readU32(bytes, 0)
        if (version != 0x00010000 && version != TAG_TRUE && version != TAG_OTTO) return bytes
        val numTables = readU16(bytes, 4)
        if (bytes.size < 12 + numTables * 16) return bytes
        val tables = (0 until numTables).map { i ->
            val entry = 12 + i * 16
            Table(String(bytes, entry, 4, Charsets.ISO_8859_1), readU32(bytes, entry + 8), readU32(bytes, entry + 12))
        }
        if (tables.none { it.tag in STRIPPED_TABLES }) return bytes
        if (tables.any { it.offset < 0 || it.length < 0 || it.offset.toLong() + it.length > bytes.size }) return bytes
        return writeSfnt(
            version,
            tables.filter { it.tag !in STRIPPED_TABLES }
                .map { it.tag to bytes.copyOfRange(it.offset, it.offset + it.length) },
        )
    }

    /** Drops tables compose2pdf never wants PDFBox to see (GSUB) from a table list. */
    fun filterTables(tables: List<Pair<String, ByteArray>>): List<Pair<String, ByteArray>> =
        tables.filter { it.first !in STRIPPED_TABLES }

    /** Serialises [tables] as an sfnt font file with a valid offset table and directory. */
    fun writeSfnt(sfntVersion: Int, tables: List<Pair<String, ByteArray>>): ByteArray {
        val numTables = tables.size
        var entrySelector = 0
        var searchRange = 16
        while (searchRange * 2 <= numTables * 16) {
            searchRange *= 2
            entrySelector++
        }

        val baos = ByteArrayOutputStream()
        DataOutputStream(baos).use { out ->
            out.writeInt(sfntVersion)
            out.writeShort(numTables)
            out.writeShort(searchRange)
            out.writeShort(entrySelector)
            out.writeShort(numTables * 16 - searchRange)
            var offset = 12 + numTables * 16
            for ((tag, bytes) in tables) {
                out.writeBytes(tag.padEnd(4))
                out.writeInt(tableChecksum(bytes))
                out.writeInt(offset)
                out.writeInt(bytes.size)
                offset += (bytes.size + 3) and -4
            }
            for ((_, bytes) in tables) {
                out.write(bytes)
                repeat(((bytes.size + 3) and -4) - bytes.size) { out.writeByte(0) }
            }
        }
        return baos.toByteArray()
    }

    const val SFNT_TRUETYPE = 0x00010000
    const val SFNT_CFF = 0x4F54544F // 'OTTO'
    private const val TAG_TRUE = 0x74727565 // 'true'
    private const val TAG_OTTO = SFNT_CFF

    private class Table(val tag: String, val offset: Int, val length: Int)

    private fun tableChecksum(bytes: ByteArray): Int {
        var sum = 0
        var i = 0
        while (i < bytes.size) {
            var word = 0
            for (j in 0 until 4) {
                word = (word shl 8) or if (i + j < bytes.size) (bytes[i + j].toInt() and 0xFF) else 0
            }
            sum += word
            i += 4
        }
        return sum
    }

    private fun readU16(b: ByteArray, at: Int): Int = ((b[at].toInt() and 0xFF) shl 8) or (b[at + 1].toInt() and 0xFF)

    private fun readU32(b: ByteArray, at: Int): Int =
        ((b[at].toInt() and 0xFF) shl 24) or ((b[at + 1].toInt() and 0xFF) shl 16) or
            ((b[at + 2].toInt() and 0xFF) shl 8) or (b[at + 3].toInt() and 0xFF)
}
