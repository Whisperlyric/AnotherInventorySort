plugins {
    id("fabric-loom") version "1.10-SNAPSHOT"
    id("java")
}

val minecraft_version: String by project
val fabric_loader_version: String by project
val fabric_api_version: String by project

base {
    archivesName.set(project.name)
}

version = project.property("mod_version") as String
group = project.property("mod_group") as String

repositories {
    mavenCentral()
    maven("https://maven.fabricmc.net/")
}

dependencies {
    minecraft("com.mojang:minecraft:$minecraft_version")
    mappings(loom.officialMojangMappings())
    modImplementation("net.fabricmc:fabric-loader:$fabric_loader_version")
    modImplementation("net.fabricmc.fabric-api:fabric-api:$fabric_api_version")
}

loom {
    accessWidenerPath.set(file("src/main/resources/anotherinventorysort.accesswidener"))
}

tasks.processResources {
    inputs.property("version", project.version)
    inputs.property("minecraft_version", minecraft_version)
    inputs.property("loader_version", fabric_loader_version)

    filesMatching("fabric.mod.json") {
        expand(
            "version" to project.version,
            "minecraft_version" to minecraft_version,
            "loader_version" to fabric_loader_version
        )
    }
}

tasks.withType<JavaCompile> {
    options.release.set(21)
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}