import gg.meza.stonecraft.mod
import net.fabricmc.loom.api.LoomGradleExtensionAPI
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

// The collar and remote need the Equippable component and the equipment/item-model layout that
// came with it - see ModContent, which lists what 1.21.4 is the oldest target to have. Older
// targets build without them, so their recipes have to be left out too - a recipe naming an item
// that does not exist is a parse error in the log on every world load.
val hasItems = stonecutter.eval(mod.minecraftVersion, ">=1.21.4")

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
    // Trinkets, Trinkets Updated and Curios are all published here and nowhere else in common.
    exclusiveContent {
        forRepository { maven("https://api.modrinth.com/maven") }
        filter { includeGroup("maven.modrinth") }
    }
    exclusiveContent {
        forRepository { maven("https://maven.ladysnake.org/releases") }
        filter { includeGroup("org.ladysnake.cardinal-components-api") }
    }
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

tasks.processResources {
    // Recipes and accessory-slot data both name the collar, and a datapack entry pointing at an
    // item that does not exist is an error in the log on every world load.
    if (!hasItems) exclude("data/**")
}

/** Loom-remapped on obfuscated versions, plain on deobfuscated ones. */
fun DependencyHandler.modDependency(notation: String) =
    add(if (deobfuscated) "implementation" else "modImplementation", notation)

/** The same, for APIs we compile against but never require at runtime. */
fun DependencyHandler.modCompileDependency(notation: String) =
    add(if (deobfuscated) "compileOnly" else "modCompileOnly", notation)

/**
 * In the dev run only. `localRuntime` is never published, so nothing added through here becomes a
 * dependency of the released mod - it just means `runClient` starts with the mod already there.
 */
fun DependencyHandler.modDevRuntimeDependency(notation: String) =
    add(if (deobfuscated) "localRuntime" else "modLocalRuntime", notation)

dependencies {
    modDependency("dev.isxander:yet-another-config-lib:${mod.prop("yacl_version")}-${mod.loader}")

    // The accessory slot the collar is worn in. Three different mods provide it across the range -
    // see CollarSlot - and all three are compile-only: none is required at runtime, and without
    // one the collar falls back to the head slot. Referenced by Modrinth version id rather than
    // version number, because a '+' in a Gradle version string means "dynamic version".
    if (hasItems) {
        val slotMod = when {
            stonecutter.eval(mod.minecraftVersion, ">=26.1") ->
                "maven.modrinth:trinkets-updated:${mod.prop("slot_mod_version")}"
            mod.isFabric -> "maven.modrinth:trinkets-canary:${mod.prop("slot_mod_version_fabric")}"
            else -> "maven.modrinth:curios:${mod.prop("slot_mod_version_neoforge")}"
        }

        modCompileDependency(slotMod)

        // And in the dev run, because the half of the collar that needs an accessory mod is the
        // half worth looking at: the worn model from assets/shockcraft/trinkets/collar.json is
        // drawn by this and nothing else, so without it `runClient` only ever shows the flat
        // leggings-slot texture. Still not required of anyone installing the mod - localRuntime
        // is not published, and CollarSlot falls back to the leggings slot when it is absent.
        modDevRuntimeDependency(slotMod)

        // Trinkets 3.x builds its component on Cardinal Components, so TrinketComponent's
        // supertype has to be resolvable even though we never name it. Trinkets Updated 4.x
        // dropped the dependency, so this is only needed below 26.1.
        //
        // Straight off Ladysnake's maven rather than Modrinth: the Modrinth artifact is a JarJar
        // container whose modules Loom strips, so the classes never reach the compile classpath -
        // the same trap the YACL libraries above work around.
        if (mod.isFabric && !stonecutter.eval(mod.minecraftVersion, ">=26.1")) {
            compileOnly("org.ladysnake.cardinal-components-api:cardinal-components-base:${mod.prop("cca_version")}")

            // And again for the dev run, for a second reason. Trinkets 3.x nests these two in its
            // own jar, but remapping strips nested jars outright - the remapped artifact declares
            // none, where the one on Modrinth declares both - so a dev client would load Trinkets
            // straight into a missing-dependency failure unless they are named here.
            //
            // Only below 26.1. Trinkets Updated 4.x dropped Cardinal Components entirely, and its
            // targets are deobfuscated, so nothing remaps and what it does nest survives.
            for (module in listOf("cardinal-components-base", "cardinal-components-entity")) {
                modDevRuntimeDependency("org.ladysnake.cardinal-components-api:$module:${mod.prop("cca_version")}")
            }
        }
    }

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

    // Written into <runDirectory>/options.txt by configureMinecraftClient, which runClient depends
    // on. These are forced on every launch, so changing them in game does not stick - only pin the
    // ones worth having identical in every target. Anything else options.txt understands can go in
    // additionalLines, e.g. additionalLines.put("maxFps", "120").
    clientOptions {
        fov = 110
        guiScale = 2
        narrator = false
        musicVolume = 0.0
        additionalLines.put("maxFps", "170")
    }

    // Exposed to fabric.mod.json / neoforge.mods.toml as ${...}
    variableReplacements.put("yaclVersion", mod.prop("yacl_version"))
    variableReplacements.put("fabricKotlinVersion", mod.prop("fabric_kotlin_version"))
    variableReplacements.put("kffVersion", mod.prop("kff_version"))
    variableReplacements.put("javaVersion", javaVersion.toString())
    variableReplacements.put("mcDepFabric", mod.prop("mc_dep_fabric"))
    variableReplacements.put("mcDepNeoforge", mod.prop("mc_dep_neoforge"))
}

// The in-game name for dev runs. Set dev_username in your own gradle.properties (this file's, or
// ~/.gradle/gradle.properties) to play as yourself; unset it stays on Stonecraft's "developer".
//
// Stonecraft pins --username=developer on the client run inside its own afterEvaluate, so this has
// to run in a later one to win - the same ordering caveat as runDirectory above. Replacing rather
// than appending, because two --username arguments would leave the choice to the argument parser.
afterEvaluate {
    val username = providers.gradleProperty("dev_username").getOrElse("developer")

    extensions.getByType<LoomGradleExtensionAPI>().runConfigs.named("client") {
        val existing = programArguments.get()
        programArguments.set(
            existing.filterNot { it.startsWith("--username") } + "--username=$username"
        )
    }
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
