plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose)
    alias(libs.plugins.compose.compiler)
    `maven-publish`
    signing
}

dependencies {
    implementation(compose.desktop.common)
    implementation(libs.pdfbox)

    testImplementation(compose.desktop.currentOs)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
}

kotlin {
    jvmToolchain(17)
    compilerOptions {
        freeCompilerArgs.add("-opt-in=androidx.compose.ui.InternalComposeUiApi")
    }
}

// --- Publishing ---

java {
    withJavadocJar()
    withSourcesJar()
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            artifactId = "compose2pdf"
            pom {
                name.set("compose2pdf")
                description.set("Render Compose Desktop content to PDF")
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
    }
}

signing {
    val signingKeyId: String? by project
    val signingKey: String? by project
    val signingPassword: String? by project
    if (signingKey != null) {
        useInMemoryPgpKeys(signingKeyId, signingKey, signingPassword)
    }
    isRequired = signingKey != null || findProperty("signing.keyId") != null
    sign(publishing.publications["maven"])
}
