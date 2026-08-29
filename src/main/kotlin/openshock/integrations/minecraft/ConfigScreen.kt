package openshock.integrations.minecraft

import dev.isxander.yacl3.api.*
import dev.isxander.yacl3.api.controller.EnumControllerBuilder
import dev.isxander.yacl3.api.controller.IntegerSliderControllerBuilder
import dev.isxander.yacl3.api.controller.StringControllerBuilder
import dev.isxander.yacl3.api.controller.TickBoxControllerBuilder
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import openshock.integrations.minecraft.config.DamageShockMode
import openshock.integrations.minecraft.config.ShockCraftConfig

/**
 * Builds the YACL settings screen. Loader-agnostic: Fabric reaches it through Mod Menu and
 * NeoForge through IConfigScreenFactory, both in [openshock.integrations.minecraft.platform].
 */
object ConfigScreen {

    fun create(parent: Screen?): Screen {
        val yacl =
            YetAnotherConfigLib.create(ShockCraftConfig.HANDLER) { defaults: ShockCraftConfig, config: ShockCraftConfig, builder: YetAnotherConfigLib.Builder ->
                createBuilder(
                    defaults,
                    config,
                    builder
                )
            }

        return yacl.generateScreen(parent)
    }

    private fun createBuilder(
        defaults: ShockCraftConfig,
        config: ShockCraftConfig,
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

            .category(
                ConfigCategory.createBuilder()
                    .name(Component.literal("Setup"))

                    // Server group
                    .group(OptionGroup.createBuilder()
                        .name(Component.literal("Server"))
                        .description(OptionDescription.of(Component.literal("Server / OpenShock Backend Settings and Shocker Setup")))
                        .option(
                            Option.createBuilder<String>()
                                .name(Component.literal("API URL"))
                                .description(OptionDescription.of(Component.literal("The API base URL of the OpenShock Backend. For the official instance this is https://api.openshock.app")))
                                .controller { option: Option<String>? -> StringControllerBuilder.create(option) }
                                .binding(
                                    defaults.apiBaseUrl,
                                    { config.apiBaseUrl },
                                    { config.apiBaseUrl = it })
                                .build()
                        )
                        .option(
                            Option.createBuilder<String>()
                                .name(Component.literal("API Token"))
                                .description(OptionDescription.of(Component.literal("API Token generated on the web, needs shocker use permission")))
                                .controller { option: Option<String> -> StringControllerBuilder.create(option) }
                                .binding(
                                    defaults.apiToken,
                                    { config.apiToken },
                                    { config.apiToken = it })
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
                                    defaults.ignoreCertificateErrors,
                                    { config.ignoreCertificateErrors },
                                    { config.ignoreCertificateErrors = it })
                                .build()
                        ).build()
                    )

                    // Shocker group

                    .group(ListOption.createBuilder<String>()
                        .name(Component.literal("Shockers"))
                        .controller { option: Option<String> -> StringControllerBuilder.create(option) }
                        .binding(
                            defaults.shockers,
                            { config.shockers },
                            { config.shockers = it })
                        .initial("Put your Shocker ID here")
                        .build()
                    )

                    .build()
            )
    }
}
