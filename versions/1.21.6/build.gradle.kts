plugins {
    id("fabric-loom")
}

val minecraft_version: String by project
val fabric_api_version: String by project
val fabric_loader_version: String by project

version = rootProject.property("mod_version") as String
group = rootProject.property("mod_group") as String

base.archivesName.set("anotherinventorysort")

repositories {
    mavenCentral()
    maven("https://maven.fabricmc.net/")
}

dependencies {
    implementation(project(":common"))

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

tasks.remapJar {
    archiveVersion.set("${project.version}-mc$minecraft_version")
}