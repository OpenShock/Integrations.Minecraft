package openshock.integrations.minecraft

import dev.isxander.yacl3.api.*
import dev.isxander.yacl3.api.controller.EnumControllerBuilder
import dev.isxander.yacl3.api.controller.IntegerSliderControllerBuilder
import dev.isxander.yacl3.api.controller.StringControllerBuilder
import dev.isxander.yacl3.api.controller.TickBoxControllerBuilder
import dev.isxander.yacl3.api.utils.OptionUtils
import dev.isxander.yacl3.gui.YACLScreen
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import openshock.integrations.minecraft.api.RemoteMode
import openshock.integrations.minecraft.api.Shocker
import openshock.integrations.minecraft.api.ShockerCatalog
import openshock.integrations.minecraft.config.AccountConfig
import openshock.integrations.minecraft.config.DamageCategory
import openshock.integrations.minecraft.config.DamageFilter
import openshock.integrations.minecraft.config.DamageFilterMode
import openshock.integrations.minecraft.config.DamageShockMode
import openshock.integrations.minecraft.config.ShockCraftConfig
import openshock.integrations.minecraft.platform.McCompat
import openshock.integrations.minecraft.platform.NetClient
import openshock.integrations.minecraft.utils.shortCode

/**
 * Builds the YACL settings screen. Loader-agnostic: Fabric reaches it through Mod Menu and
 * NeoForge through IConfigScreenFactory, both in [openshock.integrations.minecraft.platform].
 */
object ConfigScreen {

    /**
     * What the preview button draws as. Full strength and a couple of seconds, because the point
     * of it is seeing the effect at its biggest - it is not shocking anybody, so there is nothing
     * to be gentle about.
     */
    private const val PREVIEW_INTENSITY: Byte = 100
    private val PREVIEW_DURATION: UShort = 2000u

    fun create(parent: Screen?): Screen {
        // One screen over two config files, so the save button has to fan out to both handlers
        // instead of letting YetAnotherConfigLib.create wire up the single one it knows about.
        val builder = YetAnotherConfigLib.createBuilder()
            .save {
                ShockCraftConfig.HANDLER.save()
                AccountConfig.HANDLER.save()
            }

        val account = AccountConfig.HANDLER.instance()

        val yacl = createBuilder(
            ShockCraftConfig.HANDLER.defaults(),
            ShockCraftConfig.HANDLER.instance(),
            AccountConfig.HANDLER.defaults(),
            account,
            parent,
            builder
        ).build()

        val screen = yacl.generateScreen(parent)

        // A YACL screen is built from a fixed list of options, so the shocker checkboxes can only
        // show what [ShockerCatalog] already holds - on the first open with a given token that is
        // nothing. Fetch in the background and swap in a rebuilt screen once the list arrives,
        // rather than making the user press a button to see anything at all.
        if (ShockerCatalog.shouldLoadFor(account.apiBaseUrl, account.apiToken)) {
            ShockerCatalog.refresh(account.apiBaseUrl, account.apiToken, account.ignoreCertificateErrors) {
                // Only take over a screen the user is still sitting on and has not started editing
                // - rebuilding it throws pending changes away.
                val current = McCompat.currentScreen
                if (current === screen && current is YACLScreen && !current.pendingChanges()) {
                    McCompat.setScreen(create(parent))
                }
            }
        }

        return screen
    }

    private fun createBuilder(
        defaults: ShockCraftConfig,
        config: ShockCraftConfig,
        accountDefaults: AccountConfig,
        account: AccountConfig,
        parent: Screen?,
        builder: YetAnotherConfigLib.Builder
    ): YetAnotherConfigLib.Builder {
        return builder
            .title(Component.literal("ShockCraft - OpenShock Minecraft Integration"))

            // Setup first: a token and a shocker are the two things nothing else works without.
            // After that the tabs run in the order somebody actually thinks about them - what
            // shocks me, which damage counts, who else may, and what any of it looks like.
            .category(setupCategory(accountDefaults, account, parent))
            .category(triggerCategory(defaults, config))
            .category(damageFilterCategory(defaults, config))
            .category(remoteCategory(accountDefaults, account))
            .category(effectsCategory(defaults, config, accountDefaults, account))
    }

    /**
     * What shocks you, and how hard: one group per thing the mod watches happen to you.
     *
     * The damage filter is a tab of its own rather than a group down here. It is a mode switch,
     * nine checkboxes and every damage type the world has, which is more than these four triggers
     * put together, and it used to bury them.
     */
    private fun triggerCategory(defaults: ShockCraftConfig, config: ShockCraftConfig): ConfigCategory {
        return ConfigCategory.createBuilder()
            .name(Component.literal("Triggers"))

            .group(OptionGroup.createBuilder()
                .name(Component.literal("On Damage"))
                .description(OptionDescription.of(Component.literal("Settings for shocking on damage")))

                .option(Option.createBuilder<Boolean>()
                    .name(Component.literal("Enabled"))
                    .description(OptionDescription.of(Component.literal("Enable shocking on damage")))
                    .controller { TickBoxControllerBuilder.create(it) }
                    .binding(defaults.onDamage, { config.onDamage }, { config.onDamage = it })
                    .build()
                )
                .option(Option.createBuilder<DamageShockMode>()
                    .name(Component.literal("On Damage Action"))
                    .description(
                        OptionDescription.of(
                            Component.literal(
                                "Defines what happens when you receive damage.\n" +
                                        "Low Hp = You get shocked at higher intensity the less HP you have\n" +
                                        "Damage Amount = You get shocked the amount of damage you have received"
                            )
                        )
                    )
                    .controller {
                        EnumControllerBuilder.create(it).enumClass(DamageShockMode::class.java)
                    }
                    .binding(defaults.damageMode, { config.damageMode }, { config.damageMode = it })
                    .build()
                )
                .option(Option.createBuilder<Int>()
                    .name(Component.literal("Minimum Intensity"))
                    .controller { option: Option<Int> ->
                        IntegerSliderControllerBuilder.create(option)
                            .range(1, 100)
                            .step(1)
                    }
                    .binding(
                        defaults.intensityMin.toInt(),
                        { config.intensityMin.toInt() },
                        { config.intensityMin = it.toByte() })
                    .build()
                )
                .option(Option.createBuilder<Int>()
                    .name(Component.literal("Maximum Intensity"))
                    .controller { option: Option<Int> ->
                        IntegerSliderControllerBuilder.create(option)
                            .range(1, 100)
                            .step(1)
                    }
                    .binding(
                        defaults.intensityMax.toInt(),
                        { config.intensityMax.toInt() },
                        { config.intensityMax = it.toByte() })
                    .build()
                )

                .option(Option.createBuilder<Int>()
                    .name(Component.literal("Damage Threshold"))
                    .description(OptionDescription.of(Component.literal("How much damage you need to take, or have until a shock is sent")))
                    .controller { option: Option<Int> ->
                        IntegerSliderControllerBuilder.create(option)
                            .range(1, 20)
                            .step(1)
                    }
                    .binding(
                        defaults.damageThreshold.toInt(),
                        { config.damageThreshold.toInt() },
                        { config.damageThreshold = it.toUInt() })
                    .build()
                )

                .option(Option.createBuilder<Int>()
                    .name(Component.literal("Cooldown"))
                    .description(OptionDescription.of(Component.literal("Cooldown between on damage shocks")))
                    .controller { option ->
                        IntegerSliderControllerBuilder.create(option)
                            .range(300, 60_000)
                            .step(100).formatValue { Component.literal((it / 1000f).toString() + " seconds") }
                    }
                    .binding(
                        defaults.cooldown.toInt(),
                        { config.cooldown.toInt() },
                        { config.cooldown = it.toUShort() })
                    .build()
                )

                .build()
            )

            .group(OptionGroup.createBuilder()
                .name(Component.literal("On Death"))
                .description(OptionDescription.of(Component.literal("Defines what happens when you die")))

                .option(Option.createBuilder<Boolean>()
                    .name(Component.literal("Enabled"))
                    .description(OptionDescription.of(Component.literal("Enable shocking on death")))
                    .controller { TickBoxControllerBuilder.create(it) }
                    .binding(defaults.onDeath, { config.onDeath }, { config.onDeath = it })
                    .build()
                )
                .option(Option.createBuilder<Int>()
                    .name(Component.literal("Intensity"))
                    .controller { option ->
                        IntegerSliderControllerBuilder.create(option)
                            .range(1, 100)
                            .step(1)
                    }
                    .binding(
                        defaults.onDeathIntensity.toInt(),
                        { config.onDeathIntensity.toInt() },
                        { config.onDeathIntensity = it.toByte() })
                    .build()
                )
                .option(Option.createBuilder<Int>()
                    .name(Component.literal("Duration"))
                    .controller { option ->
                        IntegerSliderControllerBuilder.create(option)
                            .range(300, 30_000)
                            .step(100).formatValue { Component.literal((it / 1000f).toString() + " seconds") }
                    }
                    .binding(
                        defaults.onDeathDuration.toInt(),
                        { config.onDeathDuration.toInt() },
                        { config.onDeathDuration = it.toUShort() })
                    .build()
                )

                .build()
            )

            .group(OptionGroup.createBuilder()
                .name(Component.literal("On Level Up"))
                .description(OptionDescription.of(Component.literal("Defines what happens when you gain an XP level")))

                .option(Option.createBuilder<Boolean>()
                    .name(Component.literal("Enabled"))
                    .description(OptionDescription.of(Component.literal("Enable shocking on level up")))
                    .controller { TickBoxControllerBuilder.create(it) }
                    .binding(defaults.onLevelUp, { config.onLevelUp }, { config.onLevelUp = it })
                    .build()
                )
                .option(Option.createBuilder<Int>()
                    .name(Component.literal("Intensity"))
                    .controller { option ->
                        IntegerSliderControllerBuilder.create(option)
                            .range(1, 100)
                            .step(1)
                    }
                    .binding(
                        defaults.onLevelUpIntensity.toInt(),
                        { config.onLevelUpIntensity.toInt() },
                        { config.onLevelUpIntensity = it.toByte() })
                    .build()
                )
                .option(Option.createBuilder<Int>()
                    .name(Component.literal("Duration"))
                    .controller { option ->
                        IntegerSliderControllerBuilder.create(option)
                            .range(300, 30_000)
                            .step(100).formatValue { Component.literal((it / 1000f).toString() + " seconds") }
                    }
                    .binding(
                        defaults.onLevelUpDuration.toInt(),
                        { config.onLevelUpDuration.toInt() },
                        { config.onLevelUpDuration = it.toUShort() })
                    .build()
                )

                .build()
            )

            .group(OptionGroup.createBuilder()
                .name(Component.literal("On Chat Message"))
                .description(OptionDescription.of(Component.literal("Defines what happens when a specific chat phrase is sent or received")))

                .option(Option.createBuilder<Boolean>()
                    .name(Component.literal("Enable for Chat Messages"))
                    .description(OptionDescription.of(Component.literal("Enable shocking when a message with the key phrase is sent/received")))
                    .controller { TickBoxControllerBuilder.create(it) }
                    .binding(defaults.onChatEvent, { config.onChatEvent }, { config.onChatEvent = it })
                    .build()
                )
                .option(Option.createBuilder<String>()
                    .name(Component.literal("Key Phrase"))
                    .description(OptionDescription.of(Component.literal("The phrase to trigger the shock")))
                    .controller { StringControllerBuilder.create(it) }
                    .binding(defaults.chatMessagePhrase, { config.chatMessagePhrase }, { config.chatMessagePhrase = it })
                    .build()
                )
                .option(Option.createBuilder<Int>()
                    .name(Component.literal("Intensity"))
                    .controller { option ->
                        IntegerSliderControllerBuilder.create(option)
                            .range(1, 100)
                            .step(1)
                    }
                    .binding(
                        defaults.onChatMessageIntensity.toInt(),
                        { config.onChatMessageIntensity.toInt() },
                        { config.onChatMessageIntensity = it.toByte() })
                    .build()
                )
                .option(Option.createBuilder<Int>()
                    .name(Component.literal("Duration"))
                    .controller { option ->
                        IntegerSliderControllerBuilder.create(option)
                            .range(300, 30_000)
                            .step(100).formatValue { Component.literal((it / 1000f).toString() + " seconds") }
                    }
                    .binding(
                        defaults.onChatMessageDuration.toInt(),
                        { config.onChatMessageDuration.toInt() },
                        { config.onChatMessageDuration = it.toUShort() })
                    .build()
                )

                .build()
            )

            .build()
    }

    /**
     * What other players may do to you, and which collars you have agreed to.
     *
     * Its own tab rather than the bottom of Setup. Setup is a URL and a token - things filled in
     * once and forgotten; this is consent, and it should not sit where you have to scroll past the
     * plumbing to find it.
     */
    private fun remoteCategory(accountDefaults: AccountConfig, account: AccountConfig): ConfigCategory {
        return ConfigCategory.createBuilder()
            .name(Component.literal("Remote Control"))
            .group(remotePermissionsGroup(accountDefaults, account))
            .group(remoteLimitsGroup(accountDefaults, account))
            .group(armedCollarsGroup(account))
            .build()
    }

    /**
     * What a shock looks and sounds like, split by who gets it.
     *
     * That line is the only one worth splitting these on. The first group is drawn by this client
     * and goes nowhere; the second costs a packet upwards saying a shock got through, which is the
     * only way anybody else learns that it did. It is also why the second group lives in
     * [AccountConfig] with the remote caps while the first is per instance - see
     * [openshock.integrations.minecraft.platform.ShockedPayload].
     */
    private fun effectsCategory(
        defaults: ShockCraftConfig,
        config: ShockCraftConfig,
        accountDefaults: AccountConfig,
        account: AccountConfig,
    ): ConfigCategory {
        return ConfigCategory.createBuilder()
            .name(Component.literal("Effects"))

            .group(OptionGroup.createBuilder()
                .name(Component.literal("On Your Screen"))
                .description(OptionDescription.of(Component.literal(
                    "Drawn by your own client, for you.\n\n" +
                            "Nothing here is sent anywhere, so nobody else can tell it is happening"
                )))

                .option(Option.createBuilder<Boolean>()
                    .name(Component.literal("Display Shocks in Action Bar"))
                    .description(OptionDescription.of(Component.literal("Displays Shocks or all kinds of commands in the action bar on your screen")))
                    .controller { TickBoxControllerBuilder.create(it) }
                    .binding(defaults.displayShocksInActionBar, { config.displayShocksInActionBar }, { config.displayShocksInActionBar = it })
                    .build()
                )

                .option(Option.createBuilder<Boolean>()
                    .name(Component.literal("Lightning Overlay"))
                    .description(OptionDescription.of(Component.literal(
                        "Crawls lightning around the edge of your screen while a shock is running.\n\n" +
                                "Shocks only - a vibrate or a beep never paints over your screen. The flash and " +
                                "the flicker follow Minecraft own Distortion Effects slider, so turning that " +
                                "down leaves the lightning steady rather than gone"
                    )))
                    .controller { TickBoxControllerBuilder.create(it) }
                    .binding(defaults.shockScreenOverlay, { config.shockScreenOverlay }, { config.shockScreenOverlay = it })
                    .build()
                ).build()
            )

            .group(OptionGroup.createBuilder()
                .name(Component.literal("Around You"))
                .description(OptionDescription.of(Component.literal(
                    "What the room gets when a shock lands on you.\n\n" +
                            "Drawing any of it costs a packet upwards saying a shock got through, which is the " +
                            "only way a remote holder could ever learn that it did. With both of these off, " +
                            "nothing is sent at all and a shock is between you and your own client"
                )))

                .option(Option.createBuilder<Boolean>()
                    .name(Component.literal("Show Shock Particles"))
                    .description(OptionDescription.of(Component.literal(
                        "Draw sparks around you when a shock lands, for everyone nearby to see"
                    )))
                    .controller { TickBoxControllerBuilder.create(it) }
                    .binding(
                        accountDefaults.showEffectParticles,
                        { account.showEffectParticles },
                        { account.showEffectParticles = it })
                    .build()
                )

                .option(Option.createBuilder<Boolean>()
                    .name(Component.literal("Play Shock Crackle"))
                    .description(OptionDescription.of(Component.literal(
                        "Play a crackle when a shock lands, for everyone nearby to hear"
                    )))
                    .controller { TickBoxControllerBuilder.create(it) }
                    .binding(
                        accountDefaults.showEffectSounds,
                        { account.showEffectSounds },
                        { account.showEffectSounds = it })
                    .build()
                )

                .option(ButtonOption.createBuilder()
                    .name(Component.literal("Preview on me"))
                    .description(OptionDescription.of(Component.literal(
                        "Draws the arcs and plays the crackle on you, without shocking anything.\n\n" +
                                "Nothing is sent to OpenShock and no shocker runs - this is only the packet " +
                                "that tells the server to draw, which is the same one a real shock sends. " +
                                "Needs a world, and a server with the mod on it"
                    )))
                    .action { screen, _ ->
                        // Applied and saved first, for the same reason the reload button does it:
                        // the tick boxes above decide what the preview is allowed to draw, and a
                        // value that has only been clicked is still pending.
                        OptionUtils.forEachOptions(screen.config) { it.applyValue() }
                        screen.config.saveFunction().run()

                        McCompat.closeScreen()
                        NetClient.sendShocked(
                            RemoteMode.Shock,
                            PREVIEW_INTENSITY,
                            PREVIEW_DURATION,
                            account.showEffectParticles,
                            account.showEffectSounds,
                        )
                    }
                    .build()
                ).build()
            )

            .build()
    }

    /**
     * Which damage is allowed to shock you: a mode switch, the nine [DamageCategory] checkboxes,
     * and the exact damage type picker the mode can hand over to.
     *
     * On Damage only. Dying is decided by the On Death settings over in Triggers however this is
     * set, which is the one thing worth knowing before touching any of it.
     *
     * Both lists are always on screen, and the mode greys out whichever one is not in charge -
     * built as a listener rather than by leaving options out, because YACL fixes the option list
     * when the screen is created and the mode can be changed after that.
     *
     * Every checkbox setter only adds or removes its own entry, the same trick the shocker picker
     * uses: the order YACL applies them in cannot matter, and an entry this version does not
     * recognise rides along untouched instead of being dropped.
     */
    private fun damageFilterCategory(defaults: ShockCraftConfig, config: ShockCraftConfig): ConfigCategory {
        val categoryOptions = DamageCategory.entries.map { category ->
            Option.createBuilder<Boolean>()
                .name(Component.literal(category.displayName))
                .description(OptionDescription.of(Component.literal(category.help)))
                .controller { TickBoxControllerBuilder.create(it) }
                .available(config.damageFilterMode == DamageFilterMode.Categories)
                .binding(
                    category in defaults.damageCategories,
                    { category in config.damageCategories },
                    { checked ->
                        val without = config.damageCategories - category
                        config.damageCategories = if (checked) without + category else without
                    })
                .build()
        }

        // Only a loaded world knows its damage types, so from the title screen there is nothing to
        // list and the manual entries below are the only way in.
        val available = McCompat.damageTypeIds()
        val known = available.toSet()

        val selected = LinkedHashSet(config.damageTypes.filter { it in known })
        var manual: List<String> = config.damageTypes.filterNot { it in known }

        fun commit() {
            config.damageTypes = (selected + manual).distinct()
        }

        val byTypeAvailable = config.damageFilterMode == DamageFilterMode.DamageTypes

        val typeOptions = available.map { id ->
            Option.createBuilder<Boolean>()
                .name(Component.literal(DamageFilter.shortName(id)))
                .description(OptionDescription.of(Component.literal(id)))
                .controller { TickBoxControllerBuilder.create(it) }
                .available(byTypeAvailable)
                .binding(
                    false,
                    { id in selected },
                    { checked ->
                        if (checked) selected.add(id) else selected.remove(id)
                        commit()
                    })
                .build()
        }

        val manualTypes = ListOption.createBuilder<String>()
            .name(Component.literal("Damage Type IDs (manual)"))
            .description(OptionDescription.of(Component.literal(
                "Damage type ids entered by hand, used on top of the ones ticked above.\n\n" +
                        "Only needed for a type this world does not have, or when the settings are opened from the title screen where there is no world to ask"
            )))
            .controller { StringControllerBuilder.create(it) }
            .available(byTypeAvailable)
            .binding(
                emptyList(),
                { manual },
                {
                    manual = it
                    commit()
                })
            .initial("minecraft:cactus")
            .collapsed(available.isNotEmpty() && manual.isEmpty())
            .build()

        val mode = Option.createBuilder<DamageFilterMode>()
            .name(Component.literal("Filter by"))
            .description(OptionDescription.of(Component.literal(
                "Which list decides whether damage may shock you.\n\n" +
                        "Categories = the nine buckets below, every kind of damage falls into exactly one of them\n" +
                        "Exact damage types = the ids further down, for when the buckets are not fine grained enough"
            )))
            .controller { EnumControllerBuilder.create(it).enumClass(DamageFilterMode::class.java) }
            .binding(defaults.damageFilterMode, { config.damageFilterMode }, { config.damageFilterMode = it })
            .listener { _, value ->
                categoryOptions.forEach { it.setAvailable(value == DamageFilterMode.Categories) }
                (typeOptions + manualTypes).forEach { it.setAvailable(value == DamageFilterMode.DamageTypes) }
            }
            .build()

        val categories = OptionGroup.createBuilder()
            .name(Component.literal("Categories"))
            .description(OptionDescription.of(Component.literal(
                "The nine buckets every kind of damage falls into, exactly one each.\n\n" +
                        "Used when Filter by is set to Categories"
            )))
            .options(categoryOptions)
            .build()

        val exact = OptionGroup.createBuilder()
            .name(Component.literal("Exact Damage Types"))
            .description(OptionDescription.of(Component.literal(
                "Every damage type this world has, including the ones datapacks and other mods added.\n\n" +
                        "Used when Filter by is set to exact damage types"
            )))
            .collapsed(true)
            .apply {
                // Either branch, never both: YACL rejects an empty collection outright, so on the
                // title screen - where there is no world and therefore no damage type registry -
                // options(typeOptions) would throw before the label above could soften it.
                if (available.isEmpty()) {
                    option(LabelOption.create(Component.literal("Join a world to list its damage types")))
                } else {
                    options(typeOptions)
                }
            }
            .build()

        // Filter by sits on the category itself rather than inside a group, because a group can
        // be collapsed and this is the control that explains why one of the two lists below is
        // greyed out. Folded away with the list it governs, it would look like a bug.
        //
        // A ListOption is its own group, so the manual entries go in beside the other two.
        return ConfigCategory.createBuilder()
            .name(Component.literal("Damage Filter"))
            .option(mode)
            .group(categories)
            .group(exact)
            .group(manualTypes)
            .build()
    }

    /**
     * Backend, credentials and the shocker picker.
     *
     * [AccountConfig.shockers] is one flat list of IDs, but two controls edit it here: a checkbox
     * per shocker the API told us about, and the manual list for everything else. Both write the
     * union of the two working copies below, so it does not matter which order YACL applies them
     * in, and a shocker that only exists in one of them cannot be lost by the other.
     */
    private fun setupCategory(
        accountDefaults: AccountConfig,
        account: AccountConfig,
        parent: Screen?
    ): ConfigCategory {
        val available = ShockerCatalog.shockers
        val known = available.mapTo(HashSet()) { it.id }

        val selected = LinkedHashSet(account.shockers.filter { it in known })
        var manual: List<String> = account.shockers.filterNot { it in known }

        fun commit() {
            account.shockers = (selected + manual).distinct()
        }

        val shockers = OptionGroup.createBuilder()
            .name(Component.literal("Shockers"))
            .description(OptionDescription.of(Component.literal(
                "The shockers ShockCraft is allowed to control.\n\n" +
                        "Tick the ones you want to use. The list is everything your API token can reach, " +
                        "so fill in the server settings above first"
            )))

        statusLine(account, available)?.let { shockers.option(LabelOption.create(Component.literal(it))) }

        shockers.option(
            ButtonOption.createBuilder()
                .name(Component.literal(if (ShockerCatalog.loading) "Loading shockers..." else "Reload shockers from OpenShock"))
                .description(OptionDescription.of(Component.literal(
                    "Asks the backend which shockers your token can control.\n\n" +
                            "This saves the screen first - the token you just typed is what it has to log in with"
                )))
                .available(!ShockerCatalog.loading)
                .action { screen, button ->
                    button.setAvailable(false)

                    // Apply and save before fetching: a value that is only pending has not reached
                    // AccountConfig yet, and the rebuilt screen below would drop it either way.
                    OptionUtils.forEachOptions(screen.config) { it.applyValue() }
                    screen.config.saveFunction().run()

                    ShockerCatalog.refresh(account.apiBaseUrl, account.apiToken, account.ignoreCertificateErrors) {
                        // Rebuilt rather than updated in place, because YACL fixes the list of
                        // options when the screen is created. Skipped if the user walked away in
                        // the meantime, so a slow backend cannot pull them back into the settings.
                        if (McCompat.currentScreen === screen) McCompat.setScreen(create(parent))
                    }
                }
                .build()
        )

        for (shocker in available) {
            shockers.option(
                Option.createBuilder<Boolean>()
                    .name(Component.literal(shocker.label))
                    .description(OptionDescription.of(Component.literal(shocker.details)))
                    .controller { TickBoxControllerBuilder.create(it) }
                    .binding(
                        false,
                        { shocker.id in selected },
                        { checked ->
                            if (checked) selected.add(shocker.id) else selected.remove(shocker.id)
                            commit()
                        })
                    .build()
            )
        }

        return ConfigCategory.createBuilder()
            .name(Component.literal("Setup"))

            .group(OptionGroup.createBuilder()
                .name(Component.literal("Server"))
                .description(OptionDescription.of(Component.literal(
                    "Server / OpenShock Backend Settings and Shocker Setup\n\n" +
                            "These are stored per user rather than per instance, at\n" +
                            AccountConfig.path + "\n" +
                            "so your API token is not copied along when you share or export this instance"
                )))
                .option(
                    Option.createBuilder<String>()
                        .name(Component.literal("API URL"))
                        .description(OptionDescription.of(Component.literal("The API base URL of the OpenShock Backend. For the official instance this is https://api.openshock.app")))
                        .controller { option: Option<String>? -> StringControllerBuilder.create(option) }
                        .binding(
                            accountDefaults.apiBaseUrl,
                            { account.apiBaseUrl },
                            { account.apiBaseUrl = it })
                        .build()
                )
                // The token itself is deliberately not on this screen - see [ApiTokenScreen].
                // All that is said here is whether there is one.
                .option(LabelOption.create(Component.literal(
                    if (account.apiToken.isBlank()) "API Token: not set" else "API Token: set"
                )))
                .option(
                    ButtonOption.createBuilder()
                        .name(Component.literal(
                            if (account.apiToken.isBlank()) "Set API Token" else "Change API Token"
                        ))
                        .description(OptionDescription.of(Component.literal(
                            "Opens a box to paste your token into. Generate one on the web - it needs " +
                                    "shocker use permission.\n\n" +
                                    "It is entered on a screen of its own and never shown on this one, because " +
                                    "anyone who has it can shock you and this is the screen most likely to end " +
                                    "up on camera"
                        )))
                        .action { screen, _ ->
                            // Apply and save before leaving, the same as the reload button below:
                            // coming back rebuilds this screen, which would drop anything still
                            // pending on it.
                            OptionUtils.forEachOptions(screen.config) { it.applyValue() }
                            screen.config.saveFunction().run()

                            McCompat.setScreen(ApiTokenScreen(parent))
                        }
                        .build()
                )
                .option(
                    Option.createBuilder<Boolean>()
                        .name(Component.literal("Ignore Certificate Errors"))
                        .description(OptionDescription.of(Component.literal(
                            "Accept TLS certificates that are not signed by a trusted CA, e.g. a self-signed certificate on a self hosted backend.\n" +
                                    "Only enable this if you know what you are doing - it disables a security check and lets anyone on your network read or modify your API token"
                        )))
                        .controller { TickBoxControllerBuilder.create(it) }
                        .binding(
                            accountDefaults.ignoreCertificateErrors,
                            { account.ignoreCertificateErrors },
                            { account.ignoreCertificateErrors = it })
                        .build()
                ).build()
            )

            .group(shockers.build())

            // The escape hatch for anything the picker cannot offer: a backend that is unreachable
            // right now, or a shocker the listing endpoints do not return.
            .group(ListOption.createBuilder<String>()
                .name(Component.literal("Shocker IDs (manual)"))
                .description(OptionDescription.of(Component.literal(
                    "Shocker IDs entered by hand, used on top of the ones ticked above.\n\n" +
                            "Only needed if the list above cannot show a shocker you want to use"
                )))
                .controller { option: Option<String> -> StringControllerBuilder.create(option) }
                .binding(
                    accountDefaults.shockers,
                    { manual },
                    {
                        manual = it
                        commit()
                    })
                .initial("Put your Shocker ID here")
                .collapsed(available.isNotEmpty() && manual.isEmpty())
                .build()
            )

            .build()
    }

    /**
     * Which kinds of press another player may make at all.
     *
     * The master switch and one tick box per mode, because a remote can be set to shock, buzz or
     * beep and those are not the same thing to agree to - accepting a buzz from your friends
     * should not be how you end up accepting a shock from them.
     *
     * How hard any of it may be is [remoteLimitsGroup]; whether it reaches a particular collar at
     * all is [armedCollarsGroup].
     */
    private fun remotePermissionsGroup(accountDefaults: AccountConfig, account: AccountConfig): OptionGroup {
        return OptionGroup.createBuilder()
            .name(Component.literal("Permissions"))
            .description(OptionDescription.of(Component.literal(
                "Whether other players may reach you, and with what.\n\n" +
                        "Your API token never leaves this machine - a remote only sends a request, and what " +
                        "you set here decides whether it turns into anything. Nothing on this tab is set by a " +
                        "modpack: like the token it is stored per user, so an instance someone hands you cannot " +
                        "arrive with any of it already switched on"
            )))

            .option(Option.createBuilder<Boolean>()
                .name(Component.literal("Allow Remote Control"))
                .description(OptionDescription.of(Component.literal(
                    "Let remotes you have accepted shock you.\n\n" +
                            "With this off, nothing another player does can reach your shockers"
                )))
                .controller { TickBoxControllerBuilder.create(it) }
                .binding(
                    accountDefaults.allowRemoteControl,
                    { account.allowRemoteControl },
                    { account.allowRemoteControl = it })
                .build()
            )

            .option(Option.createBuilder<Boolean>()
                .name(Component.literal("Allow Shock"))
                .description(OptionDescription.of(Component.literal(
                    "Let remotes set to Shock reach you.\n\n" +
                            "A remote can be set to shock, buzz or beep, and those are not the same thing to " +
                            "agree to - turn off the ones you do not want"
                )))
                .controller { TickBoxControllerBuilder.create(it) }
                .binding(
                    accountDefaults.allowRemoteShock,
                    { account.allowRemoteShock },
                    { account.allowRemoteShock = it })
                .build()
            )

            .option(Option.createBuilder<Boolean>()
                .name(Component.literal("Allow Vibrate"))
                .description(OptionDescription.of(Component.literal("Let remotes set to Vibrate buzz you")))
                .controller { TickBoxControllerBuilder.create(it) }
                .binding(
                    accountDefaults.allowRemoteVibrate,
                    { account.allowRemoteVibrate },
                    { account.allowRemoteVibrate = it })
                .build()
            )

            .option(Option.createBuilder<Boolean>()
                .name(Component.literal("Allow Sound"))
                .description(OptionDescription.of(Component.literal("Let remotes set to Sound beep at you")))
                .controller { TickBoxControllerBuilder.create(it) }
                .binding(
                    accountDefaults.allowRemoteSound,
                    { account.allowRemoteSound },
                    { account.allowRemoteSound = it })
                .build()
            )

            .build()
    }

    /**
     * How hard, how long and how often, whatever a remote asks for.
     *
     * One set of numbers for all three modes on purpose: they bound how much shocker runs, and
     * that means the same thing whether it comes out as a jolt, a buzz or a beep. What differs
     * between modes is only whether you want them, which is [remotePermissionsGroup].
     *
     * The ceiling is testable from here, which is the point of the button at the bottom: press it
     * and feel the strongest thing a remote could ever do to you, before handing one to anybody.
     */
    @OptIn(DelicateCoroutinesApi::class)
    private fun remoteLimitsGroup(accountDefaults: AccountConfig, account: AccountConfig): OptionGroup {
        return OptionGroup.createBuilder()
            .name(Component.literal("Limits"))
            .description(OptionDescription.of(Component.literal(
                "The ceiling every remote press is clamped to, however hard, long or often it asks"
            )))

            .option(Option.createBuilder<Int>()
                .name(Component.literal("Maximum Intensity"))
                .description(OptionDescription.of(Component.literal("The strongest a remote may shock you, however hard it asks for")))
                .controller { option ->
                    IntegerSliderControllerBuilder.create(option)
                        .range(1, 100)
                        .step(1)
                }
                .binding(
                    accountDefaults.remoteMaxIntensity.toInt(),
                    { account.remoteMaxIntensity.toInt() },
                    { account.remoteMaxIntensity = it.toByte() })
                .build()
            )

            .option(Option.createBuilder<Int>()
                .name(Component.literal("Maximum Duration"))
                .description(OptionDescription.of(Component.literal("The longest a remote may shock you, however long it asks for")))
                .controller { option ->
                    IntegerSliderControllerBuilder.create(option)
                        .range(300, 30_000)
                        .step(100).formatValue { Component.literal((it / 1000f).toString() + " seconds") }
                }
                .binding(
                    accountDefaults.remoteMaxDuration.toInt(),
                    { account.remoteMaxDuration.toInt() },
                    { account.remoteMaxDuration = it.toUShort() })
                .build()
            )

            .option(Option.createBuilder<Int>()
                .name(Component.literal("Cooldown"))
                .description(OptionDescription.of(Component.literal("Shortest time between two remote shocks, however often the remote is pressed")))
                .controller { option ->
                    IntegerSliderControllerBuilder.create(option)
                        .range(300, 60_000)
                        .step(100).formatValue { Component.literal((it / 1000f).toString() + " seconds") }
                }
                .binding(
                    accountDefaults.remoteCooldown.toInt(),
                    { account.remoteCooldown.toInt() },
                    { account.remoteCooldown = it.toUShort() })
                .build()
            )

            .option(ButtonOption.createBuilder()
                .name(Component.literal("Test the ceiling"))
                .description(OptionDescription.of(Component.literal(
                    "Shocks you once at exactly the limits above, so you know what the worst case feels like.\n\n" +
                            "Saves the screen first, so it tests the numbers you can see"
                )))
                .available(account.shockers.isNotEmpty())
                .action { screen, _ ->
                    // Apply first: a slider that has only been dragged is still a pending value,
                    // and testing a limit other than the one on screen would be worse than useless.
                    OptionUtils.forEachOptions(screen.config) { it.applyValue() }
                    screen.config.saveFunction().run()

                    GlobalScope.launch { RemoteControl.test() }
                }
                .build()
            )

            .build()
    }

    /**
     * The collars you have agreed to, which is where permission actually lives now - the links
     * themselves are on the item. Unticking one disarms it: the collar stays on your leg and keeps
     * working as a pair of leggings, and every remote pointed at it stops.
     *
     * Its own group beside the caps rather than tacked onto the end of them, because it answers a
     * different question. The caps are how hard; this is whether at all.
     */
    private fun armedCollarsGroup(account: AccountConfig): OptionGroup {
        val group = OptionGroup.createBuilder()
            .name(Component.literal("Armed Collars"))
            .description(OptionDescription.of(Component.literal(
                "A collar has to be ticked here before anything can be shocked through it, whoever is " +
                        "holding the remote.\n\n" +
                        "You arm one by agreeing when you put it on, and taking it off disarms it again"
            )))

        val armed = account.armedCollars
        if (armed.isEmpty()) {
            group.option(LabelOption.create(Component.literal("No collars armed")))
        }

        for (collarId in armed) {
            group.option(
                Option.createBuilder<Boolean>()
                    .name(Component.literal("Collar ${shortCode(collarId)}"))
                    .description(OptionDescription.of(Component.literal(
                        "Untick to disarm this collar. Taking it off does the same thing.\n\n" +
                            "Copies of a collar share its id, so this covers all of them.\n\n" + collarId
                    )))
                    .controller { TickBoxControllerBuilder.create(it) }
                    .binding(
                        // Default false, so YACL's reset disarms rather than re-arms. There is no
                        // correct default for "may this collar shock me" - only a safe one.
                        false,
                        { collarId in account.armedCollars },
                        { allowed ->
                            val without = account.armedCollars.filterNot { it == collarId }
                            account.armedCollars = if (allowed) without + collarId else without
                        })
                    .build()
            )
        }

        return group.build()
    }

    /** What to say above the picker when it has nothing useful to show, or null when it has. */
    private fun statusLine(account: AccountConfig, available: List<Shocker>): String? = when {
        ShockerCatalog.loading -> "Loading your shockers..."
        ShockerCatalog.error != null -> "Could not load your shockers: ${ShockerCatalog.error}"
        available.isNotEmpty() -> null
        account.apiToken.isBlank() -> "Enter your API token above, then save or reload to pick your shockers"
        else -> "This account has no shockers on it"
    }

    /** Checkbox label: the shocker's name, prefixed with the owner when it is a shared one. */
    private val Shocker.label: String
        get() = buildString {
            if (owner != null) append(owner).append(" - ")
            append(name)
            if (paused) append(" (paused)")
        }

    private val Shocker.details: String
        get() = buildString {
            append("Hub: ").append(hub).append('\n')
            append(id)
            if (paused) append("\n\nThis shocker is paused, so the backend ignores anything sent to it")
        }
}
