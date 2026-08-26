import gg.meza.stonecraft.mod
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    // Kotlin first: Architectury Loom snapshots the source set output directories when it is
    // applied, and if Kotlin has not registered build/classes/kotlin/main by then, that directory
    // is missing from MOD_CLASSES and the dev-time NeoForge run cannot find the mod.
    kotlin("jvm")

    // Stonecraft must be applied before Architectury Loom, which it applies itself.
    id("gg.meza.stonecraft")
    id("dev.kikugie.stonecutter")
}

// Minecraft 26.1 and newer ship deobfuscated, so those targets skip Loom's remapping entirely
// and consume mod dependencies straight off the compile classpath.
val deobfuscated = stonecutter.eval(mod.minecraftVersion, ">=26.1")

// Stonecraft picks the Java version from the Minecraft version (21 for 1.21.x, 25 for 26.x).
// Kotlin has to follow it rather than pick its own.
val javaVersion = java.toolchain.languageVersion.get().asInt()

// Bundled inside YACL's release jar via JarJar; needed explicitly only for NeoForge dev runs.
val yaclBundledLibraries = listOf(
    "org.quiltmc.parsers:json:0.2.1",
    "org.quiltmc.parsers:gson:0.2.1",
    "com.twelvemonkeys.imageio:imageio-core:3.12.0",
    "com.twelvemonkeys.imageio:imageio-webp:3.12.0",
    "com.twelvemonkeys.imageio:imageio-metadata:3.12.0",
    "com.twelvemonkeys.common:common-lang:3.12.0",
    "com.twelvemonkeys.common:common-io:3.12.0",
    "com.twelvemonkeys.common:common-image:3.12.0",
)

repositories {
    maven("https://maven.isxander.dev/releases")
    maven("https://maven.terraformersmc.com/releases")
    exclusiveContent {
        forRepository { maven("https://thedarkcolour.github.io/KotlinForForge/") }
        filter { includeGroup("thedarkcolour") }
    }
    // Mod Menu 9.x (Minecraft 1.20.x) pulls in Patbox's placeholder-api, published only here.
    exclusiveContent {
        forRepository { maven("https://maven.nucleoid.xyz/") }
        filter { includeGroup("eu.pb4") }
    }
}

kotlin {
    jvmToolchain(javaVersion)
}

/** Loom-remapped on obfuscated versions, plain on deobfuscated ones. */
fun DependencyHandler.modDependency(notation: String) =
    add(if (deobfuscated) "implementation" else "modImplementation", notation)

dependencies {
    modDependency("dev.isxander:yet-another-config-lib:${mod.prop("yacl_version")}-${mod.loader}")

    if (mod.isFabric) {
        modDependency("net.fabricmc:fabric-language-kotlin:${mod.prop("fabric_kotlin_version")}")
        modDependency("com.terraformersmc:modmenu:${mod.prop("modmenu_version")}")
    }

    if (mod.isNeoforge) {
        // Kotlin for Forge supplies the Kotlin stdlib and coroutines at runtime, the same way
        // fabric-language-kotlin does on Fabric.
        implementation("thedarkcolour:kotlinforforge-neoforge:${mod.prop("kff_version")}")

        // YACL ships these as jar-in-jar in its release jar, so they are only missing in the dev
        // environment: Loom strips nested jars, and NeoForge's module classloader does not pick up
        // the Gradle-resolved replacements sitting on the runtime classpath. Without them YACL
        // cannot build its config serializer and both YACL and this mod fail to construct.
        // The list mirrors the dependencies in YACL's own POM (minus kotlin-stdlib, which Kotlin
        // for Forge already provides); if YACL changes them, the dev run fails with a
        // ClassNotFoundException naming the one that is missing.
        for (library in yaclBundledLibraries) {
            add("forgeRuntimeLibrary", library)
        }
    }
}

modSettings {
    // Each target gets its own run directory. Minecraft 1.21.1 and 26.2 cannot share a world or an
    // options file, and a shared directory also mixes Fabric and NeoForge mod jars together.
    // Stonecraft applies this in an afterEvaluate, so overriding loom.runConfigs directly here
    // would be silently ignored.
    runDirectory = rootProject.layout.projectDirectory.dir("run/${stonecutter.current.project}")

    // Exposed to fabric.mod.json / neoforge.mods.toml as ${...}
    variableReplacements.put("yaclVersion", mod.prop("yacl_version"))
    variableReplacements.put("fabricKotlinVersion", mod.prop("fabric_kotlin_version"))
    variableReplacements.put("kffVersion", mod.prop("kff_version"))
    variableReplacements.put("javaVersion", javaVersion.toString())
    variableReplacements.put("mcDepFabric", mod.prop("mc_dep_fabric"))
    variableReplacements.put("mcDepNeoforge", mod.prop("mc_dep_neoforge"))
}

// Stonecraft already wires the Modrinth/CurseForge credentials, jar, version and display name
// from environment variables; only the things unique to this mod belong here.
// Stonecraft only configures a platform when its credentials are present, so these blocks have to
// be guarded the same way - declaring them unconditionally leaves projectId unset and fails the
// task on any machine without the secrets.
fun hasEnv(vararg names: String) = names.all { providers.environmentVariable(it).isPresent }
val publishToModrinth = hasEnv("MODRINTH_TOKEN", "MODRINTH_ID")
val publishToCurseforge = hasEnv("CURSEFORGE_TOKEN", "CURSEFORGE_ID", "CURSEFORGE_SLUG")

publishMods {
    // Stonecraft derives dryRun from DO_PUBLISH, but its docs and its code disagree about which
    // way round that is. Publishing is not reversible, so decide it here instead: nothing is
    // uploaded unless PUBLISH_RELEASE is explicitly true.
    dryRun = !providers.environmentVariable("PUBLISH_RELEASE").getOrElse("false").toBoolean()

    // Stonecraft defaults the changelog to the contents of CHANGELOG.md. The release workflow
    // passes the GitHub release body instead, so a tag's notes reach both platforms.
    providers.environmentVariable("CHANGELOG").orNull?.let { changelog = it }

    if (publishToModrinth) modrinth {
        requires("yacl")
        if (mod.isFabric) {
            requires("fabric-api")
            requires("fabric-language-kotlin")
            requires("modmenu")
        } else {
            requires("kotlin-for-forge")
        }
    }

    if (publishToCurseforge) curseforge {
        client = true
        server = false
        requires("yacl")
        if (mod.isFabric) {
            requires("fabric-api")
            requires("fabric-language-kotlin")
            requires("modmenu")
        } else {
            requires("kotlin-for-forge")
        }
    }
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions.jvmTarget = JvmTarget.fromTarget(javaVersion.toString())
    dependsOn("stonecutterGenerate")
}
