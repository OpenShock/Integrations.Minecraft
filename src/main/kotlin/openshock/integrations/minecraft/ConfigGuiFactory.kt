package openshock.integrations.minecraft

import com.terraformersmc.modmenu.api.ConfigScreenFactory
import dev.isxander.yacl3.api.ConfigCategory
import dev.isxander.yacl3.api.ListOption
import dev.isxander.yacl3.api.Option
import dev.isxander.yacl3.api.OptionDescription
import dev.isxander.yacl3.api.OptionGroup
import dev.isxander.yacl3.api.YetAnotherConfigLib
import dev.isxander.yacl3.api.controller.IntegerSliderControllerBuilder
import dev.isxander.yacl3.api.controller.StringControllerBuilder
import dev.isxander.yacl3.gui.controllers.slider.FloatSliderController
import dev.isxander.yacl3.gui.controllers.slider.IntegerSliderController
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.screen.Screen
import net.minecraft.text.Text

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

        return yacl.generateScreen(MinecraftClient.getInstance().currentScreen)
    }

    fun createBuilder(
        defaults: ShockCraftConfig,
        config: ShockCraftConfig,
        builder: YetAnotherConfigLib.Builder
    ): YetAnotherConfigLib.Builder {
        return builder
            .title(Text.literal("OpenShock"))


            .category(
                ConfigCategory.createBuilder()
                    .name(Text.literal("ShockCraft - OpenShock Config"))

                    // Server group
                    .group(OptionGroup.createBuilder()
                        .name(Text.literal("Server"))
                        .description(OptionDescription.of(Text.literal("Server / OpenShock Backend Settings")))
                        .option(
                            Option.createBuilder<String>()
                                .name(Text.literal("API URL"))
                                .description(OptionDescription.of(Text.literal("The API base URL of the OpenShock Backend. For the official instance this is https://api.openshock.org")))
                                .controller { option: Option<String>? -> StringControllerBuilder.create(option) }
                                .binding(
                                    defaults.apiBaseUrl,
                                    { config.apiBaseUrl },
                                    { newVal: String -> config.apiBaseUrl = newVal })
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
                                    { newVal: String -> config.apiToken = newVal })
                                .build()
                        ).build()
                    )

                    // Shocker group
                    .group(OptionGroup.createBuilder()
                        .name(Text.literal("Shocking Options"))

                        .option(Option.createBuilder<Int>()
                            .name(Text.literal("Minimum Intensity"))
                            .controller { option: Option<Int> ->
                                IntegerSliderControllerBuilder.create(option)
                                    .range(1, 100)
                                    .step(1).formatValue { it -> Text.literal("$it minimum") }
                            }
                            .binding(
                                defaults.intensityMin,
                                { config.intensityMin },
                                { newVal: Int -> config.intensityMin = newVal })
                            .build()
                        )


                        .option(Option.createBuilder<Int>()
                            .name(Text.literal("Maximum Intensity"))
                            .controller { option: Option<Int> ->
                                IntegerSliderControllerBuilder.create(option)
                                    .range(1, 100)
                                    .step(1).formatValue { it -> Text.literal("$it maximum") }
                            }
                            .binding(
                                defaults.intensityMax,
                                { config.intensityMax },
                                { newVal: Int -> config.intensityMax = newVal })
                            .build()
                        )
                        .build()
                    )

                    .group(ListOption.createBuilder<String>()
                        .name(Text.literal("Shockers"))
                        .controller { option: Option<String> -> StringControllerBuilder.create(option) }
                        .binding(
                            defaults.shockers,
                            { config.shockers },
                            { newVal: List<String> -> config.shockers = newVal })
                        .initial("Put your Shocker ID here")
                        .build()
                    )

                    .build()
            )
    }
}
