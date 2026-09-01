package openshock.integrations.minecraft.platform

//? if fabric {
//? if >=1.21 {
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.client.Minecraft
import net.minecraft.world.InteractionHand
import openshock.integrations.minecraft.RemoteControl
import openshock.integrations.minecraft.api.RemoteMode

/**
 * The client half of the channel: where a fire packet lands.
 *
 * Kept apart from [Net] so a dedicated server never loads a class that mentions a client-only API,
 * which is the same reason `ClientBootstrap` exists in [Entrypoint].
 *
 * The handler decides nothing. It hands the request to [RemoteControl], which runs on this machine
 * and checks it against the collar on this player's head and the settings only they can change.
 */
object NetClient {

    fun init() {
        ClientPlayNetworking.registerGlobalReceiver(RemoteFirePayload.TYPE) { payload, _ ->
            receiveFire(payload)
        }

    }

    @OptIn(DelicateCoroutinesApi::class)
    fun receiveFire(payload: RemoteFirePayload) {
        // Off the handler thread: the gate ends in an HTTP call to OpenShock, and blocking here
        // would stall the connection.
        GlobalScope.launch {
            RemoteControl.onRemoteFired(
                payload.collarId,
                RemoteMode.byName(payload.mode),
                payload.intensity.toByte(),
                payload.duration.toUShort(),
            )
        }
    }

    /**
     * Tells the server a shock just landed, so it can draw sparks everyone nearby can see.
     *
     * The one thing this mod ever sends upwards - see [ShockedPayload] for what that costs. It is
     * best effort in both directions: a server without the mod cannot receive it, and nothing here
     * waits to find out whether it arrived.
     */
    fun sendShocked(mode: RemoteMode, intensity: Byte, duration: UShort, particles: Boolean, sound: Boolean) {
        // Hopped onto the client thread: this is called from the coroutine that just finished the
        // HTTP call to OpenShock, and packets go out where the connection lives.
        Minecraft.getInstance().execute {
            if (!ClientPlayNetworking.canSend(ShockedPayload.TYPE)) return@execute
            ClientPlayNetworking.send(
                ShockedPayload(mode.name, intensity.toInt(), duration.toInt(), particles, sound)
            )
        }
    }

    /**
     * Sends what the player just set their held remote to, from the screen or the scroll gesture.
     *
     * Best effort like everything else on this channel: a server without the mod cannot receive
     * it, and the remote simply keeps the settings it had.
     */
    //? if >=1.21.5 {
    fun sendRemoteConfig(hand: InteractionHand, mode: RemoteMode, intensity: Int, duration: Int) {
        Minecraft.getInstance().execute {
            if (!ClientPlayNetworking.canSend(RemoteConfigPayload.TYPE)) return@execute
            ClientPlayNetworking.send(
                RemoteConfigPayload(hand == InteractionHand.MAIN_HAND, mode.name, intensity, duration)
            )
        }
    }
    //?}
}
//?} else {
/*/** Stub for 1.20.4, which has no channel to receive on. See [Net]. */
object NetClient {
    fun init() {}
    fun sendShocked(mode: openshock.integrations.minecraft.api.RemoteMode, intensity: Byte, duration: UShort, particles: Boolean, sound: Boolean) {}
}
*///?}
//?} elif neoforge {
/*//? if >=1.21 {
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import net.minecraft.client.Minecraft
import net.minecraft.world.InteractionHand
import openshock.integrations.minecraft.RemoteControl
import openshock.integrations.minecraft.api.RemoteMode
// PacketDistributor lost sendToServer in 1.21.11, which split the client-only half of it out into
// ClientPacketDistributor.
//? if >=1.21.11 {
import net.neoforged.neoforge.client.network.ClientPacketDistributor
//?} else {
/*import net.neoforged.neoforge.network.PacketDistributor
*///?}

/**
 * The client half of the channel: what to do with an arriving packet.
 *
 * NeoForge registers handlers alongside the payload types in [Net.init], which runs on both sides,
 * so unlike Fabric there is nothing to register here. What this object buys instead is the
 * separation itself: [Net] names it but never runs it, so on a dedicated server this class - and
 * through it [RemoteControl], the config and YACL, none of which a server should ever load - is
 * never resolved.
 */
object NetClient {

    /** Fabric registers its receiver here; NeoForge already did it in [Net.init]. */
    fun init() {}

    @OptIn(DelicateCoroutinesApi::class)
    fun receiveFire(payload: RemoteFirePayload) {
        // Off the handler thread: the gate ends in an HTTP call to OpenShock, and blocking here
        // would stall the connection.
        GlobalScope.launch {
            RemoteControl.onRemoteFired(
                payload.collarId,
                RemoteMode.byName(payload.mode),
                payload.intensity.toByte(),
                payload.duration.toUShort(),
            )
        }
    }

    /**
     * Tells the server a shock just landed, so it can draw sparks everyone nearby can see.
     *
     * The one thing this mod ever sends upwards - see [ShockedPayload] for what that costs. It is
     * best effort in both directions: a server without the mod cannot receive it, and nothing here
     * waits to find out whether it arrived.
     */
    fun sendShocked(mode: RemoteMode, intensity: Byte, duration: UShort, particles: Boolean, sound: Boolean) {
        // Hopped onto the client thread: this is called from the coroutine that just finished the
        // HTTP call to OpenShock, and packets go out where the connection lives.
        Minecraft.getInstance().execute {
            val connection = Minecraft.getInstance().connection ?: return@execute
            if (!connection.hasChannel(ShockedPayload.TYPE)) return@execute

            val payload = ShockedPayload(mode.name, intensity.toInt(), duration.toInt(), particles, sound)

            //? if >=1.21.11 {
            ClientPacketDistributor.sendToServer(payload)
            //?} else {
            /*PacketDistributor.sendToServer(payload)
            *///?}
        }
    }

    /**
     * Sends what the player just set their held remote to, from the screen or the scroll gesture.
     *
     * Best effort like everything else on this channel: a server without the mod cannot receive
     * it, and the remote simply keeps the settings it had.
     */
    //? if >=1.21.5 {
    fun sendRemoteConfig(hand: InteractionHand, mode: RemoteMode, intensity: Int, duration: Int) {
        Minecraft.getInstance().execute {
            val connection = Minecraft.getInstance().connection ?: return@execute
            if (!connection.hasChannel(RemoteConfigPayload.TYPE)) return@execute

            val payload =
                RemoteConfigPayload(hand == InteractionHand.MAIN_HAND, mode.name, intensity, duration)

            //? if >=1.21.11 {
            ClientPacketDistributor.sendToServer(payload)
            //?} else {
            /*PacketDistributor.sendToServer(payload)
            *///?}
        }
    }
    //?}
}
//?} else {
/*/** Stub for 1.20.4, which has no channel to receive on. See [Net]. */
object NetClient {
    fun init() {}
    fun sendShocked(mode: openshock.integrations.minecraft.api.RemoteMode, intensity: Byte, duration: UShort, particles: Boolean, sound: Boolean) {}
}
*///?}
*///?}
