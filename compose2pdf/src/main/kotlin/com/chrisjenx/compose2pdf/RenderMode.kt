package com.chrisjenx.compose2pdf

/**
 * Controls how Compose content is rendered to PDF.
 */
enum class RenderMode {
    /**
     * Vector rendering via Skia SVGCanvas.
     * Produces smaller PDFs with selectable text and crisp scaling.
     */
    VECTOR,

    /**
     * Raster rendering via ImageComposeScene.
     * Produces pixel-perfect output as an embedded image.
     */
    RASTER,
}
