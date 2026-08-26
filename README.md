<center><div align="center">
  
# OpenShock Minecraft Integration (ShockCraft)

![](https://img.shields.io/badge/Enviroment-Client-purple?style=for-the-badge)
[![Modrinth](https://img.shields.io/modrinth/dt/shockcraft?color=00AF5C&label=downloads&logo=modrinth&style=for-the-badge)](https://modrinth.com/mod/shockcraft)
[![CurseForge](https://img.shields.io/curseforge/dt/980833?style=for-the-badge&logo=curseforge&color=f16436)](https://www.curseforge.com/minecraft/mc-mods/openshock-shockcraft)

[![Discord](https://img.shields.io/discord/1078124408775901204?style=for-the-badge&color=6451f1&label=OpenShock%20Discord&logo=discord)](https://openshock.net/discord)
[![Website](https://img.shields.io/badge/Website-e14a6d?style=for-the-badge)](https://openshock.net)

</div></center>

### Features

- Shock on Death
- Shock on Damage
- Multiple on damage modes
- Threshold of damage
- Cooldown between damage shocks
- Adjustible intensity and duration

### Config GUI by [YetAnotherConfigLib](https://github.com/isXander/YetAnotherConfigLib)

![Config GUI](https://cdn.modrinth.com/data/DwMSqx5B/images/e2c399b6c2c222aa3c729e9ae069d854a2708a4a.png)

## Support

You can support the openshock dev team here: [Sponsor OpenShock](https://github.com/sponsors/OpenShock)

## Development

The mod is built from a single source tree for every supported Minecraft version and both mod
loaders, using [Stonecutter](https://github.com/stonecutter-versioning/stonecutter) for the version
matrix and [Stonecraft](https://github.com/meza/Stonecraft) (which wraps Architectury Loom) for the
loader wiring.

| | |
|---|---|
| Minecraft | 1.21.1, 1.21.4, 1.21.8, 1.21.11, 26.1, 26.2 |
| Loaders | Fabric, NeoForge |
| Targets | 12 (every version × every loader) |

### Building

```bash
./gradlew chiseledBuildAndCollect   # build every target; jars land in build/libs/
./gradlew build                     # build only the currently active target
```

The active target is the one your IDE and `runClient` use. Switch it with:

```bash
./gradlew "Set active project to 1.21.1-neoforge"
```

Stonecutter rewrites the files under `src/` in place when you switch, commenting out the branches
that do not apply to the new target. That is expected — do not revert it.

### Running the game

Architectury Loom generates one IntelliJ run configuration per target, named
`Minecraft Client (:<version>-<loader>)`. They are created during Gradle sync, so after cloning (or
after adding a version) use **Gradle tool window -> Reload All Gradle Projects** before looking for
them. `.idea/` is gitignored, so these are local-only and always regenerated.

From the command line:

```bash
./gradlew :26.2-fabric:runClient
./gradlew :1.21.1-neoforge:runClient
```

You do not need to switch the active target to run a different one - every target is a real Gradle
subproject and can be launched directly. Switching only changes which one your IDE indexes and
edits.

Each target runs in its own directory, `run/<version>-<loader>/`, because Minecraft 1.21.1 and 26.2
cannot share a world or an options file and the two loaders cannot share a mods folder.

### Layout

- `settings.gradle.kts` — the list of targets. Adding a Minecraft version is one line here plus a
  matching `versions/dependencies/<version>.properties` file.
- `versions/dependencies/<version>.properties` — every dependency version for that Minecraft
  version, shared by its Fabric and NeoForge targets. This is the only file to touch when bumping
  Fabric API, NeoForge, YACL, Mod Menu or Kotlin for Forge.
- `build.gradle.kts` — applied to every target.
- `src/main/kotlin/.../platform/` — the only loader- and version-specific code:
  - `Entrypoint.kt` — the Fabric and NeoForge entrypoints.
  - `Platform.kt` — the handful of loader APIs with no common equivalent.
  - `McCompat.kt` — the few vanilla APIs that changed across the version range.

Everything outside `platform/` compiles unchanged on all twelve targets. Sources use **Mojang
mappings**, which both loaders share.
