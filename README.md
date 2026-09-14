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
- Filter which kinds of damage may shock you, by category or by exact damage type
- Threshold of damage
- Cooldown between damage shocks
- Adjustible intensity and duration
- Shock on level up, or when a phrase you pick shows up in chat
- Collar and remote items, so somebody else can press it — within caps you set, on a collar you
  agreed to (1.21.4+)
- Sparks and a crackle around whoever just got shocked, for everyone nearby (1.21+)

### Compatibility

ShockCraft is built from one source tree for eight Minecraft versions × two loaders
([Fabric](https://fabricmc.net/) and [NeoForge](https://neoforged.net/)). Every target
ships the same jar contents; what differs is which features the underlying game version can carry.

| Minecraft | Loaders | Java |
|---|---|---|
| 1.20.4 | Fabric, NeoForge | 17 |
| 1.21 | Fabric, NeoForge | 21 |
| 1.21.1 | Fabric, NeoForge | 21 |
| 1.21.4 | Fabric, NeoForge | 21 |
| 1.21.5 | Fabric, NeoForge | 21 |
| 1.21.11 and later 1.21.x | Fabric, NeoForge | 21 |
| 26.1 | Fabric, NeoForge | 25 |
| 26.2 | Fabric, NeoForge | 25 |

Each build accepts exactly its own version, except the 1.21.11 one, which also takes any 1.21.x
above it. Nothing is published for 1.20.5–1.20.6, 1.21.2–1.21.3 or 1.21.6–1.21.10 — the loaders
refuse to load the jar there rather than load it half-broken.

#### What works where

| | 1.20.4 | 1.21 · 1.21.1 | 1.21.4 and up |
|---|:-:|:-:|:-:|
| Shock on damage, death, level up, chat phrase | ✅ | ✅ | ✅ |
| Damage filters, threshold, cooldown, intensity/duration ranges | ✅ | ✅ | ✅ |
| Config screen, shocker picker, action bar messages | ✅ | ✅ | ✅ |
| Sparks and sounds around whoever was shocked, for everyone nearby | — | ✅ † | ✅ † |
| Collar and remote items, recipes, consent prompt | — | — | ✅ † |
| Remote screen, sneak-and-scroll intensity gesture | — | — | ✅ † |
| Collar worn in an accessory slot | — | — | ✅ ‡ |

† Needs ShockCraft on the server as well — see below. Singleplayer always counts.
‡ Needs an accessory mod — see [Optional dependencies](#optional-dependencies).

The two cut-offs are the game's, not choices:

- **1.21** is where the codec-based custom payload API arrives. Below it the mod has no channel at
  all, so a client cannot tell the server it was just shocked and nothing can be drawn around it.
- **1.21.4** is the oldest target carrying the whole set the collar needs — the `Equippable`
  component, `ResourceKey`-based equipment assets, `Properties.setId` and `assets/<ns>/items/`
  model definitions. Without items there is no collar, so there is no remote either, and the
  recipes are left out of the jar rather than shipped pointing at items that do not exist.

Everything the mod does *to you* works on every version, because none of it touches the network:
health, XP and chat are all read off your own client and the shock goes straight to the OpenShock
API over HTTPS.

#### Client, server, or both

The jar loads on both sides and on dedicated servers, and what you get depends on the other end:

| You are on | What works |
|---|---|
| Singleplayer | Everything the version supports |
| A server running ShockCraft | Everything the version supports |
| A vanilla or modded server without ShockCraft | Damage, death, level-up and chat shocks, the config screen, the shocker picker. No items, no remotes, no particles |

Nothing is ever forced on you by a server. A remote press arrives as a request and is checked on
your own machine against the collar you are actually wearing, the modes you allow and the caps you
set — an operator can put a collar on you, but they cannot make it work.

#### Required dependencies

**Fabric** — all versions need [Fabric API](https://modrinth.com/mod/fabric-api),
[Fabric Language Kotlin](https://modrinth.com/mod/fabric-language-kotlin) and
[YetAnotherConfigLib](https://modrinth.com/mod/yacl). [Mod Menu](https://modrinth.com/mod/modmenu)
is listed as required too: it is the only in-game way to reach the config screen on Fabric, since
the mod adds no keybind of its own. The mod metadata only *recommends* it, so the game still
starts without it — you would just have to edit the config files by hand.

| Minecraft | Fabric API | Fabric Language Kotlin | YACL | Mod Menu |
|---|---|---|---|---|
| 1.20.4 | 0.97.3+1.20.4 | 1.13.13+kotlin.2.4.10 | 3.6.6+1.20.4 | 9.2.0 |
| 1.21 | 0.102.0+1.21 | 1.13.13+kotlin.2.4.10 | 3.8.2+1.21.1 | 11.0.4 |
| 1.21.1 | 0.116.15+1.21.1 | 1.13.13+kotlin.2.4.10 | 3.8.2+1.21.1 | 11.0.4 |
| 1.21.4 | 0.119.4+1.21.4 | 1.13.13+kotlin.2.4.10 | 3.8.2+1.21.4 | 13.0.4 |
| 1.21.5 | 0.128.2+1.21.5 | 1.13.13+kotlin.2.4.10 | 3.8.2+1.21.5 | 14.0.2 |
| 1.21.11 | 0.141.6+1.21.11 | 1.13.13+kotlin.2.4.10 | 3.8.2+1.21.11 | 17.0.1-beta.1 |
| 26.1 | 0.155.2+26.1.2 | 1.13.13+kotlin.2.4.10 | 3.9.6+26.1 | 18.0.0 |
| 26.2 | 0.158.0+26.2 | 1.13.13+kotlin.2.4.10 | 3.9.6+26.2 | 20.0.1 |

**NeoForge** — [Kotlin for Forge](https://modrinth.com/mod/kotlin-for-forge) supplies the Kotlin
runtime, and its version range is declared as the `modLoader` version, so an older one makes FML
reject the jar outright. [YACL](https://modrinth.com/mod/yacl) is declared client-side only here.

| Minecraft | NeoForge (minimum) | Kotlin for Forge (minimum) | YACL |
|---|---|---|---|
| 1.20.4 | 20.4.251 | 4.12.0 | 3.6.6+1.20.4 |
| 1.21 | 21.0.167 | 5.12.0 | 3.8.2+1.21.1 |
| 1.21.1 | 21.1.248 | 5.12.0 | 3.8.2+1.21.1 |
| 1.21.4 | 21.4.157 | 5.12.0 | 3.8.2+1.21.4 |
| 1.21.5 | 21.5.98 | 5.12.0 | 3.8.2+1.21.5 |
| 1.21.11 | 21.11.45 | 6.3.0 | 3.8.2+1.21.11 |
| 26.1 | 26.1.2.98 | 6.3.0 | 3.9.6+26.1 |
| 26.2 | 26.2.0.69 | 6.3.0 | 3.9.6+26.2 |

The versions above are what each target is built and tested against. Fabric API and YACL are
declared as `*` in the Fabric metadata, so a newer one loads; the NeoForge side declares its
minimum as an open range, so newer is fine there too.

On Fabric, YACL and Fabric Language Kotlin are hard dependencies on **both** sides, so a Fabric
dedicated server needs them installed alongside the mod. NeoForge servers only need Kotlin for
Forge.

#### Optional dependencies

None of these is required, and the mod never checks which mod is installed — it looks for the API
class, so any mod providing it counts.

**Accessory slot for the collar.** With one installed, the collar is worn in a belt accessory slot
and shows as a cuff on the ankle. Without one, the collar falls back to the vanilla leggings slot
instead, so it works either way, but it costs you a pair of leggings. Only relevant from 1.21.4,
where the collar exists.

| Minecraft | Fabric | NeoForge |
|---|---|---|
| 1.20.4 – 1.21.1 | n/a — no collar on these versions | n/a |
| 1.21.4 · 1.21.5 · 1.21.11 | [Trinkets (Canary)](https://modrinth.com/mod/trinkets-canary) | [Curios](https://modrinth.com/mod/curios) |
| 26.1 · 26.2 | [Trinkets Updated](https://modrinth.com/mod/trinkets-updated) | [Trinkets Updated](https://modrinth.com/mod/trinkets-updated) |

**[Mod Menu](https://modrinth.com/mod/modmenu)** (Fabric) is the one entry here that is optional only on paper — see above. On NeoForge
the config button comes from the loader's own mod list and Mod Menu is not involved at all.

### Config GUI by [YetAnotherConfigLib](https://github.com/isXander/YetAnotherConfigLib)

![Config GUI](https://cdn.modrinth.com/data/DwMSqx5B/images/e2c399b6c2c222aa3c729e9ae069d854a2708a4a.png)

### Picking your shockers

Enter your API URL and token under **Setup**, and the **Shockers** group fills itself with every
shocker that token can control - your own and the ones shared with you - as a checkbox each. Tick
the ones ShockCraft should use; no IDs to copy over by hand.

The list is fetched the first time the settings screen is opened with a given token, and
**Reload shockers from OpenShock** fetches it again (saving the screen first, so a token you just
typed is the one it logs in with). If the backend cannot be reached, the reason is shown above the
button and **Shocker IDs (manual)** still takes IDs typed in by hand.

### Where the config lives

The behaviour settings are per instance, in `config/ShockCraft.json5`, so they travel with a
modpack or an exported instance like any other mod config.

Your API URL, API token and shocker IDs are not. They belong to your OpenShock account rather than
to one instance, so they are stored per user instead:

| | |
|---|---|
| Windows | `%APPDATA%\OpenShock\ShockCraft\account.json5` |
| macOS | `~/Library/Application Support/OpenShock/ShockCraft/account.json5` |
| Linux | `$XDG_CONFIG_HOME/OpenShock/ShockCraft/account.json5` (usually `~/.config`) |

This keeps your token out of the files you share when you send someone an instance or attach a log
to a bug report, and means you only enter it once no matter how many instances or launchers you
run. It is still stored in plain text - it is not encrypted, and anyone with access to your user
account can read it. Existing setups are migrated automatically the first time you launch, and the
token is removed from the old instance config.

## Support

You can support the openshock dev team here: [Sponsor OpenShock](https://github.com/sponsors/OpenShock)

## Development

The mod is built from a single source tree for every supported Minecraft version and both mod
loaders, using [Stonecutter](https://github.com/stonecutter-versioning/stonecutter) for the version
matrix and [Stonecraft](https://github.com/meza/Stonecraft) (which wraps Architectury Loom) for the
loader wiring.

| | |
|---|---|
| Minecraft | 1.20.4, 1.21, 1.21.1, 1.21.4, 1.21.5, 1.21.11, 26.1, 26.2 |
| Loaders | Fabric, NeoForge |
| Targets | 16 (every version × every loader) |
| Java | 17 for 1.20.x, 21 for 1.21.x, 25 for 26.x (Gradle downloads any it is missing) |

The per-version dependency versions and the feature differences between targets are in
[Compatibility](#compatibility); `versions/dependencies/<version>.properties` is the source of
truth for both.

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

```bash
./gradlew :26.2-fabric:runClient
./gradlew :1.21.1-neoforge:runClient
```

In IntelliJ, the same targets are under `runClient` for each subproject in the Gradle tool window,
and Architectury Loom adds a `Minecraft Client (:<version>-<loader>)` configuration per target
during sync, which launches the game directly and is the easier one to attach a debugger to.

Dev runs log in as `developer`. To play as yourself, set `dev_username` in `gradle.properties` or
`~/.gradle/gradle.properties`:

```properties
dev_username=YourName
```

FOV, GUI scale and the narrator are pinned for every target in the `clientOptions` block in
`build.gradle.kts`, and written to `run/<version>-<loader>/options.txt` before each launch. They
are reapplied every run, so change them there rather than in the game's own settings screen.

You do not need to switch the active target to run a different one - every target is a real Gradle
subproject and can be launched directly. Switching only changes which one your IDE indexes and
edits.

Each target runs in its own directory, `run/<version>-<loader>/`, because Minecraft 1.21.1 and 26.2
cannot share a world or an options file and the two loaders cannot share a mods folder.

### Releasing

Pushing a version tag runs `.github/workflows/release.yml`, which builds all targets, creates a
GitHub release with the jars attached, and publishes every one of them to Modrinth and CurseForge.

```bash
git tag 1.4.0 && git push origin 1.4.0
```

The tag is the source of truth for the version (tag `1.4.0` publishes `1.4.0`), so `mod.version` in
`gradle.properties` does not need bumping first. The changelog on Modrinth and CurseForge is a link
back to the GitHub release, so the notes only ever live in one place.

The tag also decides the release type, consistently across all three destinations:

| Tag contains | Modrinth / CurseForge | GitHub release |
|---|---|---|
| `alpha` | Alpha | pre-release |
| `beta`, `next`, `rc` | Beta | pre-release |
| anything else | Stable | normal |

`rc` needs an explicit mapping because Stonecraft's own inference recognises only `alpha`, `beta`
and `next`; left to it, an `rc` tag would reach both platforms marked stable.

Configure these under **Settings -> Secrets and variables -> Actions**:

| Secret | |
|---|---|
| `MODRINTH_TOKEN` | Modrinth PAT with the "Create versions" scope |
| `CURSEFORGE_TOKEN` | CurseForge API token |

| Variable | Current value |
|---|---|
| `MODRINTH_ID` | `DwMSqx5B` |
| `CURSEFORGE_ID` | `980833` |
| `CURSEFORGE_SLUG` | `openshock-shockcraft` |

The workflow checks all five are set before building, so a missing one fails in seconds with a
clear message rather than part-way through publishing.

Nothing is ever uploaded unless `PUBLISH_RELEASE=true`, which only the release workflow sets, so
running `./gradlew chiseledPublishMods` locally is always a dry run. Use it to preview exactly what
would be sent:

```bash
./gradlew chiseledPublishMods
```

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

Everything outside `platform/` compiles unchanged on all sixteen targets. Sources use **Mojang
mappings**, which both loaders share.

The version-conditional code is deliberately confined to those files. `McCompat.kt` covers the
vanilla renames (`ResourceLocation`/`Identifier`, the `fromNamespaceAndPath` factory,
`displayClientMessage`/`sendOverlayMessage`, `Minecraft.screen` moving onto `Gui`), and the
NeoForge half of `Entrypoint.kt` covers the 1.21 API break (`TickEvent.ClientTickEvent` became
`ClientTickEvent.Post`, `ConfigScreenHandler.ConfigScreenFactory` became `IConfigScreenFactory`,
and `@Mod` gained its `dist` element).
