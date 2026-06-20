pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.fabricmc.net/")
    }

    plugins {
        id("net.fabricmc.fabric-loom") version "1.15.+"
        kotlin("jvm") version extra["kotlin_version"] as String
    }
}

rootProject.name = "kotlinmcui-backend"
