package openshock.integrations.minecraft.platform

//? if >=1.21.4 {
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import openshock.integrations.minecraft.ShockCraft

/**
 * Client to server: set what the remote in my hand asks for.
 *
 * The second thing in this mod that travels *up* the wire, and unlike [ShockedPayload] it is not a
 * statement about anybody - it is a player editing an item they are holding. The settings live on
 * the item (see [openshock.integrations.minecraft.content.RemoteSettings]) because a remote is a
 * thing you can hand over and what it asks for should go with it, and only the server may write a
 * component onto a held stack. So the edit has to be asked for from here.
 *
 * It carries no collar id and no target. Which remote is being edited is whichever one the sending
 * player is holding, read from their own connection on the far side, so this cannot reach anybody
 * else's item however it is written.
 *
 * Nothing in it is trusted. The mode is resolved by name and falls back to Shock, the numbers are
 * clamped to the range the backend accepts, and none of it can raise a ceiling: the caps that
 * decide what a press turns into belong to the wearer and are applied on the wearer's own machine.
 * The worst a hand-written client can do here is set its own remote to something it could have set
 * through the screen anyway.
 */
data class RemoteConfigPayload(
    /** Which hand the remote is in, so the far side never has to be told which item to write to. */
    val mainHand: Boolean,
    val mode: String,
    val intensity: Int,
    val duration: Int,
) : CustomPacketPayload {

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

    companion object {
        val TYPE: CustomPacketPayload.Type<RemoteConfigPayload> =
            CustomPacketPayload.Type(McCompat.identifier(ShockCraft.MOD_ID, "remote_config"))

        // Hand-written like the other two, so the wire format stays readable at a glance.
        val CODEC: StreamCodec<RegistryFriendlyByteBuf, RemoteConfigPayload> = StreamCodec.of(
            { buffer, payload ->
                buffer.writeBoolean(payload.mainHand)
                buffer.writeUtf(payload.mode)
                buffer.writeVarInt(payload.intensity)
                buffer.writeVarInt(payload.duration)
            },
            { buffer ->
                RemoteConfigPayload(
                    buffer.readBoolean(),
                    buffer.readUtf(),
                    buffer.readVarInt(),
                    buffer.readVarInt(),
                )
            },
        )
    }
}
//?}
