plugins {
    id("java")
}

// Common 模块只提供纯 Java 代码，不依赖 Minecraft API

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}