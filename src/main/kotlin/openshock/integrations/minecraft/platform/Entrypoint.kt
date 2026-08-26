package openshock.integrations.minecraft.platform

//? if fabric {
import com.terraformersmc.modmenu.api.ConfigScreenFactory
import com.terraformersmc.modmenu.api.ModMenuApi
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import net.minecraft.client.gui.screens.Screen
import openshock.integrations.minecraft.ConfigScreen
import openshock.integrations.minecraft.ShockCraft

class FabricEntrypoint : ClientModInitializer {
    override fun onInitializeClient() {
        ShockCraft.init()

        ClientTickEvents.END_CLIENT_TICK.register(
            ClientTickEvents.EndTick { ShockCraft.onClientTick() }
        )

        ClientReceiveMessageEvents.CHAT.register(
            ClientReceiveMessageEvents.Chat { message, _, _, _, _ ->
                ShockCraft.onChatMessage(message.string)
            }
        )
    }
}

/** Adds the config button to Mod Menu's mod list. NeoForge does this through IConfigScreenFactory. */
class ModMenuEntrypoint : ModMenuApi {
    override fun getModConfigScreenFactory(): ConfigScreenFactory<*> =
        ConfigScreenFactory<Screen> { parent -> ConfigScreen.create(parent) }
}
//?} elif neoforge {
/*import net.neoforged.api.distmarker.Dist
import net.neoforged.fml.ModLoadingContext
import net.neoforged.fml.common.Mod
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent
import net.neoforged.neoforge.client.event.ClientTickEvent
import net.neoforged.neoforge.client.gui.IConfigScreenFactory
import net.neoforged.neoforge.common.NeoForge
import openshock.integrations.minecraft.ConfigScreen
import openshock.integrations.minecraft.ShockCraft

// Kotlin for Forge ("kotlinforforge" modLoader in neoforge.mods.toml) expects an object declaration.
@Mod(value = ShockCraft.MOD_ID, dist = [Dist.CLIENT])
object NeoForgeEntrypoint {
    init {
        ShockCraft.init()

        ModLoadingContext.get().registerExtensionPoint(IConfigScreenFactory::class.java) {
            IConfigScreenFactory { _, parent -> ConfigScreen.create(parent) }
        }

        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post::class.java) {
            ShockCraft.onClientTick()
        }

        NeoForge.EVENT_BUS.addListener(ClientChatReceivedEvent::class.java) { event ->
            ShockCraft.onChatMessage(event.message.string)
        }
    }
}
*///?}
