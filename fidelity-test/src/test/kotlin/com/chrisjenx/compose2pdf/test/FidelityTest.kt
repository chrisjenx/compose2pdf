@file:OptIn(InternalComposeUiApi::class)

package com.chrisjenx.compose2pdf.test

import androidx.compose.material.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import com.chrisjenx.compose2pdf.InterFontFamily
import com.chrisjenx.compose2pdf.PdfPageConfig
import com.chrisjenx.compose2pdf.RenderMode
import com.chrisjenx.compose2pdf.renderToPdf
import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse

class FidelityTest {

    private val config = PdfPageConfig.A4
    private val density = Density(2f)
    private val renderDpi = 144f // 2x scaling matches density=2

    private val reportDir = File("build/reports/fidelity")
    private val imagesDir = File(reportDir, "images")

    // Android PDFs from GMD test output (run :compose2pdf:pixel2api30atdDebugAndroidTest first)
    private val androidPdfDir = findAndroidPdfDir()

    @Test
    fun `fidelity comparison of all fixtures`() {
        imagesDir.mkdirs()

        val results = fidelityFixtures.map { fixture ->
            runFixture(fixture)
        }

        // Generate unified HTML report
        val reportFile = File(reportDir, "index.html")
        generateFidelityReport(results, reportFile)
        println("Fidelity report: ${reportFile.absolutePath}")

        // Print summary table
        println()
        println(
            "${"Fixture".padEnd(25)} " +
                "${"Vector".padEnd(50)} " +
                "Raster",
        )
        println("-".repeat(130))
        for (result in results) {
            val vStatus = result.vectorStatus.label
            val rStatus = result.rasterStatus.label
            println(
                "${result.name.padEnd(25)} " +
                    "$vStatus RMSE=${"%.4f".format(result.vectorRmse)} SSIM=${"%.4f".format(result.vectorSsim)} Match=${"%.1f".format(result.vectorExactMatch * 100)}%".padEnd(50) + " " +
                    "$rStatus RMSE=${"%.4f".format(result.rasterRmse)} SSIM=${"%.4f".format(result.rasterSsim)} Match=${"%.1f".format(result.rasterExactMatch * 100)}%",
            )
        }
        println()

        // Collect PDF failures (hard fail)
        val failures = mutableListOf<String>()
        for (result in results) {
            val fixture = fidelityFixtures.first { it.name == result.name }
            if (result.vectorRmse > fixture.vectorThreshold) {
                failures.add("${result.name}: vector RMSE ${"%.4f".format(result.vectorRmse)} > threshold ${fixture.vectorThreshold}")
            }
            if (result.rasterExactMatch < 0.999) {
                failures.add("${result.name}: raster match ${"%.4f".format(result.rasterExactMatch)} < 0.999")
            }
        }

        assertFalse(
            failures.isNotEmpty(),
            "Fidelity failures:\n${failures.joinToString("\n") { "  - $it" }}",
        )
    }

    private fun runFixture(fixture: Fixture): FidelityResult {
        val fixtureConfig = fixture.config
        val pageW = (fixtureConfig.width.value * density.density).toInt()
        val pageH = (fixtureConfig.height.value * density.density).toInt()
        val contentW = (fixtureConfig.contentWidth.value * density.density).toInt()
        val contentH = (fixtureConfig.contentHeight.value * density.density).toInt()

        val wrappedContent: @Composable () -> Unit = {
            ProvideTextStyle(TextStyle(fontFamily = InterFontFamily)) {
                fixture.content()
            }
        }

        // 1. Reference render: Compose content at content dimensions, then composite onto full page
        val contentImage = renderComposeReference(contentW, contentH, density, wrappedContent)
        val composeImage = compositeOnPage(contentImage, pageW, pageH, fixtureConfig, density)
        val flatCompose = ImageMetrics.flattenOnWhite(composeImage)
        saveImage(flatCompose, imagesDir, "${fixture.name}-compose.png")

        // 2. Save raw SVG for diagnostic inspection
        val svg = renderComposeToSvg(contentW, contentH, density, wrappedContent)
        File(imagesDir, "${fixture.name}-vector.svg").writeText(svg)

        // 3. Vector PDF render
        val vectorPdfBytes = renderToPdf(config = fixtureConfig, density = density, mode = RenderMode.VECTOR) {
            fixture.content()
        }
        File(imagesDir, "${fixture.name}-vector.pdf").writeBytes(vectorPdfBytes)
        val vectorImage = rasterizePdf(vectorPdfBytes, renderDpi)
        saveImage(vectorImage, imagesDir, "${fixture.name}-vector.png")

        // 4. Raster PDF render
        val rasterPdfBytes = renderToPdf(config = fixtureConfig, density = density, mode = RenderMode.RASTER) {
            fixture.content()
        }
        File(imagesDir, "${fixture.name}-raster.pdf").writeBytes(rasterPdfBytes)
        val rasterImage = rasterizePdf(rasterPdfBytes, renderDpi)
        saveImage(rasterImage, imagesDir, "${fixture.name}-raster.png")

        // 5. Vector metrics + diff
        val vectorRmse = ImageMetrics.computeRmse(composeImage, vectorImage)
        val vectorSsim = ImageMetrics.computeSsim(composeImage, vectorImage)
        val vectorExactMatch = ImageMetrics.computeExactMatchPercent(composeImage, vectorImage)
        val vectorMaxError = ImageMetrics.computeMaxPixelError(composeImage, vectorImage)
        val vectorDiff = ImageMetrics.generateStructuralDiffImage(composeImage, vectorImage)
        saveImage(vectorDiff, imagesDir, "${fixture.name}-vector-diff.png")

        // 6. Raster metrics + diff
        val rasterRmse = ImageMetrics.computeRmse(composeImage, rasterImage)
        val rasterSsim = ImageMetrics.computeSsim(composeImage, rasterImage)
        val rasterExactMatch = ImageMetrics.computeExactMatchPercent(composeImage, rasterImage)
        val rasterMaxError = ImageMetrics.computeMaxPixelError(composeImage, rasterImage)
        val rasterDiff = ImageMetrics.generateStructuralDiffImage(composeImage, rasterImage)
        saveImage(rasterDiff, imagesDir, "${fixture.name}-raster-diff.png")

        // 7. Android cross-platform comparison (optional — requires prior GMD run)
        val androidPdf = androidPdfDir?.let { File(it, "${fixture.name}-android.pdf") }
        val androidResult = if (androidPdf != null && androidPdf.exists()) {
            try {
                val androidPdfBytes = androidPdf.readBytes()
                androidPdf.copyTo(File(imagesDir, "${fixture.name}-android.pdf"), overwrite = true)
                val androidImage = rasterizePdf(androidPdfBytes, renderDpi)
                saveImage(androidImage, imagesDir, "${fixture.name}-android.png")
                val aRmse = ImageMetrics.computeRmse(composeImage, androidImage)
                val aSsim = ImageMetrics.computeSsim(composeImage, androidImage)
                val aExactMatch = ImageMetrics.computeExactMatchPercent(composeImage, androidImage)
                val aMaxError = ImageMetrics.computeMaxPixelError(composeImage, androidImage)
                val aDiff = ImageMetrics.generateStructuralDiffImage(composeImage, androidImage)
                saveImage(aDiff, imagesDir, "${fixture.name}-android-diff.png")
                AndroidMetrics(aRmse, aSsim, aExactMatch, aMaxError)
            } catch (e: Exception) {
                println("  Warning: failed to process Android PDF for ${fixture.name}: ${e.message}")
                null
            }
        } else null

        return FidelityResult(
            name = fixture.name,
            category = fixture.category,
            description = fixture.description,
            vectorRmse = vectorRmse,
            vectorSsim = vectorSsim,
            vectorExactMatch = vectorExactMatch,
            vectorMaxError = vectorMaxError,
            vectorStatus = vectorStatus(vectorRmse, fixture.vectorThreshold),
            rasterRmse = rasterRmse,
            rasterSsim = rasterSsim,
            rasterExactMatch = rasterExactMatch,
            rasterMaxError = rasterMaxError,
            rasterStatus = rasterStatus(rasterExactMatch),
            composePath = "images/${fixture.name}-compose.png",
            vectorPath = "images/${fixture.name}-vector.png",
            rasterPath = "images/${fixture.name}-raster.png",
            vectorDiffPath = "images/${fixture.name}-vector-diff.png",
            rasterDiffPath = "images/${fixture.name}-raster-diff.png",
            vectorPdfPath = "images/${fixture.name}-vector.pdf",
            rasterPdfPath = "images/${fixture.name}-raster.pdf",
            androidPath = if (androidResult != null) "images/${fixture.name}-android.png" else "",
            androidDiffPath = if (androidResult != null) "images/${fixture.name}-android-diff.png" else "",
            androidPdfPath = if (androidResult != null) "images/${fixture.name}-android.pdf" else "",
            androidRmse = androidResult?.rmse ?: -1.0,
            androidSsim = androidResult?.ssim ?: -1.0,
            androidExactMatch = androidResult?.exactMatch ?: -1.0,
            androidMaxError = androidResult?.maxError ?: -1.0,
            androidStatus = if (androidResult != null) vectorStatus(androidResult.rmse, fixture.vectorThreshold) else Status.SKIPPED,
        )
    }

    private data class AndroidMetrics(
        val rmse: Double,
        val ssim: Double,
        val exactMatch: Double,
        val maxError: Double,
    )

    companion object {
        /** Searches for Android PDF output from GMD tests. */
        private fun findAndroidPdfDir(): File? {
            // Standard GMD output path (relative to fidelity-test working dir)
            val candidates = listOf(
                File("../compose2pdf/build/outputs/managed_device_android_test_additional_output/debug/pixel2api30atd"),
                File("../compose2pdf/build/outputs/managed_device_android_test_additional_output/debug"),
            )
            for (candidate in candidates) {
                if (candidate.isDirectory && candidate.listFiles()?.any { it.name.endsWith("-android.pdf") } == true) {
                    println("Found Android PDFs at: ${candidate.absolutePath}")
                    return candidate
                }
            }
            // Search recursively under the Android output dir
            val baseDir = File("../compose2pdf/build/outputs/managed_device_android_test_additional_output")
            if (baseDir.isDirectory) {
                baseDir.walk().maxDepth(3).forEach { dir ->
                    if (dir.isDirectory && dir.listFiles()?.any { it.name.endsWith("-android.pdf") } == true) {
                        println("Found Android PDFs at: ${dir.absolutePath}")
                        return dir
                    }
                }
            }
            println("No Android PDFs found — run :compose2pdf:pixel2api30atdDebugAndroidTest first for cross-platform comparison")
            return null
        }
    }
}
