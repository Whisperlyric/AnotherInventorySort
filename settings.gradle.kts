pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.fabricmc.net/") {
            name = "Fabric"
        }
    }
}

rootProject.name = "anotherinventorysort"

include("common")
include("versions:1.20.1")
include("versions:1.20.5")
include("versions:1.21.1")
include("versions:1.21.2")
include("versions:1.21.6")
include("versions:1.21.9")
include("versions:1.21.11")
include("versions:26.1")