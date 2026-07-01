plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose)
    alias(libs.plugins.compose.compiler)
}

dependencies {
    testImplementation(project(":compose2pdf"))
    testImplementation(compose.desktop.currentOs)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.pdfbox)
}

// Mirrors the compose2pdf module: the fidelity SVG renderer (TestHelpers.renderComposeToSvg) drives
// the same @InternalComposeUiApi CanvasLayersComposeScene that changed shape in CMP 1.12, so its
// scene driver ships as two source variants added to the TEST source set by the resolved Compose
// version (cmpLegacy -> CMP <= 1.11, cmpNext -> CMP >= 1.12). See compose2pdf/build.gradle.kts.
val composeVersion: String = libs.versions.compose.multiplatform.get()
val composeSceneVariant: String = run {
    val parts = composeVersion.substringBefore('-').split('.')
    val major = parts.getOrNull(0)?.toIntOrNull()
    val minor = parts.getOrNull(1)?.toIntOrNull()
    if (major == null || minor == null) {
        error("fidelity-test: cannot parse Compose Multiplatform version '$composeVersion' to select the scene-driver variant")
    }
    if (major > 1 || (major == 1 && minor >= 12)) "cmpNext" else "cmpLegacy"
}

kotlin {
    jvmToolchain(17)
    compilerOptions {
        freeCompilerArgs.add("-opt-in=androidx.compose.ui.InternalComposeUiApi")
    }
    sourceSets.named("test") {
        kotlin.srcDir("src/$composeSceneVariant/kotlin")
    }
}
