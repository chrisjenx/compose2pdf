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
        )
    }
}
