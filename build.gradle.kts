plugins {
    id("fabric-loom") version "1.10-SNAPSHOT" apply false
    id("java")
}

tasks.register("buildAll") {
    dependsOn(subprojects.map { it.tasks.named("build") })
}