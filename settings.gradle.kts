pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net/") {
            name = "Fabric"
        }
        gradlePluginPortal()
    }
}

plugins {
    // Automatically downloads the toolchain's JDK 17 if it is not installed.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
