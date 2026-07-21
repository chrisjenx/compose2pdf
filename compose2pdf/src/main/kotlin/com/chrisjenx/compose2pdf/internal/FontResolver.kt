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
        val bold = isBold(weight)
        val italic = isItalic(style)
        val weightValue = numericWeight(weight)
        val key = "${families.firstOrNull() ?: "sans-serif"}-$weightValue-$italic"

        fontCache[key]?.let { return it }

        // Try bundled fonts first (guaranteed to match Compose rendering)
        for (fam in families) {
            val bundledStream = resolveBundledFont(fam, bold, italic)
            if (bundledStream != null) {
                try {
                    val font = bundledStream.use { PDType0Font.load(doc, it) }
                    fontCache[key] = font
                    return font
                } catch (e: Exception) {
                    logger.warning("Failed to load bundled font '$fam' (bold=$bold, italic=$italic): ${e.message}")
                }
            }
        }

        // Typefaces Compose loaded while laying the text out — the exact fonts behind the
        // SVG's glyph positions (covers resource/file fonts and platform defaults that
        // never exist as resolvable files on disk).
        ComposeFontStack.harvest()
        for (fam in families) {
            val typeface = TypefaceCaptureRegistry.lookup(fam, weightValue, italic) ?: continue
            val font = SkiaTypefaceEmbedder.embed(doc, typeface) ?: continue
            fontCache[key] = font
            return font
        }

        // The composition's Skia font collection — the same lookup the paragraph shaper
        // used for system fonts and glyph-fallback runs.
        for (fam in families) {
            val typeface = ComposeFontStack.findTypeface(fam, weightValue, italic, generic = fam in GENERIC_FAMILIES)
                ?: continue
            val font = SkiaTypefaceEmbedder.embed(doc, typeface) ?: continue
            fontCache[key] = font
            return font
        }

        // Skia's default font manager — same matcher, for callers that never went through
        // a Compose composition (e.g. direct SVG conversion).
        for (fam in families) {
            val typeface = matchSystemTypeface(fam, weightValue, italic) ?: continue
            val font = SkiaTypefaceEmbedder.embed(doc, typeface) ?: continue
            fontCache[key] = font
            return font
        }

        // Try each family in the fallback list (system fonts)
        for (fam in families) {
            if (fam in GENERIC_FAMILIES) continue
            val fontFile = resolveFile(fam, bold, italic)
            if (fontFile != null) {
                try {
                    val font = PDType0Font.load(doc, fontFile)
                    fontCache[key] = font
                    return font
                } catch (e: Exception) {
                    logger.fine("Failed to load system font '$fam' from ${fontFile.path}: ${e.message}")
                }
            }
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

    // Families already warned about falling back to a standard-14 font (avoid log spam)
    private val warnedFallbacks = ConcurrentHashMap.newKeySet<String>()

    private fun parseFontFamilyList(family: String?): List<String> {
        if (family.isNullOrEmpty()) return listOf("sans-serif")
        return family.split(",")
            .map { it.trim().trim('\'', '"') }
            .filter { it.isNotEmpty() }
    }

    private fun isBold(weight: String?): Boolean {
        if (weight == null) return false
        weight.toIntOrNull()?.let { return it >= 700 }
        return weight == "bold" || weight == "bolder"
    }

    private fun isItalic(style: String?): Boolean {
        return style == "italic" || style == "oblique"
    }

    private fun numericWeight(weight: String?): Int {
        weight?.toIntOrNull()?.let { return it }
        return if (isBold(weight)) 700 else 400
    }

    /**
     * Resolves [family] through Skia's font manager — the same lookup Skia's text shaper
     * uses — and verifies the returned typeface actually carries the requested family name
     * (fontconfig on Linux substitutes a default for unknown families, which would embed
     * an arbitrary font). Generic families are exempt: whatever the font manager maps them
     * to IS the font Skia shaped with.
     */
    private fun matchSystemTypeface(family: String, weight: Int, italic: Boolean): org.jetbrains.skia.Typeface? {
        return try {
            val slant = if (italic) org.jetbrains.skia.FontSlant.ITALIC else org.jetbrains.skia.FontSlant.UPRIGHT
            val style = org.jetbrains.skia.FontStyle(weight, /* width = */ 5, slant)
            val typeface = org.jetbrains.skia.FontMgr.default.matchFamilyStyle(family, style) ?: return null
            val requested = family.trim().lowercase()
            val isGeneric = requested in GENERIC_FAMILIES
            val nameMatches = typeface.familyName?.trim()?.lowercase() == requested ||
                typeface.familyNames.any { it.name.trim().lowercase() == requested }
            if (isGeneric || nameMatches) typeface else null
        } catch (t: Throwable) {
            logger.fine("Skia font manager lookup failed for '$family': ${t.message}")
            null
        }
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

    private fun resolveBundledFont(family: String, bold: Boolean, italic: Boolean): java.io.InputStream? {
        val resourcePath = BUNDLED_FONTS[Triple(family, bold, italic)] ?: return null
        return Thread.currentThread().contextClassLoader?.getResourceAsStream(resourcePath)
    }

    private val GENERIC_FAMILIES = setOf(
        "sans-serif", "serif", "monospace", "cursive", "fantasy", "system-ui",
    )
    private val FONT_EXTENSIONS = listOf(".ttf", ".otf", ".TTF", ".OTF")
    private val FONT_EXTENSIONS_BARE = setOf("ttf", "otf")
}
