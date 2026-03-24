package com.chrisjenx.compose2pdf.examples

import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import org.apache.pdfbox.Loader
import org.apache.pdfbox.rendering.PDFRenderer

class ExampleOutput(
    val name: String,
    val sourceFile: String,
    val pdfBytes: ByteArray,
)

fun main() {
    val projectDir = File(System.getProperty("user.dir"))
    val examplesModule = if (projectDir.name == "examples") projectDir else projectDir.resolve("examples")
    val baseDir = examplesModule.resolve("build/output")
    val sourceRoot = examplesModule.resolve("src/main/kotlin/com/chrisjenx/compose2pdf/examples")
    val pdfDir = baseDir.resolve("pdfs").also { it.mkdirs() }
    val imageDir = baseDir.resolve("images").also { it.mkdirs() }
    val snippetDir = baseDir.resolve("snippets").also { it.mkdirs() }

    val examples = listOf(
        ::helloPdf,
        ::pageConfiguration,
        ::textStyling,
        ::layoutBasics,
        ::shapesAndDrawing,
        ::imagesInPdf,
        ::linksInPdf,
        ::multiPageDocument,
        ::vectorVsRaster,
        ::professionalInvoice,
        ::autoPagination,
    )

    val outputs = mutableListOf<ExampleOutput>()
    for (exampleFn in examples) {
        try {
            outputs += exampleFn()
        } catch (e: Exception) {
            System.err.println("ERROR running ${exampleFn.name}: ${e.message}")
            e.printStackTrace()
        }
    }

    println("\n${"═".repeat(60)}")
    println("  compose2pdf examples — generated ${outputs.size} PDFs")
    println("${"═".repeat(60)}\n")

    val extractedSnippets = mutableSetOf<String>()
    for (output in outputs) {
        val pdfFile = pdfDir.resolve("${output.name}.pdf")
        pdfFile.writeBytes(output.pdfBytes)

        val pngFiles = rasterizeToPng(output.pdfBytes, output.name, imageDir)
        extractSnippet(output.sourceFile, sourceRoot, snippetDir, extractedSnippets)

        val sizeKb = output.pdfBytes.size / 1024.0
        println("  %-30s  %6.1f KB  %d page(s)".format(pdfFile.name, sizeKb, pngFiles.size))
    }

    println("\n  PDFs:     ${pdfDir.absolutePath}")
    println("  Images:   ${imageDir.absolutePath}")
    println("  Snippets: ${snippetDir.absolutePath}")
    println()
}

private fun rasterizeToPng(pdfBytes: ByteArray, baseName: String, imageDir: File): List<File> {
    val files = mutableListOf<File>()
    Loader.loadPDF(pdfBytes).use { doc ->
        val renderer = PDFRenderer(doc)
        renderer.setRenderingHints(
            RenderingHints(
                mapOf(
                    RenderingHints.KEY_ANTIALIASING to RenderingHints.VALUE_ANTIALIAS_ON,
                    RenderingHints.KEY_TEXT_ANTIALIASING to RenderingHints.VALUE_TEXT_ANTIALIAS_ON,
                    RenderingHints.KEY_FRACTIONALMETRICS to RenderingHints.VALUE_FRACTIONALMETRICS_ON,
                    RenderingHints.KEY_STROKE_CONTROL to RenderingHints.VALUE_STROKE_PURE,
                    RenderingHints.KEY_RENDERING to RenderingHints.VALUE_RENDER_QUALITY,
                    RenderingHints.KEY_INTERPOLATION to RenderingHints.VALUE_INTERPOLATION_BICUBIC,
                )
            )
        )
        val pageCount = doc.numberOfPages
        for (i in 0 until pageCount) {
            val image: BufferedImage = renderer.renderImageWithDPI(i, 150f)
            val suffix = if (pageCount > 1) "-${i + 1}" else ""
            val pngFile = imageDir.resolve("$baseName$suffix.png")
            ImageIO.write(image, "png", pngFile)
            files += pngFile
        }
    }
    return files
}

private val snippetRegex = Regex(
    """// --- snippet start ---\r?\n(.*?)// --- snippet end ---""",
    RegexOption.DOT_MATCHES_ALL,
)

private fun extractSnippet(
    sourceFileName: String,
    sourceRoot: File,
    snippetDir: File,
    seen: MutableSet<String>,
) {
    if (!seen.add(sourceFileName)) return
    val sourceFile = sourceRoot.resolve(sourceFileName)
    if (!sourceFile.exists()) return
    val source = sourceFile.readText()
    val match = snippetRegex.find(source) ?: return
    val snippet = match.groupValues[1].trimIndent().trimEnd()
    val outputName = sourceFileName.replace(Regex("^\\d+_"), "")
    snippetDir.resolve(outputName).writeText(snippet + "\n")
}
