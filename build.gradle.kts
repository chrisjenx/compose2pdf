plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.compose) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.nexus.publish)
}

allprojects {
    group = "com.chrisjenx"
    version = rootProject.findProperty("version") as String
}

nexusPublishing {
    repositories {
        sonatype {
            nexusUrl.set(uri("https://s01.oss.sonatype.org/service/local/"))
            snapshotRepositoryUrl.set(uri("https://s01.oss.sonatype.org/content/repositories/snapshots/"))
            username.set(findProperty("mavenCentralUsername")?.toString())
            password.set(findProperty("mavenCentralPassword")?.toString())
        }
    }
}
