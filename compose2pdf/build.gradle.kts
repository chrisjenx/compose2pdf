plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.maven.publish)
}

dependencies {
    implementation(compose.desktop.common)
    implementation(libs.pdfbox)

    testImplementation(compose.desktop.currentOs)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
}

// Compose Multiplatform reworked the @InternalComposeUiApi `CanvasLayersComposeScene` API in 1.12,
// so the version-specific scene driver (ComposeSceneRenderer) ships as two source variants and the
// matching one is added to the main source set based on the resolved Compose version:
//   cmpLegacy -> CMP <= 1.11 (coroutineContext/invalidate + render)
//   cmpNext   -> CMP >= 1.12 (frameRecomposer + measureAndLayout/draw)
val composeSceneVariant: String = run {
    val parts = libs.versions.compose.multiplatform.get().substringBefore('-').split('.')
    val major = parts.getOrNull(0)?.toIntOrNull() ?: 0
    val minor = parts.getOrNull(1)?.toIntOrNull() ?: 0
    if (major > 1 || (major == 1 && minor >= 12)) "cmpNext" else "cmpLegacy"
}
logger.info(
    "compose2pdf: using '$composeSceneVariant' ComposeSceneRenderer for Compose " +
        libs.versions.compose.multiplatform.get()
)

kotlin {
    jvmToolchain(17)
    compilerOptions {
        freeCompilerArgs.add("-opt-in=androidx.compose.ui.InternalComposeUiApi")
    }
    sourceSets.named("main") {
        kotlin.srcDir("src/$composeSceneVariant/kotlin")
    }
}

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()
    coordinates("com.chrisjenx", "compose2pdf", version.toString())
    pom {
        name.set("compose2pdf")
        description.set(
            "Kotlin JVM library for rendering Compose Desktop content to production-quality PDFs " +
                "with vector text, embedded fonts, auto-pagination, and server-side streaming support."
        )
        url.set("https://github.com/chrisjenx/compose2pdf")
        licenses {
            license {
                name.set("Apache-2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0")
            }
        }
        developers {
            developer {
                id.set("chrisjenx")
                name.set("Christopher Jenkins")
            }
        }
        scm {
            url.set("https://github.com/chrisjenx/compose2pdf")
            connection.set("scm:git:git://github.com/chrisjenx/compose2pdf.git")
        }
    }
}
