package openshock.integrations.minecraft.platform

import net.minecraft.server.level.ServerPlayer

//? if fabric {
//? if >=1.21 {
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import openshock.integrations.minecraft.ShockEffects
import openshock.integrations.minecraft.api.RemoteMode

/**
 * The server half of the channel: register the packets, ask whether a player can hear them, send
 * them.
 *
 * Runs on both sides - a payload type has to be registered by whoever might read or write it - so
 * nothing here may touch the client. The receiving end lives in [NetClient].
 *
 * [canReach] is the whole graceful-degradation story. A player on a client without the mod, or
 * with a version that has no channel, simply cannot be reached, and the caller does nothing rather
 * than failing. The loaders track this for us; there is no handshake of our own.
 */
object Net {

    /** Whether this build can speak the channel at all. False on 1.20.4. */
    const val SUPPORTED: Boolean = true

    fun init() {
        // Fabric API 6 (26.1) renamed playS2C/playC2S to clientboundPlay/serverboundPlay.
        //? if >=26.1 {
        val toClient = PayloadTypeRegistry.clientboundPlay()
        val toServer = PayloadTypeRegistry.serverboundPlay()
        //?} else {
        /*val toClient = PayloadTypeRegistry.playS2C()
        val toServer = PayloadTypeRegistry.playC2S()
        *///?}

        toClient.register(RemoteFirePayload.TYPE, RemoteFirePayload.CODEC)
        toServer.register(ShockedPayload.TYPE, ShockedPayload.CODEC)

        // Runs on the server thread, which is where the particles have to be spawned from anyway.
        // context.player() is who the connection belongs to, not anything the packet claimed, so
        // there is no way to draw sparks on somebody else.
        ServerPlayNetworking.registerGlobalReceiver(ShockedPayload.TYPE) { payload, context ->
            ShockEffects.onShocked(
                context.player(),
                RemoteMode.byName(payload.mode),
                payload.intensity,
                payload.duration,
                payload.particles,
                payload.sound,
            )
        }

        // Remotes only exist where the Equippable component does, and with no remote there is
        // nothing to configure.
        //? if >=1.21.4 {
        toServer.register(RemoteConfigPayload.TYPE, RemoteConfigPayload.CODEC)

        ServerPlayNetworking.registerGlobalReceiver(RemoteConfigPayload.TYPE) { payload, context ->
            RemoteConfig.receive(context.player(), payload)
        }
        //?}
    }

    fun canReach(player: ServerPlayer): Boolean =
        ServerPlayNetworking.canSend(player, RemoteFirePayload.TYPE)

    fun fire(player: ServerPlayer, collarId: String, mode: String, intensity: Int, duration: Int) {
        ServerPlayNetworking.send(player, RemoteFirePayload(collarId, mode, intensity, duration))
    }
}
//?} else {
/*/**
 * Stub for 1.20.4, which predates the codec-based payload API. Remotes are simply unavailable
 * there; everything the mod does on its own still works, because none of that touches the network.
 */
object Net {
    const val SUPPORTED: Boolean = false
    fun init() {}
    fun canReach(player: ServerPlayer): Boolean = false
    fun fire(player: ServerPlayer, collarId: String, mode: String, intensity: Int, duration: Int) {}
}
*///?}
//?} elif neoforge {
/*//? if >=1.21 {
import net.neoforged.fml.ModLoadingContext
import net.neoforged.neoforge.network.PacketDistributor
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent
import openshock.integrations.minecraft.ShockEffects
import openshock.integrations.minecraft.api.RemoteMode

/**
 * The server half of the channel. Same jobs as the Fabric version, done through a different set of
 * calls: NeoForge registers payloads from a mod-bus event rather than a static table, and the
 * handler goes in beside the type rather than separately.
 *
 * Runs on both sides, so nothing here may touch the client. The one client-shaped thing - what to
 * do with an arriving packet - is handed straight to [NetClient], whose body is the only part that
 * ever loads a client-only class, and only on a machine that runs the handler.
 */
object Net {

    /** Whether this build can speak the channel at all. False on 1.20.4. */
    const val SUPPORTED: Boolean = true

    fun init() {
        // The mod bus is what RegisterPayloadHandlersEvent is fired on, and Kotlin for Forge gives
        // an object declaration no constructor to receive it through - so it is fetched from the
        // container being loaded right now, which is ours. Identical from 1.21 through 26.2.
        val bus = ModLoadingContext.get().activeContainer.eventBus ?: return

        bus.addListener(RegisterPayloadHandlersEvent::class.java) { event ->
            event.registrar("1")
                // Optional is the whole point: without it, NeoForge refuses connections to anyone
                // who cannot speak this channel, which is every vanilla client on earth.
                .optional()
                .playToClient(RemoteFirePayload.TYPE, RemoteFirePayload.CODEC) { payload, _ ->
                    NetClient.receiveFire(payload)
                }
                // Handlers run on the main thread unless the registrar is told otherwise, which is
                // where the particles have to be spawned from anyway. context.player() is who the
                // connection belongs to, not anything the packet claimed, so there is no way to
                // draw sparks on somebody else - and on this side it is always a ServerPlayer.
                .playToServer(ShockedPayload.TYPE, ShockedPayload.CODEC) { payload, context ->
                    val player = context.player() as? ServerPlayer ?: return@playToServer
                    ShockEffects.onShocked(
                        player,
                        RemoteMode.byName(payload.mode),
                        payload.intensity,
                        payload.duration,
                        payload.particles,
                        payload.sound,
                    )
                }
                // Remotes only exist where the Equippable component does, and with no remote
                // there is nothing to configure.
                //? if >=1.21.4 {
                .playToServer(RemoteConfigPayload.TYPE, RemoteConfigPayload.CODEC) { payload, context ->
                    val player = context.player() as? ServerPlayer ?: return@playToServer
                    RemoteConfig.receive(player, payload)
                }
                //?}
        }
    }

    fun canReach(player: ServerPlayer): Boolean =
        player.connection.hasChannel(RemoteFirePayload.TYPE)

    fun fire(player: ServerPlayer, collarId: String, mode: String, intensity: Int, duration: Int) {
        PacketDistributor.sendToPlayer(player, RemoteFirePayload(collarId, mode, intensity, duration))
    }
}
//?} else {
/*/**
 * Stub for 1.20.4, which predates the codec-based payload API - the same gap Fabric has there.
 */
object Net {
    const val SUPPORTED: Boolean = false
    fun init() {}
    fun canReach(player: ServerPlayer): Boolean = false
    fun fire(player: ServerPlayer, collarId: String, mode: String, intensity: Int, duration: Int) {}
}
*///?}
*///?}
