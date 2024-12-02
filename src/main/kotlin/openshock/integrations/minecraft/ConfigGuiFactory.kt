package openshock.integrations.minecraft

import com.terraformersmc.modmenu.api.ConfigScreenFactory
import dev.isxander.yacl3.api.ConfigCategory
import dev.isxander.yacl3.api.ListOption
import dev.isxander.yacl3.api.Option
import dev.isxander.yacl3.api.OptionDescription
import dev.isxander.yacl3.api.OptionGroup
import dev.isxander.yacl3.api.YetAnotherConfigLib
import dev.isxander.yacl3.api.controller.*
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.screen.Screen
import net.minecraft.text.Text
import openshock.integrations.minecraft.config.DamageShockMode
import openshock.integrations.minecraft.config.ShockCraftConfig

object ConfigGuiFactory : ConfigScreenFactory<Screen> {

    override fun create(parent: Screen): Screen {
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
            .title(Text.literal("ShockCraft - OpenShock Minecraft Integration"))
            
            .category(
                ConfigCategory.createBuilder()
                    .name(Text.literal("Behaviour / Shock Settings"))
                    
                    .group(OptionGroup.createBuilder()
                        .name(Text.literal("General"))
                        .description(OptionDescription.of(Text.literal("General settings for the mod")))
                        
                        .option(Option.createBuilder<Boolean>()
                            .name(Text.literal("Display Shocks in Action Bar"))
                            .description(OptionDescription.of(Text.literal("Displays Shocks or all kinds of commands in the action bar on your screen")))
                            .controller { TickBoxControllerBuilder.create(it) }
                            .binding(defaults.displayShocksInActionBar, { config.displayShocksInActionBar }, { config.displayShocksInActionBar = it })
                            .build()
                        ).build()
                    )

                    .group(OptionGroup.createBuilder()
                        .name(Text.literal("On Damage"))
                        .description(OptionDescription.of(Text.literal("Settings for shocking on damage")))

                        .option(Option.createBuilder<Boolean>()
                            .name(Text.literal("Enabled"))
                            .description(OptionDescription.of(Text.literal("Enable shocking on damage")))
                            .controller { TickBoxControllerBuilder.create(it) }
                            .binding(defaults.onDamage, { config.onDamage }, { config.onDamage = it })
                            .build()
                        )
                        .option(Option.createBuilder<DamageShockMode>()
                            .name(Text.literal("On Damage Action"))
                            .description(
                                OptionDescription.of(
                                    Text.literal(
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
                            .name(Text.literal("Minimum Intensity"))
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
                            .name(Text.literal("Maximum Intensity"))
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
                            .name(Text.literal("Damage Threshold"))
                            .description(OptionDescription.of(Text.literal("How much damage you need to take, or have until a shock is sent")))
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
                            .name(Text.literal("Cooldown"))
                            .description(OptionDescription.of(Text.literal("Cooldown between on damage shocks")))
                            .controller { option ->
                                IntegerSliderControllerBuilder.create(option)
                                    .range(300, 60_000)
                                    .step(100).formatValue { Text.literal((it / 1000f).toString() + " seconds") }
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
                        .name(Text.literal("On Death"))
                        .description(OptionDescription.of(Text.literal("Defines what happens when you die")))

                        .option(Option.createBuilder<Boolean>()
                            .name(Text.literal("Enabled"))
                            .description(OptionDescription.of(Text.literal("Enable shocking on death")))
                            .controller { TickBoxControllerBuilder.create(it) }
                            .binding(defaults.onDeath, { config.onDeath }, { config.onDeath = it })
                            .build()
                        )
                        .option(Option.createBuilder<Int>()
                            .name(Text.literal("Intensity"))
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
                            .name(Text.literal("Duration"))
                            .controller { option ->
                                IntegerSliderControllerBuilder.create(option)
                                    .range(300, 30_000)
                                    .step(100).formatValue { Text.literal((it / 1000f).toString() + " seconds") }
                            }
                            .binding(
                                defaults.onDeathDuration.toInt(),
                                { config.onDeathDuration.toInt() },
                                { config.onDeathDuration = it.toUShort() })
                            .build()
                        )

                        .build()
                    )

                    .build()
            )

            .category(
                ConfigCategory.createBuilder()
                    .name(Text.literal("Setup"))

                    // Server group
                    .group(OptionGroup.createBuilder()
                        .name(Text.literal("Server"))
                        .description(OptionDescription.of(Text.literal("Server / OpenShock Backend Settings and Shocker Setup")))
                        .option(
                            Option.createBuilder<String>()
                                .name(Text.literal("API URL"))
                                .description(OptionDescription.of(Text.literal("The API base URL of the OpenShock Backend. For the official instance this is https://api.openshock.app")))
                                .controller { option: Option<String>? -> StringControllerBuilder.create(option) }
                                .binding(
                                    defaults.apiBaseUrl,
                                    { config.apiBaseUrl },
                                    { config.apiBaseUrl = it })
                                .build()
                        )
                        .option(
                            Option.createBuilder<String>()
                                .name(Text.literal("API Token"))
                                .description(OptionDescription.of(Text.literal("API Token generated on the web, needs shocker use permission")))
                                .controller { option: Option<String> -> StringControllerBuilder.create(option) }
                                .binding(
                                    defaults.apiToken,
                                    { config.apiToken },
                                    { config.apiToken = it })
                                .build()
                        ).build()
                    )

                    // Shocker group

                    .group(ListOption.createBuilder<String>()
                        .name(Text.literal("Shockers"))
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
