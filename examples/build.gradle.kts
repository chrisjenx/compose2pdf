plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose)
    alias(libs.plugins.compose.compiler)
    application
}

application {
    mainClass.set("com.chrisjenx.compose2pdf.examples.MainKt")
}

// currentOs is deprecated in CMP 1.13 but kept: the pinned base can be a stable <= 1.12 (the weekly
// update workflow pins the highest STABLE), where the explicit modules ship no Skiko natives.
@Suppress("DEPRECATION")
dependencies {
    implementation(project(":compose2pdf"))
    implementation(compose.desktop.currentOs)
    implementation(libs.pdfbox)
}

kotlin {
    jvmToolchain(17)
    compilerOptions {
        freeCompilerArgs.add("-opt-in=androidx.compose.ui.InternalComposeUiApi")
    }
}

// CMP 1.13 ships two DIFFERENT jars with the same file name
// (org.jetbrains.compose.runtime:runtime-saveable-desktop — a small shim — and
// androidx.compose.runtime:runtime-saveable-desktop), which collide in the application plugin's
// flat lib/ folder. Examples are only ever launched via `:examples:run` (never distributed), so
// skip the archives rather than silently dropping one of the jars with DuplicatesStrategy.EXCLUDE.
tasks.named("distTar") { enabled = false }
tasks.named("distZip") { enabled = false }
