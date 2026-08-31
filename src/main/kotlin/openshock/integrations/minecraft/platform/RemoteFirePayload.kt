package openshock.integrations.minecraft.platform

//? if >=1.21 {
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import openshock.integrations.minecraft.ShockCraft

/**
 * Server to client: a remote was pressed at you.
 *
 * It carries which collar was pressed and what the remote asked for, and deliberately nothing
 * else. Who pressed it is absent because the wearer agreed to the collar rather than to a person,
 * and a name on the wire would be a name the sender chose.
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
    val intensity: Int,
    val duration: Int,
) : CustomPacketPayload {

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

    companion object {
        val TYPE: CustomPacketPayload.Type<RemoteFirePayload> =
            CustomPacketPayload.Type(McCompat.identifier(ShockCraft.MOD_ID, "remote_fire"))

        // Written out by hand rather than through StreamCodec.composite: three fields is not worth
        // the generic gymnastics, and this way the wire format is readable at a glance.
        val CODEC: StreamCodec<RegistryFriendlyByteBuf, RemoteFirePayload> = StreamCodec.of(
            { buffer, payload ->
                buffer.writeUtf(payload.collarId)
                buffer.writeVarInt(payload.intensity)
                buffer.writeVarInt(payload.duration)
            },
            { buffer -> RemoteFirePayload(buffer.readUtf(), buffer.readVarInt(), buffer.readVarInt()) },
        )
    }
}
//?}
