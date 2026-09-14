package openshock.integrations.minecraft.platform

//? if fabric {
import com.terraformersmc.modmenu.api.ConfigScreenFactory
import com.terraformersmc.modmenu.api.ModMenuApi
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import net.minecraft.client.gui.screens.Screen
import org.slf4j.LoggerFactory
import openshock.integrations.minecraft.ConfigScreen
import openshock.integrations.minecraft.ShockCraft
//? if >=1.21 {
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import openshock.integrations.minecraft.ShockEffects
//?}
//? if >=1.21.4 {
import openshock.integrations.minecraft.RemoteScreen
import openshock.integrations.minecraft.content.RemoteScreens
//?}

/**
 * Runs on both sides, and on a dedicated server it is the only thing that runs at all.
 *
 * It must never touch the config: [ShockCraft.init] loads it through YACL, and the whole point of
 * the split is that a server holds no token, no caps and no shocker list. Anything a server does
 * belongs here; everything else belongs in [FabricEntrypoint].
 */
class FabricCommonEntrypoint : ModInitializer {
    override fun onInitialize() {
        // The only sign of life a dedicated server gets: nothing else here logs, because nothing
        // else here does anything until a player asks it to. Without this, "is it even loaded" and
        // "is it loaded but broken" look identical from the console.
        // MOD_ID is a const, so naming it does not drag the client-heavy ShockCraft object in.
        LoggerFactory.getLogger(ShockCraft.MOD_ID)
            .info("ShockCraft loaded; remote channel {}", if (Net.SUPPORTED) "registered" else "unavailable")

        // Registering the payload type is what makes a server *offer* the remote channel, which is
        // what a client's canSend check later sees. Both sides have to do it.
        Net.init()

        // Items too: a stack cannot cross between sides unless both know what it is.
        Content.init()

        // Drives the sparks around anyone being shocked. Left out below 1.21, where there is no
        // channel for a client to report a shock on and so nothing that could ever be crackling.
        //? if >=1.21 {
        ServerTickEvents.END_SERVER_TICK.register(
            ServerTickEvents.EndTick { server -> ShockEffects.tick(server) }
        )
        //?}
    }
}

class FabricEntrypoint : ClientModInitializer {
    override fun onInitializeClient() {
        ShockCraft.init()
        NetClient.init()
        Content.initClient()

        // Handed in rather than reached for: RemoteItem runs on a dedicated server too, so it
        // must never name the screen it opens. See RemoteScreens.
        //? if >=1.21.4 {
        RemoteScreens.opener = { stack, hand -> McCompat.setScreen(RemoteScreen(stack, hand)) }
        //?}

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
import net.neoforged.fml.loading.FMLEnvironment
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent
import net.neoforged.neoforge.common.NeoForge
//? if >=1.21 {
import net.neoforged.neoforge.client.event.ClientTickEvent
import net.neoforged.neoforge.client.gui.IConfigScreenFactory
import net.neoforged.neoforge.event.tick.ServerTickEvent
import openshock.integrations.minecraft.ShockEffects
//?} else {
/*import net.neoforged.neoforge.client.ConfigScreenHandler
import net.neoforged.neoforge.event.TickEvent
*///?}
import openshock.integrations.minecraft.ConfigScreen
import openshock.integrations.minecraft.ShockCraft
import org.slf4j.LoggerFactory
//? if >=1.21.4 {
import openshock.integrations.minecraft.RemoteScreen
import openshock.integrations.minecraft.content.RemoteScreens
//?}

// Kotlin for Forge ("kotlinforforge" modLoader in neoforge.mods.toml) expects an object
// declaration. The mod loads on dedicated servers now, so @Mod no longer restricts itself to
// Dist.CLIENT and the dist check moved into the body - which is also what 1.20.4 always did,
// since its @Mod had no `dist` element to restrict.
@Mod(ShockCraft.MOD_ID)
object NeoForgeEntrypoint {
    init {
        // FMLEnvironment's public `dist` field became a getDist() method in the loader that
        // shipped with 1.21.11.
        //? if >=1.21.11 {
        val onClient = FMLEnvironment.getDist() == Dist.CLIENT
        //?} else {
        /*val onClient = FMLEnvironment.dist == Dist.CLIENT
        *///?}

        // The only sign of life a dedicated server gets: nothing else here logs, because nothing
        // else here does anything until a player asks it to. Without this, "is it even loaded" and
        // "is it loaded but broken" look identical from the console.
        LoggerFactory.getLogger(ShockCraft.MOD_ID)
            .info("ShockCraft loaded; remote channel {}", if (Net.SUPPORTED) "registered" else "unavailable")

        // Everything client-only lives in a separate class so a dedicated server never loads it:
        // the config is YACL-backed, and a server has no business holding a token or caps.
        Net.init()

        // Items too: a stack cannot cross between sides unless both know what it is.
        Content.init()

        // Drives the sparks around anyone being shocked. Left out below 1.21, where there is no
        // channel for a client to report a shock on and so nothing that could ever be crackling.
        //? if >=1.21 {
        NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post::class.java) { event ->
            ShockEffects.tick(event.server)
        }
        //?}

        if (onClient) {
            ClientBootstrap.run()
        }
    }
}

private object ClientBootstrap {
    fun run() {
        ShockCraft.init()
        NetClient.init()
        Content.initClient()

        // Handed in rather than reached for: RemoteItem runs on a dedicated server too, so it
        // must never name the screen it opens. See RemoteScreens.
        //? if >=1.21.4 {
        RemoteScreens.opener = { stack, hand -> McCompat.setScreen(RemoteScreen(stack, hand)) }
        //?}

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
