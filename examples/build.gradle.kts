plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose)
    alias(libs.plugins.compose.compiler)
    application
}

application {
    mainClass.set("com.chrisjenx.compose2pdf.examples.MainKt")
}

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
