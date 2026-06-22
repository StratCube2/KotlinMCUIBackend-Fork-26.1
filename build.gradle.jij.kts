import org.gradle.kotlin.dsl.provideDelegate
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("net.fabricmc.fabric-loom")
    kotlin("jvm")
}

val maven_group: String by project
val archives_base_name: String by project
val license: String by project
val mod_version: String by project
val mod_id: String by project
val mod_name: String by project
val mod_description: String by project
val mod_authors: String by project
val mod_github: String by project
val mod_issues: String by project
val mod_mcmod: String by project
val mod_modrinth: String by project
val mod_curseforge: String by project
val icon: String by project

val kotlin_version: String by project
val java_version: String by project
val kotlinmcui_version: String by project
val minecraft_version: String by project
val minecraft_version_range: String by project
val loader: String by project
val loader_suffix: String by project
val loader_version: String by project
val loader_version_range: String by project
val fabric_kotlin_version: String by project
val mod_menu_version: String by project

base.archivesName = archives_base_name

group = maven_group
version = "$mod_version+$loader$loader_suffix+$minecraft_version"

repositories {
    mavenCentral()
    maven("https://maven.fabricmc.net/")
    maven("https://maven.terraformersmc.com")
    maven("https://jitpack.io")
}

loom {
    accessWidenerPath = file("src/main/resources/kotlinmcuibackend.accesswidener")
}

configurations.all {
    resolutionStrategy {
        cacheChangingModulesFor(0, "seconds")
    }
}

dependencies {
    minecraft("com.mojang:minecraft:$minecraft_version")
    implementation("net.fabricmc:fabric-loader:$loader_version")
    implementation("net.fabricmc:fabric-language-kotlin:$fabric_kotlin_version")
    implementation("com.terraformersmc:modmenu:$mod_menu_version")
                        
    val localFiles = files(
        "../kotlinmcui/build/libs/kotlinmcui-$kotlinmcui_version.jar",
        "../kotlinmcui/build/libs/kotlinmcui-$kotlinmcui_version-sources.jar",
    )
                                
    if (localFiles.all { it.exists() }) {
        implementation(localFiles)
        // Bundle the local library jar inside the output jar
        include(files("../kotlinmcui/build/libs/kotlinmcui-$kotlinmcui_version.jar"))
    } else {
        val kotlinmcuiDep = "com.github.2894638479:KotlinMCUI:v$kotlinmcui_version"
        implementation(kotlinmcuiDep)
        // Bundle the maven-hosted library jar inside the output jar
        include(kotlinmcuiDep)
    }
}

kotlin {
    jvmToolchain(25)
    compilerOptions {
        jvmTarget.set(JvmTarget.valueOf("JVM_$java_version"))
    }
}

java {
    withSourcesJar()
    sourceCompatibility = JavaVersion.valueOf("VERSION_$java_version")
    targetCompatibility = JavaVersion.valueOf("VERSION_$java_version")
}

val sourcesJar: Jar by tasks
sourcesJar.exclude("fabric.mod.json")
sourcesJar.from("LICENSE")

tasks.jar {
    from("LICENSE")
}

val compileKotlin: KotlinCompile by tasks
compileKotlin.compilerOptions {
    freeCompilerArgs.set(listOf("-Xcontext-parameters"))
}

tasks.processResources {
    val map = mapOf(
        "license" to license,
        "mod_version" to mod_version,
        "mod_id" to mod_id,
        "mod_name" to mod_name,
        "mod_description" to mod_description,
        "mod_authors" to mod_authors,
        "mod_github" to mod_github,
        "mod_issues" to mod_issues,
        "mod_mcmod" to mod_mcmod,
        "mod_modrinth" to mod_modrinth,
        "mod_curseforge" to mod_curseforge,
        "icon" to icon,
        "kotlin_version" to kotlin_version,
        "java_version" to java_version,
        "kotlinmcui_version" to kotlinmcui_version,
        "minecraft_version" to minecraft_version,
        "minecraft_version_range" to minecraft_version_range,
        "loader" to loader,
        "loader_version" to loader_version,
        "loader_version_range" to loader_version_range,
        "fabric_kotlin_version" to fabric_kotlin_version,
        "mod_menu_version" to mod_menu_version,
    )
    inputs.properties(map)
    filesMatching("fabric.mod.json") { expand(map) }
}
