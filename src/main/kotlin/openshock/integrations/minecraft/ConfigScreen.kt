package openshock.integrations.minecraft

import dev.isxander.yacl3.api.*
import dev.isxander.yacl3.api.controller.EnumControllerBuilder
import dev.isxander.yacl3.api.controller.IntegerSliderControllerBuilder
import dev.isxander.yacl3.api.controller.StringControllerBuilder
import dev.isxander.yacl3.api.controller.TickBoxControllerBuilder
import dev.isxander.yacl3.api.utils.OptionUtils
import dev.isxander.yacl3.gui.YACLScreen
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import openshock.integrations.minecraft.api.Shocker
import openshock.integrations.minecraft.api.ShockerCatalog
import openshock.integrations.minecraft.config.AccountConfig
import openshock.integrations.minecraft.config.DamageCategory
import openshock.integrations.minecraft.config.DamageFilterMode
import openshock.integrations.minecraft.config.DamageShockMode
import openshock.integrations.minecraft.config.ShockCraftConfig
import openshock.integrations.minecraft.platform.McCompat

/**
 * Builds the YACL settings screen. Loader-agnostic: Fabric reaches it through Mod Menu and
 * NeoForge through IConfigScreenFactory, both in [openshock.integrations.minecraft.platform].
 */
object ConfigScreen {

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

            .category(
                ConfigCategory.createBuilder()
                    .name(Component.literal("Behaviour / Shock Settings"))

                    .group(OptionGroup.createBuilder()
                        .name(Component.literal("General"))
                        .description(OptionDescription.of(Component.literal("General settings for the mod")))

                        .option(Option.createBuilder<Boolean>()
                            .name(Component.literal("Display Shocks in Action Bar"))
                            .description(OptionDescription.of(Component.literal("Displays Shocks or all kinds of commands in the action bar on your screen")))
                            .controller { TickBoxControllerBuilder.create(it) }
                            .binding(defaults.displayShocksInActionBar, { config.displayShocksInActionBar }, { config.displayShocksInActionBar = it })
                            .build()
                        ).build()
                    )

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

                    .groups(damageFilterGroups(defaults, config))

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
            )

            .category(setupCategory(accountDefaults, account, parent))
    }

    /**
     * The damage filter: a mode switch, the nine [DamageCategory] checkboxes, and the exact damage
     * type picker the mode can hand over to.
     *
     * Both lists are always on screen, and the mode greys out whichever one is not in charge -
     * built as a listener rather than by leaving options out, because YACL fixes the option list
     * when the screen is created and the mode can be changed after that.
     *
     * Every checkbox setter only adds or removes its own entry, the same trick the shocker picker
     * uses: the order YACL applies them in cannot matter, and an entry this version does not
     * recognise rides along untouched instead of being dropped.
     */
    private fun damageFilterGroups(defaults: ShockCraftConfig, config: ShockCraftConfig): List<OptionGroup> {
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
                .name(Component.literal(id.removePrefix("minecraft:")))
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
            .name(Component.literal("Damage Types"))
            .description(OptionDescription.of(Component.literal(
                "Which damage is allowed to shock you.\n\n" +
                        "This filters On Damage only - dying is still up to the On Death settings"
            )))
            .option(mode)
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
                if (available.isEmpty()) {
                    option(LabelOption.create(Component.literal("Join a world to list its damage types")))
                }
            }
            .options(typeOptions)
            .build()

        // A ListOption is its own group, so it goes into the category alongside the other two.
        return listOf(categories, exact, manualTypes)
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
                .option(
                    Option.createBuilder<String>()
                        .name(Component.literal("API Token"))
                        .description(OptionDescription.of(Component.literal("API Token generated on the web, needs shocker use permission")))
                        .controller { option: Option<String> -> StringControllerBuilder.create(option) }
                        .binding(
                            accountDefaults.apiToken,
                            { account.apiToken },
                            { account.apiToken = it })
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
