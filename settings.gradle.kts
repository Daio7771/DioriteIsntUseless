pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net/") {
            name = "Fabric"
        }
        gradlePluginPortal()
    }
}

plugins {
    // Descarga automáticamente el JDK 17 del toolchain si no está instalado.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
