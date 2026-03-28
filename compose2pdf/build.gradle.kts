plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.compose)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.maven.publish)
}

kotlin {
    jvmToolchain(17)

    jvm()

    androidTarget {
        publishLibraryVariants("release")
        @Suppress("OPT_IN_USAGE")
        instrumentedTestVariant.sourceSetTree.set(org.jetbrains.kotlin.gradle.plugin.KotlinSourceSetTree.test)
    }

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { target ->
        target.binaries.framework {
            baseName = "compose2pdf"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.ui)
        }

        jvmMain.dependencies {
            implementation(compose.desktop.common)
            implementation(libs.pdfbox)
        }

        jvmTest.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }

        androidMain.dependencies {
            // Uses android.graphics.pdf.PdfDocument (platform API, no external dependency)
        }

        val androidInstrumentedTest by getting {
            dependencies {
                implementation(project(":test-fixtures"))
                implementation(libs.kotlin.test)
                implementation(libs.kotlinx.coroutines.test)
                implementation("androidx.test:runner:1.6.2")
                implementation("androidx.test:rules:1.6.1")
                implementation("androidx.test.ext:junit:1.2.1")
                implementation("androidx.test.services:test-services:1.5.0")
                implementation(compose.material3)
            }
        }

        iosMain.dependencies {
        }

        val iosSimulatorArm64Test by getting {
            dependencies {
                implementation(project(":test-fixtures"))
                implementation(libs.kotlin.test)
                implementation(compose.material3)
            }
        }
    }

    compilerOptions {
        freeCompilerArgs.add("-opt-in=androidx.compose.ui.InternalComposeUiApi")
    }
}

android {
    namespace = "com.chrisjenx.compose2pdf"
    compileSdk = 35
    defaultConfig {
        minSdk = 24
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        testInstrumentationRunnerArguments["useTestStorageService"] = "true"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    @Suppress("UnstableApiUsage")
    testOptions {
        managedDevices {
            localDevices {
                create("pixel2api30atd") {
                    device = "Pixel 2"
                    apiLevel = 30
                    systemImageSource = "aosp-atd"
                }
            }
        }
    }
}

dependencies {
    androidTestUtil("androidx.test.services:test-services:1.5.0")
}

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()
    coordinates("com.chrisjenx", "compose2pdf", version.toString())
    pom {
        name.set("compose2pdf")
        description.set(
            "Kotlin Multiplatform library for rendering Compose content to production-quality PDFs " +
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
