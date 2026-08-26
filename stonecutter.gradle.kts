plugins {
    id("dev.kikugie.stonecutter")
    id("gg.meza.stonecraft")

    // Declared here so the central build.gradle.kts can apply it without repeating the version.
    // 2.4.0 is the lowest Kotlin bundled by our runtime providers (Kotlin for Forge 5.12/6.3 ship
    // 2.4.0, fabric-language-kotlin 1.13.13 ships 2.4.10), so compiling against it is safe on both.
    kotlin("jvm") version "2.4.0" apply false
}

stonecutter active "26.2-fabric" /* [SC] DO NOT EDIT */
