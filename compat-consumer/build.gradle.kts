plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
    application
}

val composeVersion = providers.gradleProperty("composeVersion").orElse("1.11.1").get()
val compose2pdfVersion = providers.gradleProperty("compose2pdfVersion").orElse("1.1.4-SNAPSHOT").get()
val skikoVersion = providers.gradleProperty("skikoVersion").orNull // fallback override; normally unset

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation("com.chrisjenx:compose2pdf:$compose2pdfVersion")
    implementation(compose.desktop.currentOs)
}

application {
    mainClass.set("com.chrisjenx.compat.SmokeKt")
}

// compose2pdf is published against its pinned base Compose version and requests it
// transitively; without this, conflict resolution would UPGRADE the target back to the
// base and the smoke check would run on the wrong runtime. Forcing the whole Compose
// group to the target makes each module resolve its own POM, so Skiko cascades to the
// version that Compose version declares — no per-row Skiko bookkeeping. If some version
// ever fails to cascade Skiko, pass -PskikoVersion=<v> to pin it explicitly.
configurations.all {
    resolutionStrategy.eachDependency {
        if (requested.group.startsWith("org.jetbrains.compose")) {
            useVersion(composeVersion)
            because("compat runtime swap: exercise the published binary on Compose $composeVersion")
        }
        if (skikoVersion != null && requested.group == "org.jetbrains.skiko") {
            useVersion(skikoVersion)
        }
    }
}
