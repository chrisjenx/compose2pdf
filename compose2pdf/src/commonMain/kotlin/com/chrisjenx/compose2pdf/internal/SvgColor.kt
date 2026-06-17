package com.chrisjenx.compose2pdf.internal

/**
 * An RGB color parsed from SVG/CSS color values.
 * Components are in the range 0.0–1.0.
 */
internal data class SvgColor(val r: Float, val g: Float, val b: Float)

/**
 * Parses CSS/SVG color strings into [SvgColor].
 *
 * Supports hex (#RGB, #RRGGBB, #RGBA, #RRGGBBAA), rgb()/rgba() functions
 * (comma-separated, space-separated, and percentage values), and common named colors.
 */
internal object SvgColorParser {

    fun parse(color: String): SvgColor? {
        val c = color.trim().lowercase()
        return when {
            c.startsWith("#") -> parseHex(c)
            c.startsWith("rgba(") || c.startsWith("rgb(") -> parseRgbFunc(c)
            else -> NAMED_COLORS[c]
        }
    }

    private fun parseHex(c: String): SvgColor? {
        fun h(start: Int, end: Int) = c.substring(start, end).toIntOrNull(16)?.div(255f)
        fun hh(pos: Int) = c.substring(pos, pos + 1).repeat(2).toIntOrNull(16)?.div(255f)
        return when (c.length) {
            9 -> { // #RRGGBBAA (alpha ignored)
                val r = h(1, 3) ?: return null; val g = h(3, 5) ?: return null; val b = h(5, 7) ?: return null
                SvgColor(r, g, b)
            }
            7 -> { // #RRGGBB
                val r = h(1, 3) ?: return null; val g = h(3, 5) ?: return null; val b = h(5, 7) ?: return null
                SvgColor(r, g, b)
            }
            5 -> { // #RGBA (alpha ignored)
                val r = hh(1) ?: return null; val g = hh(2) ?: return null; val b = hh(3) ?: return null
                SvgColor(r, g, b)
            }
            4 -> { // #RGB
                val r = hh(1) ?: return null; val g = hh(2) ?: return null; val b = hh(3) ?: return null
                SvgColor(r, g, b)
            }
            else -> null
        }
    }

    /** Parses rgb()/rgba() with comma-separated, space-separated, or percentage values. */
    private fun parseRgbFunc(c: String): SvgColor? {
        val inner = c.substringAfter("(").substringBefore(")")
        val parts = inner.replace("/", " ").split(Regex("[,\\s]+"))
            .map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.size < 3) return null
        fun comp(s: String): Float? = when {
            s.endsWith("%") -> s.removeSuffix("%").toFloatOrNull()?.div(100f)
            "." in s -> s.toFloatOrNull() // Float 0.0–1.0
            else -> s.toFloatOrNull()?.div(255f) // Integer 0–255
        }
        val r = comp(parts[0]) ?: return null
        val g = comp(parts[1]) ?: return null
        val b = comp(parts[2]) ?: return null
        return SvgColor(r, g, b)
    }

    private val NAMED_COLORS = mapOf(
        "black" to SvgColor(0f, 0f, 0f),
        "white" to SvgColor(1f, 1f, 1f),
        "red" to SvgColor(1f, 0f, 0f),
        "green" to SvgColor(0f, 0.502f, 0f),
        "blue" to SvgColor(0f, 0f, 1f),
        "yellow" to SvgColor(1f, 1f, 0f),
        "cyan" to SvgColor(0f, 1f, 1f),
        "aqua" to SvgColor(0f, 1f, 1f),
        "magenta" to SvgColor(1f, 0f, 1f),
        "fuchsia" to SvgColor(1f, 0f, 1f),
        "gray" to SvgColor(0.502f, 0.502f, 0.502f),
        "grey" to SvgColor(0.502f, 0.502f, 0.502f),
        "silver" to SvgColor(0.753f, 0.753f, 0.753f),
        "maroon" to SvgColor(0.502f, 0f, 0f),
        "olive" to SvgColor(0.502f, 0.502f, 0f),
        "lime" to SvgColor(0f, 1f, 0f),
        "teal" to SvgColor(0f, 0.502f, 0.502f),
        "navy" to SvgColor(0f, 0f, 0.502f),
        "orange" to SvgColor(1f, 0.647f, 0f),
        "purple" to SvgColor(0.502f, 0f, 0.502f),
        "pink" to SvgColor(1f, 0.753f, 0.796f),
        "brown" to SvgColor(0.647f, 0.165f, 0.165f),
    )
}
