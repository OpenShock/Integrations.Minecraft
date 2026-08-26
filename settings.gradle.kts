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
    id("gg.meza.stonecraft") version "1.12.6"
    id("dev.kikugie.stonecutter") version "0.9.7"
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

        mc("1.21.1", "fabric", "neoforge")
        mc("1.21.4", "fabric", "neoforge")
        mc("1.21.8", "fabric", "neoforge")
        mc("1.21.11", "fabric", "neoforge")
        mc("26.1", "fabric", "neoforge")
        mc("26.2", "fabric", "neoforge")

        vcsVersion = "26.2-fabric"
    }

    create(rootProject)
}

rootProject.name = "Integrations.Minecraft"
