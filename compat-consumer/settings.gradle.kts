// Standalone build (NOT included in the root settings.gradle.kts) so it can apply a
// different Compose Multiplatform version than the library it consumes. Plugin versions
// come from -PcomposeVersion / -PkotlinVersion (CI passes them; defaults are a fallback).
pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }
    val composeVersion = providers.gradleProperty("composeVersion").orElse("1.11.1").get()
    val kotlinVersion = providers.gradleProperty("kotlinVersion").orElse("2.4.0").get()
    plugins {
        id("org.jetbrains.kotlin.jvm") version kotlinVersion
        id("org.jetbrains.compose") version composeVersion
        id("org.jetbrains.kotlin.plugin.compose") version kotlinVersion
    }
}

dependencyResolutionManagement {
    repositories {
        mavenLocal() // the compose2pdf snapshot under test
        google()
        mavenCentral()
    }
}

rootProject.name = "compat-consumer"
