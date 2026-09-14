package openshock.integrations.minecraft.platform

//? if >=1.21 {
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import openshock.integrations.minecraft.ShockCraft

/**
 * Server to client: a remote was pressed at you.
 *
 * It carries which collar was pressed, who pressed it, and what the remote asked for.
 *
 * The name is the one the server already holds for whoever used the remote, never anything the
 * pressing client offered, so nobody can put someone else's name on a shock. It is shown to the
 * wearer and filed with OpenShock as the control name; it gates nothing, and the wearer still
 * agreed to the collar rather than to a person.
 *
 * The link id is absent too: the server has already checked the pressing remote against the links
 * on the worn collar, so repeating it here would only be something to forge.
 *
 * There is no reply packet and there never should be. Whether a shock happened is between that
 * client and its own settings; sending back "switched off" or "not linked" would turn a remote
 * into a way to read them.
 *
 * Only exists from 1.21 on: 1.20.4 predates the codec-based payload API, and the mod reports the
 * channel as unavailable there rather than carrying a second wire format.
 */
data class RemoteFirePayload(
    val collarId: String,
    /**
     * Who pressed it, for the wearer to read.
     *
     * Display only, and treated as such on arrival - it is bounded and never matched against
     * anything. See [openshock.integrations.minecraft.RemoteControl], which decides whether the
     * shock happens from the collar on this player and their own settings, not from this.
     */
    val presser: String,
    /**
     * What the remote was set to, by [openshock.integrations.minecraft.api.RemoteMode] name.
     *
     * A request like the numbers beside it: the wearer has a separate switch per mode, so asking
     * for a vibrate does not mean one happens. An unreadable name resolves to Shock, which is what
     * every remote asked for before this field existed.
     */
    val mode: String,
    val intensity: Int,
    val duration: Int,
) : CustomPacketPayload {

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

    companion object {
        val TYPE: CustomPacketPayload.Type<RemoteFirePayload> =
            CustomPacketPayload.Type(McCompat.identifier(ShockCraft.MOD_ID, "remote_fire"))

        // Written out by hand rather than through StreamCodec.composite: five fields is not worth
        // the generic gymnastics, and this way the wire format is readable at a glance.
        val CODEC: StreamCodec<RegistryFriendlyByteBuf, RemoteFirePayload> = StreamCodec.of(
            { buffer, payload ->
                buffer.writeUtf(payload.collarId)
                buffer.writeUtf(payload.presser)
                buffer.writeUtf(payload.mode)
                buffer.writeVarInt(payload.intensity)
                buffer.writeVarInt(payload.duration)
            },
            { buffer ->
                RemoteFirePayload(
                    buffer.readUtf(),
                    buffer.readUtf(),
                    buffer.readUtf(),
                    buffer.readVarInt(),
                    buffer.readVarInt(),
                )
            },
        )
    }
}
//?}
