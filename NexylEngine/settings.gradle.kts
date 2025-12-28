rootProject.name = "NexylEngine"

pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven("https://plugins.gradle.org/m2/")
        maven("https://central.sonatype.com/repository/maven-snapshots")
    }
    
    // Указываем использовать Liberica JDK
    plugins {
        kotlin("jvm") version "1.9.22"
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

// Настройка toolchains для Liberica
dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

