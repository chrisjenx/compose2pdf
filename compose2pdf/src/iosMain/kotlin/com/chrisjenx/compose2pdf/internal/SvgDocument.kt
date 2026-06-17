package com.chrisjenx.compose2pdf.internal

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSXMLParser
import platform.Foundation.NSXMLParserDelegateProtocol
import platform.Foundation.create
import platform.darwin.NSObject

/**
 * A simple DOM model for parsed SVG elements.
 *
 * Used on iOS as a lightweight alternative to DOM parsers (which aren't available
 * in Kotlin/Native). The SVG is parsed via [NSXMLParser] into this tree structure.
 */
internal data class SvgElement(
    val name: String,
    val attributes: Map<String, String> = emptyMap(),
    val children: List<SvgElement> = emptyList(),
    val textContent: String = "",
) {
    /** Gets an attribute value by name, checking inline `style` first. */
    fun attr(attrName: String): String? {
        // Check inline style first (CSS properties override presentation attributes)
        val style = attributes["style"]
        if (!style.isNullOrEmpty()) {
            val styleMap = parseInlineStyle(style)
            styleMap[attrName]?.takeIf { it.isNotEmpty() }?.let { return it }
        }
        return attributes[attrName]?.takeIf { it.isNotEmpty() }
    }

    companion object {
        private fun parseInlineStyle(style: String): Map<String, String> {
            return style.split(";").mapNotNull { prop ->
                val colon = prop.indexOf(':')
                if (colon < 0) null
                else prop.substring(0, colon).trim() to prop.substring(colon + 1).trim()
            }.toMap()
        }
    }
}

/**
 * Parses an SVG XML string into an [SvgElement] tree using NSXMLParser.
 *
 * Returns the root SVG element and a map of elements with `id` attributes
 * collected from `<defs>` and `<clipPath>` elements.
 */
internal object SvgParser {

    data class ParseResult(
        val root: SvgElement,
        val defs: Map<String, SvgElement>,
    )

    @OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
    fun parse(svg: String): ParseResult {
        val bytes = svg.encodeToByteArray()
        val nsData = bytes.usePinned { pinned ->
            NSData.create(
                bytes = pinned.addressOf(0),
                length = bytes.size.toULong(),
            )
        }
        val parser = NSXMLParser(nsData)
        val delegate = SvgParserDelegate()
        parser.delegate = delegate
        parser.shouldProcessNamespaces = true

        if (!parser.parse()) {
            val error = parser.parserError
            throw IllegalStateException("Failed to parse SVG XML: ${error?.localizedDescription ?: "unknown error"}")
        }

        val root = delegate.rootElement
            ?: throw IllegalStateException("SVG parsing produced no root element")

        val defs = mutableMapOf<String, SvgElement>()
        collectDefs(root, defs)
        return ParseResult(root, defs)
    }

    private fun collectDefs(parent: SvgElement, defs: MutableMap<String, SvgElement>) {
        for (child in parent.children) {
            when (child.name) {
                "defs" -> {
                    for (defChild in child.children) {
                        val id = defChild.attributes["id"]
                        if (!id.isNullOrEmpty()) defs[id] = defChild
                    }
                }
                // Skia emits <clipPath> as siblings of <g>, not inside <defs>
                "clipPath" -> {
                    val id = child.attributes["id"]
                    if (!id.isNullOrEmpty()) defs[id] = child
                }

                "g" -> collectDefs(child, defs)
            }
        }
    }
}

/**
 * NSXMLParser delegate that builds an [SvgElement] tree.
 *
 * Uses a stack-based approach: each `didStartElement` pushes a new builder
 * onto the stack, and `didEndElement` pops it and adds it as a child of
 * the parent element.
 */
@OptIn(BetaInteropApi::class)
private class SvgParserDelegate : NSObject(), NSXMLParserDelegateProtocol {

    var rootElement: SvgElement? = null
        private set

    private val elementStack = mutableListOf<ElementBuilder>()

    private class ElementBuilder(
        val name: String,
        val attributes: Map<String, String>,
    ) {
        val children = mutableListOf<SvgElement>()
        val textContent = StringBuilder()
    }

    override fun parser(
        parser: NSXMLParser,
        didStartElement: String,
        namespaceURI: String?,
        qualifiedName: String?,
        attributes: Map<Any?, *>,
    ) {
        @Suppress("UNCHECKED_CAST")
        val attrs = (attributes as Map<String, String>)
        elementStack.add(ElementBuilder(didStartElement, attrs))
    }

    override fun parser(
        parser: NSXMLParser,
        didEndElement: String,
        namespaceURI: String?,
        qualifiedName: String?,
    ) {
        if (elementStack.isEmpty()) return
        val builder = elementStack.removeLast()
        val element = SvgElement(
            name = builder.name,
            attributes = builder.attributes,
            children = builder.children.toList(),
            textContent = builder.textContent.toString().trim(),
        )
        if (elementStack.isEmpty()) {
            rootElement = element
        } else {
            elementStack.last().children.add(element)
        }
    }

    override fun parser(parser: NSXMLParser, foundCharacters: String) {
        if (elementStack.isNotEmpty()) {
            elementStack.last().textContent.append(foundCharacters)
        }
    }
}
