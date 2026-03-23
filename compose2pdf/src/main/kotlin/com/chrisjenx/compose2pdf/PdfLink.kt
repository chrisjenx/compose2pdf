package com.chrisjenx.compose2pdf

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.toSize

/**
 * A link annotation that will be added to the PDF output.
 *
 * @param href The target URL for the link.
 * @param x Left edge in PDF points.
 * @param y Top edge in PDF points (SVG/Compose coordinate space, Y-down).
 * @param width Width in PDF points.
 * @param height Height in PDF points.
 */
data class PdfLinkAnnotation(
    val href: String,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
)

/**
 * Collector for link annotations during PDF rendering.
 * Set via [LocalPdfLinkCollector] during renderToPdf calls.
 */
class PdfLinkCollector {
    private val _links = mutableListOf<PdfLinkAnnotation>()
    val links: List<PdfLinkAnnotation> get() = _links

    fun add(annotation: PdfLinkAnnotation) {
        _links.add(annotation)
    }

    fun clear() {
        _links.clear()
    }
}

/**
 * CompositionLocal providing access to the link collector during PDF rendering.
 * When rendering to screen (not PDF), this is null and link annotations are ignored.
 */
val LocalPdfLinkCollector = compositionLocalOf<PdfLinkCollector?> { null }

/**
 * Wraps content in a clickable link annotation for PDF output.
 *
 * When rendered via [renderToPdf], the bounds of [content] are recorded and a
 * clickable URL annotation is added to the PDF at that location.
 *
 * When rendered to screen (outside of renderToPdf), this is a no-op wrapper.
 *
 * @param href The target URL for the link.
 * @param modifier Modifier applied to the link wrapper.
 * @param content The composable content that forms the clickable region.
 */
@Composable
fun PdfLink(
    href: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val collector = LocalPdfLinkCollector.current
    val density = LocalDensity.current

    Box(
        modifier = modifier.then(
            if (collector != null) {
                Modifier.onGloballyPositioned { coords ->
                    val pos = coords.positionInRoot()
                    val size = coords.size.toSize()
                    collector.add(
                        PdfLinkAnnotation(
                            href = href,
                            x = pos.x / density.density,
                            y = pos.y / density.density,
                            width = size.width / density.density,
                            height = size.height / density.density,
                        )
                    )
                }
            } else {
                Modifier
            }
        ),
    ) {
        content()
    }
}
