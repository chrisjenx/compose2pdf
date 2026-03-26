plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.compose) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.maven.publish) apply false
}

allprojects {
    group = "com.chrisjenx"
    version = rootProject.findProperty("version") as String
}
