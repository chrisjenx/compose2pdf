package com.chrisjenx.compose2pdf.internal

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.font.PDFont
import org.apache.pdfbox.pdmodel.font.PDType0Font
import org.jetbrains.skia.Typeface
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap

/**
 * Embeds a Skia [Typeface] into a PDF by reconstructing its font file from Skia's
 * font-table API ([Typeface.getTableTags]/[Typeface.getTableData]) and loading the
 * rebuilt sfnt bytes into PDFBox. This gives the PDF the exact font Skia shaped the
 * text with — glyph widths match the SVG's per-glyph positions by construction.
 */
internal object SkiaTypefaceEmbedder {

    private val logger = java.util.logging.Logger.getLogger(SkiaTypefaceEmbedder::class.java.name)

    // Reconstructed font bytes per Skia typeface id (fonts are process-lifetime objects)
    private val bytesCache = ConcurrentHashMap<Int, Optional<ByteArray>>()

    /**
     * Embeds [typeface] into [doc] as a subset PDType0Font, or returns null when the
     * typeface can't be faithfully embedded (non-default variable instance, table
     * extraction failure, or PDFBox rejecting the rebuilt font).
     */
    fun embed(doc: PDDocument, typeface: Typeface): PDFont? {
        if (hasNonDefaultVariation(typeface)) {
            // PDFBox renders variable fonts at their default axis values; embedding a
            // non-default instance (e.g. wght=700 of a variable font) would draw the
            // wrong glyphs. Let FontResolver fall through to its other strategies.
            logger.fine("Skipping variable font instance '${typeface.familyName}' (non-default axes)")
            return null
        }
        val bytes = bytesCache.getOrPut(typeface.uniqueId) {
            Optional.ofNullable(
                try {
                    reconstructSfnt(typeface)
                } catch (t: Throwable) {
                    logger.fine("Failed to reconstruct font '${typeface.familyName}': ${t.message}")
                    null
                },
            )
        }.orElse(null) ?: return null
        return try {
            PDType0Font.load(doc, bytes.inputStream())
        } catch (e: Exception) {
            logger.fine("PDFBox rejected rebuilt font '${typeface.familyName}': ${e.message}")
            null
        }
    }

    // Axes that change glyph shapes/metrics substantially. Optical size (opsz) or grade
    // (GRAD) deviations are visually negligible, and rejecting them would exclude e.g.
    // macOS's ".SF NS" default font, whose regular instance reports a non-default opsz.
    private val STYLE_AXES = setOf("wght", "wdth", "slnt", "ital")

    /** True when the typeface is a variable font styled away from its default axis values. */
    private fun hasNonDefaultVariation(typeface: Typeface): Boolean {
        val axes = typeface.variationAxes?.takeIf { it.isNotEmpty() } ?: return false
        val variations = typeface.variations?.takeIf { it.isNotEmpty() } ?: return false
        return variations.any { v ->
            if (v.tag !in STYLE_AXES) return@any false
            val axis = axes.firstOrNull { it.tag == v.tag } ?: return@any false
            v.value != axis.defaultValue
        }
    }

    /** Rebuilds a TrueType/OpenType font file from the typeface's raw sfnt tables. */
    private fun reconstructSfnt(typeface: Typeface): ByteArray? {
        val tables = typeface.tableTags.orEmpty().mapNotNull { tag ->
            val bytes = typeface.getTableData(tag)?.bytes
            if (bytes == null || bytes.isEmpty()) null else tag to bytes
        }
        if (tables.none { it.first == "glyf" || it.first == "CFF " }) return null

        val numTables = tables.size
        val sfntVersion = if (tables.any { it.first == "CFF " }) 0x4F54544F else 0x00010000
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
}
