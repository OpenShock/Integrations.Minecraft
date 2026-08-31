package openshock.integrations.minecraft.platform

//? if fabric {
//? if >=1.21 {
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import openshock.integrations.minecraft.RemoteControl

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
                payload.intensity.toByte(),
                payload.duration.toUShort(),
            )
        }
    }
}
//?} else {
/*/** Stub for 1.20.4, which has no channel to receive on. See [Net]. */
object NetClient {
    fun init() {}
}
*///?}
//?} elif neoforge {
/*//? if >=1.21 {
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import openshock.integrations.minecraft.RemoteControl

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
                payload.intensity.toByte(),
                payload.duration.toUShort(),
            )
        }
    }
}
//?} else {
/*/** Stub for 1.20.4, which has no channel to receive on. See [Net]. */
object NetClient {
    fun init() {}
}
*///?}
*///?}
