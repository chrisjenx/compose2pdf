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

// The internal Compose scene API (androidx.compose.ui.scene) has no binary-compatibility guarantee
// and reshaped in 1.12. compose2pdf ships ONE binary, so ComposeSceneRenderer resolves the scene
// API by reflection at runtime (see its KDoc) — nothing version-specific in the build.
val composeVersion: String = libs.versions.compose.multiplatform.get()
logger.lifecycle("compose2pdf: reflective ComposeSceneRenderer, base Compose $composeVersion")

kotlin {
    jvmToolchain(17)
    compilerOptions {
        freeCompilerArgs.add("-opt-in=androidx.compose.ui.InternalComposeUiApi")
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
