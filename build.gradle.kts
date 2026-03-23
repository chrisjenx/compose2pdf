plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.compose) apply false
    alias(libs.plugins.compose.compiler) apply false
}

allprojects {
    group = "com.chrisjenx"
    version = "0.1.0"
}
