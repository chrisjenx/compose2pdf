package com.chrisjenx.compose2pdf.internal

import org.jetbrains.skia.OutputWStream
import org.jetbrains.skia.Picture
import org.jetbrains.skia.Rect
import org.jetbrains.skia.svg.SVGCanvas
import java.io.ByteArrayOutputStream

internal actual fun renderPictureToSvgBytes(
    picture: Picture,
    width: Float,
    height: Float,
): ByteArray {
    val baos = ByteArrayOutputStream()
    val wstream = OutputWStream(baos)
    val svgCanvas = SVGCanvas.make(
        Rect.makeWH(width, height),
        wstream,
        convertTextToPaths = false,
        prettyXML = false,
    )
    picture.playback(svgCanvas)
    svgCanvas.close()
    wstream.close()
    return baos.toByteArray()
}
