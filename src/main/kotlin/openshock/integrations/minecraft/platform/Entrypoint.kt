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
/*import net.neoforged.fml.ModLoadingContext
import net.neoforged.fml.common.Mod
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent
import net.neoforged.neoforge.common.NeoForge
//? if >=1.21 {
import net.neoforged.api.distmarker.Dist
import net.neoforged.neoforge.client.event.ClientTickEvent
import net.neoforged.neoforge.client.gui.IConfigScreenFactory
//?} else {
/*import net.neoforged.api.distmarker.Dist
import net.neoforged.fml.loading.FMLEnvironment
import net.neoforged.neoforge.client.ConfigScreenHandler
import net.neoforged.neoforge.event.TickEvent
*///?}
import openshock.integrations.minecraft.ConfigScreen
import openshock.integrations.minecraft.ShockCraft

// Kotlin for Forge ("kotlinforforge" modLoader in neoforge.mods.toml) expects an object
// declaration. @Mod only gained its `dist` element after 1.20.4, so that target registers for both
// sides; the mod stays client-only there via the `side = "CLIENT"` entries in neoforge.mods.toml.
//? if >=1.21 {
@Mod(value = ShockCraft.MOD_ID, dist = [Dist.CLIENT])
//?} else {
/*@Mod(ShockCraft.MOD_ID)
*///?}
object NeoForgeEntrypoint {
    init {
        //? if >=1.21 {
        ClientBootstrap.run()
        //?} else {
        /*// @Mod only gained its `dist` element after 1.20.4, so the entrypoint is constructed on
        // dedicated servers too. Everything below touches client-only APIs, so keep it in a
        // separate class that a server never loads.
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ClientBootstrap.run()
        }
        *///?}
    }
}

private object ClientBootstrap {
    fun run() {
        ShockCraft.init()

        // 1.21 replaced ConfigScreenHandler.ConfigScreenFactory with IConfigScreenFactory.
        //? if >=1.21 {
        ModLoadingContext.get().registerExtensionPoint(IConfigScreenFactory::class.java) {
            IConfigScreenFactory { _, parent -> ConfigScreen.create(parent) }
        }
        //?} else {
        /*ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory::class.java) {
            ConfigScreenHandler.ConfigScreenFactory { _, parent -> ConfigScreen.create(parent) }
        }
        *///?}

        // 1.21 split the phase-based TickEvent.ClientTickEvent into ClientTickEvent.Pre/Post.
        //? if >=1.21 {
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post::class.java) {
            ShockCraft.onClientTick()
        }
        //?} else {
        /*NeoForge.EVENT_BUS.addListener(TickEvent.ClientTickEvent::class.java) { event ->
            if (event.phase == TickEvent.Phase.END) ShockCraft.onClientTick()
        }
        *///?}

        NeoForge.EVENT_BUS.addListener(ClientChatReceivedEvent::class.java) { event ->
            ShockCraft.onChatMessage(event.message.string)
        }
    }
}
*///?}
