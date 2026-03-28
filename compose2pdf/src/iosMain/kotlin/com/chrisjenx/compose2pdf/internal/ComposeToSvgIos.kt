package com.chrisjenx.compose2pdf.internal

import org.jetbrains.skia.OutputWStream
import org.jetbrains.skia.Picture
import org.jetbrains.skia.Rect
import org.jetbrains.skia.svg.SVGCanvas

internal actual fun renderPictureToSvgBytes(
    picture: Picture,
    width: Float,
    height: Float,
): ByteArray {
    val wstream = OutputWStream()
    val svgCanvas = SVGCanvas.make(
        Rect.makeWH(width, height),
        wstream,
        convertTextToPaths = false,
        prettyXML = false,
    )
    picture.playback(svgCanvas)
    svgCanvas.close()
    val data = wstream.toData()
    wstream.close()
    return data.bytes
}
