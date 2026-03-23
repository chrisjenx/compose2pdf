plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose)
    alias(libs.plugins.compose.compiler)
    `maven-publish`
    signing
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(libs.pdfbox)

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
                url.set("https://github.com/nickhall-ck/compose2pdf")
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
                    url.set("https://github.com/nickhall-ck/compose2pdf")
                    connection.set("scm:git:git://github.com/nickhall-ck/compose2pdf.git")
                }
            }
        }
    }
    repositories {
        maven {
            name = "sonatype"
            url = uri("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/")
            credentials {
                username = findProperty("ossrhUsername")?.toString()
                password = findProperty("ossrhPassword")?.toString()
            }
        }
    }
}

signing {
    isRequired = findProperty("signing.keyId") != null
    sign(publishing.publications["maven"])
}
