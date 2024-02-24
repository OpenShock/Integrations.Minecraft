package openshock.integrations.minecraft

import com.terraformersmc.modmenu.api.ConfigScreenFactory
import dev.isxander.yacl3.api.ConfigCategory
import dev.isxander.yacl3.api.Option
import dev.isxander.yacl3.api.YetAnotherConfigLib
import dev.isxander.yacl3.api.controller.StringControllerBuilder
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.screen.Screen
import net.minecraft.text.Text

object ModMenuEntryFactory : ConfigScreenFactory<Screen> {

    override fun create(parent: Screen): Screen {
        val yacl = YetAnotherConfigLib.create(ShockCraftConfig.HANDLER) {
            defaults: ShockCraftConfig, config: ShockCraftConfig, builder: YetAnotherConfigLib.Builder -> createBuilder(defaults, config, builder) }

        return yacl.generateScreen(MinecraftClient.getInstance().currentScreen)
    }

    fun createBuilder(defaults: ShockCraftConfig, config: ShockCraftConfig, builder: YetAnotherConfigLib.Builder): YetAnotherConfigLib.Builder {
        return builder
            .title(Text.literal("OpenShock"))
            .category(
                ConfigCategory.createBuilder()
                    .name(Text.literal("Server"))
                    .option(
                        Option.createBuilder<String>()
                            .name(Text.literal("API Url"))
                            .controller { option: Option<String>? -> StringControllerBuilder.create(option) }
                            .binding(defaults.apiBaseUrl, { config.apiBaseUrl }, { newVal: String -> config.apiBaseUrl = newVal })
                            .build()
                    )
                    .build()
            )
    }
}
