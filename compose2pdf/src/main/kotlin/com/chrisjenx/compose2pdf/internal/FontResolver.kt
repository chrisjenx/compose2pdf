package com.chrisjenx.compose2pdf.internal

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.font.PDFont
import org.apache.pdfbox.pdmodel.font.PDType0Font
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import java.io.File
import java.io.RandomAccessFile
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap

/**
 * Resolves font-family/weight/style from SVG text elements to PDFBox fonts.
 *
 * Tries to find and embed system TrueType/OpenType fonts as PDType0Font (with automatic
 * subsetting). Falls back to the PDF standard 14 fonts (Helvetica/Times/Courier variants)
 * when a system font can't be found.
 */
internal object FontResolver {

    private val logger = java.util.logging.Logger.getLogger(FontResolver::class.java.name)

    // Global cache: font search key → file path (file system search is expensive)
    // Uses Optional because ConcurrentHashMap doesn't allow null values
    private val filePathCache = ConcurrentHashMap<String, Optional<File>>()

    /**
     * Resolves a font for use in a PDF document.
     *
     * @param doc The target PDDocument (embedded fonts are document-specific).
     * @param fontCache Per-document cache of already-loaded PDFont instances.
     *   Pass the same map for all pages in a document to reuse embedded fonts.
     * @param family Comma-separated font-family list from SVG (e.g., "'Inter', sans-serif").
     * @param weight Font weight from SVG (e.g., "bold", "700").
     * @param style Font style from SVG (e.g., "italic", "oblique").
     */
    fun resolve(
        doc: PDDocument,
        fontCache: MutableMap<String, PDFont>,
        family: String?,
        weight: String?,
        style: String?,
    ): PDFont {
        val families = parseFontFamilyList(family)
        val weightValue = numericWeight(weight)
        val bold = weightValue >= BOLD_THRESHOLD
        val italic = isItalic(style)
        val key = "${families.firstOrNull() ?: "sans-serif"}-$weightValue-$italic"

        fontCache[key]?.let { return it }

        // 1. Bundled fonts first (guaranteed to match Compose rendering).
        for (fam in families) {
            val path = bundledFontPath(fam, bold, italic) ?: continue
            loadOrReuse(fontCache, key, "bundled:$path") {
                Thread.currentThread().contextClassLoader?.getResourceAsStream(path)?.use { stream ->
                    try {
                        PDType0Font.load(doc, stream)
                    } catch (e: Exception) {
                        logger.warning("Failed to load bundled font '$fam' (bold=$bold, italic=$italic): ${e.message}")
                        null
                    }
                }
            }?.let { return it }
        }

        // 2. Typefaces the current render loaded — the exact fonts behind the SVG's glyph
        //    positions (resource/file fonts, platform defaults that aren't resolvable files).
        for (fam in families) {
            val typeface = ComposeFontStack.lookupCaptured(fam, weightValue, italic) ?: continue
            embedTypeface(doc, fontCache, key, typeface)?.let { return it }
        }
        // 3. The composition's Skia FontCollection — the shaper's own system/glyph-fallback lookup.
        for (fam in families) {
            val typeface = ComposeFontStack.findTypeface(fam, weightValue, italic, fam in GENERIC_FAMILIES) ?: continue
            embedTypeface(doc, fontCache, key, typeface)?.let { return it }
        }
        // 4. Skia's default font manager — for callers that never went through a composition.
        for (fam in families) {
            val typeface = ComposeFontStack.matchSystemTypeface(fam, weightValue, italic, fam in GENERIC_FAMILIES) ?: continue
            embedTypeface(doc, fontCache, key, typeface)?.let { return it }
        }

        // 5. A same-named font on the filesystem.
        for (fam in families) {
            if (fam in GENERIC_FAMILIES) continue
            val fontFile = resolveFile(fam, bold, italic) ?: continue
            loadOrReuse(fontCache, key, "file:${fontFile.path}") {
                try {
                    PDType0Font.load(doc, fontFile)
                } catch (e: Exception) {
                    logger.fine("Failed to load system font '$fam' from ${fontFile.path}: ${e.message}")
                    null
                }
            }?.let { return it }
        }

        // Fall back to standard PDF fonts
        val font = standardFont(families.firstOrNull(), bold, italic)
        if (warnedFallbacks.add(key)) {
            logger.warning(
                "Font family '${families.firstOrNull()}' (bold=$bold, italic=$italic) could not be " +
                    "resolved to an embeddable font; falling back to standard PDF font " +
                    "'${font.name}'. Its glyph widths differ from the font Compose laid the text " +
                    "out with, so letter spacing may look uneven. For exact output pass " +
                    "defaultFontFamily = InterFontFamily (or another embeddable FontFamily) to renderToPdf.",
            )
        }
        fontCache[key] = font
        return font
    }

    /**
     * Loads a font once per document and reuses it. [identityKey] namespaces the physical
     * source (bundled resource, file path, Skia typeface id) so distinct resolution keys that
     * map to the same font don't each embed a duplicate subset; [resolutionKey] is the
     * family/weight/style lookup key, aliased to the same instance on success. Returns null
     * (embedding nothing) when [load] yields null.
     */
    private inline fun loadOrReuse(
        fontCache: MutableMap<String, PDFont>,
        resolutionKey: String,
        identityKey: String,
        load: () -> PDFont?,
    ): PDFont? {
        val font = fontCache[identityKey] ?: load()?.also { fontCache[identityKey] = it } ?: return null
        fontCache[resolutionKey] = font
        return font
    }

    /** Embeds [typeface] via [loadOrReuse], deduped per document by its Skia uniqueId. */
    private fun embedTypeface(
        doc: PDDocument,
        fontCache: MutableMap<String, PDFont>,
        resolutionKey: String,
        typeface: org.jetbrains.skia.Typeface,
    ): PDFont? = loadOrReuse(fontCache, resolutionKey, "typeface:${typeface.uniqueId}") {
        SkiaTypefaceEmbedder.embed(doc, typeface)
    }

    // Families already warned about falling back to a standard-14 font (avoid log spam)
    private val warnedFallbacks = ConcurrentHashMap.newKeySet<String>()

    private fun parseFontFamilyList(family: String?): List<String> {
        if (family.isNullOrEmpty()) return listOf("sans-serif")
        return family.split(",")
            .map { it.trim().trim('\'', '"') }
            .filter { it.isNotEmpty() }
    }

    // Skia's SVG writer quantizes typeface weights down a CSS bucket (Inter-Bold's OS/2
    // weight of 700 is emitted as font-weight="600"), and Compose's own font matching
    // resolves 600+ to the bold face — so treat >= 600 as bold.
    private const val BOLD_THRESHOLD = 600

    private fun isBold(weight: String?): Boolean {
        if (weight == null) return false
        weight.toIntOrNull()?.let { return it >= BOLD_THRESHOLD }
        return weight == "bold" || weight == "bolder"
    }

    private fun isItalic(style: String?): Boolean {
        return style == "italic" || style == "oblique"
    }

    private fun numericWeight(weight: String?): Int {
        weight?.toIntOrNull()?.let { return it }
        return if (isBold(weight)) 700 else 400
    }


    private fun resolveFile(family: String, bold: Boolean, italic: Boolean): File? {
        val cacheKey = "$family-$bold-$italic"
        return filePathCache.getOrPut(cacheKey) {
            Optional.ofNullable(searchFontFile(family, bold, italic))
        }.orElse(null)
    }

    private fun searchFontFile(family: String, bold: Boolean, italic: Boolean): File? {
        val fontDirs = platformFontDirs()
        val rawBaseName = family.replace(" ", "")
        // macOS system fonts use a dot prefix (e.g. ".SF NS", ".New York") that doesn't
        // appear in the actual filename on disk (SFNS.ttf, NewYork.ttf). Try both variants.
        val baseNames = buildList {
            add(rawBaseName)
            if (rawBaseName.startsWith(".")) add(rawBaseName.removePrefix("."))
        }

        val styleSuffixes = buildList {
            if (bold && italic) addAll(listOf("-BoldItalic", "-BoldOblique", "BoldItalic"))
            if (bold && !italic) addAll(listOf("-Bold", "Bold", "-bold"))
            if (!bold && italic) addAll(listOf("-Italic", "-Oblique", "Italic"))
            addAll(listOf("-Regular", "Regular", "-regular", ""))
        }

        // Exact filename match first (fast)
        for (dir in fontDirs) {
            val dirFile = File(dir)
            if (!dirFile.isDirectory) continue
            for (baseName in baseNames) {
                for (suffix in styleSuffixes) {
                    for (ext in FONT_EXTENSIONS) {
                        val file = File(dirFile, "$baseName$suffix$ext")
                        if (file.exists() && file.canRead() && !isVariableFont(file)) return file
                    }
                }
            }
        }

        // Fuzzy search: walk font directories looking for matching filenames
        for (dir in fontDirs) {
            val dirFile = File(dir)
            if (!dirFile.isDirectory) continue
            try {
                val found = dirFile.walk()
                    .maxDepth(3)
                    .filter { it.isFile && it.extension.lowercase() in FONT_EXTENSIONS_BARE }
                    .filter { f -> baseNames.any { f.nameWithoutExtension.contains(it, ignoreCase = true) } }
                    .filter { !isVariableFont(it) }
                    .firstOrNull()
                if (found != null) return found
            } catch (e: Exception) {
                logger.fine("Error searching font directory '$dir': ${e.message}")
            }
        }

        return null
    }

    /**
     * Detects variable fonts by scanning the TrueType/OpenType table directory for
     * the 'fvar' table. PDFBox doesn't support variable font instances, so embedding
     * them causes glyphs to render at the default axis values (wrong weight/width).
     */
    private fun isVariableFont(file: File): Boolean {
        try {
            RandomAccessFile(file, "r").use { raf ->
                if (raf.length() < 12) return false
                raf.seek(4) // skip sfVersion
                val numTables = raf.readUnsignedShort()
                raf.seek(12) // skip searchRange, entrySelector, rangeShift
                for (i in 0 until numTables.coerceAtMost(200)) {
                    val tag = ByteArray(4)
                    if (raf.read(tag) < 4) return false
                    if (String(tag) == "fvar") return true
                    raf.skipBytes(12) // skip checksum, offset, length
                }
            }
        } catch (e: Exception) {
            logger.fine("Error reading font file '${file.path}': ${e.message}")
        }
        return false
    }

    private fun platformFontDirs(): List<String> {
        val os = System.getProperty("os.name", "").lowercase()
        val home = System.getProperty("user.home")
        return buildList {
            when {
                os.contains("mac") -> {
                    add("/Library/Fonts")
                    add("/System/Library/Fonts")
                    add("/System/Library/Fonts/Supplemental")
                    if (home != null) add("$home/Library/Fonts")
                }
                os.contains("linux") -> {
                    add("/usr/share/fonts")
                    add("/usr/local/share/fonts")
                    if (home != null) {
                        add("$home/.fonts")
                        add("$home/.local/share/fonts")
                    }
                }
                os.contains("win") -> {
                    System.getenv("WINDIR")?.let { add("$it\\Fonts") }
                }
            }
        }
    }

    private fun standardFont(family: String?, bold: Boolean, italic: Boolean): PDType1Font {
        val fontName = when {
            family != null && isMonoFamily(family) -> when {
                bold && italic -> Standard14Fonts.FontName.COURIER_BOLD_OBLIQUE
                bold -> Standard14Fonts.FontName.COURIER_BOLD
                italic -> Standard14Fonts.FontName.COURIER_OBLIQUE
                else -> Standard14Fonts.FontName.COURIER
            }
            family != null && isSerifFamily(family) -> when {
                bold && italic -> Standard14Fonts.FontName.TIMES_BOLD_ITALIC
                bold -> Standard14Fonts.FontName.TIMES_BOLD
                italic -> Standard14Fonts.FontName.TIMES_ITALIC
                else -> Standard14Fonts.FontName.TIMES_ROMAN
            }
            else -> when {
                bold && italic -> Standard14Fonts.FontName.HELVETICA_BOLD_OBLIQUE
                bold -> Standard14Fonts.FontName.HELVETICA_BOLD
                italic -> Standard14Fonts.FontName.HELVETICA_OBLIQUE
                else -> Standard14Fonts.FontName.HELVETICA
            }
        }
        return PDType1Font(fontName)
    }

    private fun isMonoFamily(family: String): Boolean {
        val lower = family.lowercase()
        return lower == "monospace" || lower.contains("mono") || lower.contains("courier")
    }

    private fun isSerifFamily(family: String): Boolean {
        val lower = family.lowercase()
        return (lower == "serif") ||
            (lower.contains("serif") && !lower.contains("sans")) ||
            lower.contains("times") || lower.contains("georgia") ||
            lower.contains("new york")
    }

    // --- Bundled font resolution ---

    private val BUNDLED_FONTS = mapOf(
        Triple("Inter", false, false) to "fonts/Inter-Regular.ttf",
        Triple("Inter", true, false) to "fonts/Inter-Bold.ttf",
        Triple("Inter", false, true) to "fonts/Inter-Italic.ttf",
        Triple("Inter", true, true) to "fonts/Inter-BoldItalic.ttf",
    )

    private fun bundledFontPath(family: String, bold: Boolean, italic: Boolean): String? =
        BUNDLED_FONTS[Triple(family, bold, italic)]

    private val GENERIC_FAMILIES = setOf(
        "sans-serif", "serif", "monospace", "cursive", "fantasy", "system-ui",
    )
    private val FONT_EXTENSIONS = listOf(".ttf", ".otf", ".TTF", ".OTF")
    private val FONT_EXTENSIONS_BARE = setOf("ttf", "otf")
}
