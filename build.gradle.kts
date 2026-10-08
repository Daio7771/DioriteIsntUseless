import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "2.4.20"
    id("fabric-loom") version "1.18-SNAPSHOT"
    id("maven-publish")
}

version = project.property("mod_version") as String
group = project.property("maven_group") as String

base {
    archivesName.set(project.property("archives_base_name") as String)
}

val targetJavaVersion = 17
java {
    toolchain.languageVersion = JavaLanguageVersion.of(targetJavaVersion)
    // Loom will automatically attach sourcesJar to a RemapSourcesJar task and to the "build" task
    // if it is present.
    // If you remove this line, sources will not be generated.
    withSourcesJar()
}

loom {
    splitEnvironmentSourceSets()

    mods {
        register("dioriteisntuseless") {
            sourceSet("main")
            sourceSet("client")
        }
    }

    runs {
        // El servidor en su propia carpeta: así cliente y servidor tienen cada uno su config/ y se
        // puede probar la sincronización con valores distintos en cada lado.
        named("server") {
            runDir = "run/server"
        }
    }
}

fabricApi {
    configureDataGeneration {
        client = true
    }
}

repositories {
    // Add repositories to retrieve artifacts from in here.
    // You should only use this when depending on other mods because
    // Loom adds the essential maven repositories to download Minecraft and libraries from automatically.
    // See https://docs.gradle.org/current/userguide/declaring_repositories.html
    // for more information about repositories.
    maven("https://maven.shedaniel.me/") { name = "Shedaniel" }  // Cloth Config
    maven("https://maven.terraformersmc.com/releases/") { name = "TerraformersMC" }  // Mod Menu
    maven("https://maven.nucleoid.xyz/") { name = "Nucleoid" }  // Common Protection API
}

dependencies {
    // To change the versions see the gradle.properties file
    minecraft("com.mojang:minecraft:${project.property("minecraft_version")}")
    mappings(loom.officialMojangMappings())
    modImplementation("net.fabricmc:fabric-loader:${project.property("loader_version")}")
    modImplementation("net.fabricmc:fabric-language-kotlin:${project.property("kotlin_loader_version")}")

    modImplementation("net.fabricmc.fabric-api:fabric-api:${project.property("fabric_version")}")

    // Cloth Config y Mod Menu son opcionales ("suggests" en fabric.mod.json): se compila contra
    // ellas, pero el mod funciona sin ellas. Solo dan la pantalla de configuración.
    val clothConfig = "me.shedaniel.cloth:cloth-config-fabric:${project.property("cloth_config_version")}"
    val modMenu = "com.terraformersmc:modmenu:${project.property("modmenu_version")}"
    modCompileOnly(clothConfig) { exclude(group = "net.fabricmc.fabric-api") }
    modCompileOnly(modMenu) { exclude(group = "net.fabricmc.fabric-api") }
    // Se cargan en runClient/runServer para probar la pantalla. Para probar sin ellas:
    // ./gradlew runClient -PnoOptionalMods
    if (!project.hasProperty("noOptionalMods")) {
        modLocalRuntime(clothConfig) { exclude(group = "net.fabricmc.fabric-api") }
        modLocalRuntime(modMenu) { exclude(group = "net.fabricmc.fabric-api") }
    }

    // Common Protection API, también opcional: si está instalada (la traen los mods de claims),
    // el Abuse Mode respeta sus zonas protegidas. Solo se compila contra ella.
    modCompileOnly("eu.pb4:common-protection-api:${project.property("common_protection_api_version")}") {
        exclude(group = "net.fabricmc.fabric-api")
    }
}

tasks.processResources {
    inputs.property("version", project.version)
    inputs.property("minecraft_version", project.property("minecraft_version"))
    inputs.property("loader_version", project.property("loader_version"))
    inputs.property("kotlin_loader_version", project.property("kotlin_loader_version"))
    filteringCharset = "UTF-8"

    filesMatching("fabric.mod.json") {
        expand(
            "version" to project.version,
            "minecraft_version" to project.property("minecraft_version") as String,
            "loader_version" to project.property("loader_version") as String,
            "kotlin_loader_version" to project.property("kotlin_loader_version") as String
        )
    }
}

tasks.withType<JavaCompile>().configureEach {
    // ensure that the encoding is set to UTF-8, no matter what the system default is
    // this fixes some edge cases with special characters not displaying correctly
    // see http://yodaconditions.net/blog/fix-for-java-file-encoding-problems-with-gradle.html
    // If Javadoc is generated, this must be specified in that task too.
    options.encoding = "UTF-8"
    options.release.set(targetJavaVersion)
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions.jvmTarget.set(JvmTarget.fromTarget(targetJavaVersion.toString()))
}

tasks.jar {
    from("LICENSE") {
        rename { "${it}_${project.base.archivesName.get()}" }
    }
}

// configure the maven publication
publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            artifactId = project.property("archives_base_name") as String
            from(components["java"])
        }
    }

    // See https://docs.gradle.org/current/userguide/publishing_maven.html for information on how to set up publishing.
    repositories {
        // Add repositories to publish to here.
        // Notice: This block does NOT have the same function as the block in the top level.
        // The repositories here will be used for publishing your artifact, not for
        // retrieving dependencies.
    }
}
