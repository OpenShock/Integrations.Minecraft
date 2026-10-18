pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.kikugie.dev/releases")
        maven("https://maven.fabricmc.net/")
        maven("https://maven.architectury.dev")
        maven("https://maven.minecraftforge.net")
        maven("https://maven.neoforged.net/releases/")
    }
}

plugins {
    id("gg.meza.stonecraft") version "1.14.2"
    id("dev.kikugie.stonecutter") version "0.9.7"

    // Targets span three Java versions (17 for 1.20.x, 21 for 1.21.x, 25 for 26.x). This lets
    // Gradle download any toolchain the machine is missing instead of failing the build.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

stonecutter {
    centralScript = "build.gradle.kts"
    kotlinController = true

    shared {
        // Every entry here becomes one build target. Adding a Minecraft version is a one-line
        // change; the matching versions/dependencies/<version>.properties file supplies the
        // dependency versions for it.
        fun mc(version: String, vararg loaders: String) {
            for (loader in loaders) version("$version-$loader", version)
        }

        mc("1.20.4", "fabric", "neoforge")
        mc("1.21", "fabric", "neoforge")
        mc("1.21.1", "fabric", "neoforge")
        mc("1.21.4", "fabric", "neoforge")
        mc("1.21.5", "fabric", "neoforge")
        mc("1.21.11", "fabric", "neoforge")
        mc("26.1", "fabric", "neoforge")
        mc("26.2", "fabric", "neoforge")
        // Fabric only for now: Kotlin for Forge 6.3.0, the newest release, declares
        // minecraft [1.21.9, 26.3), so FML refuses to load it - and therefore this mod - on 26.3.
        // Add "neoforge" here (26.3.properties already has a neoforge_version) once KFF catches up.
        mc("26.3", "fabric")

        vcsVersion = "26.3-fabric"
    }

    create(rootProject)
}

rootProject.name = "Integrations.Minecraft"
