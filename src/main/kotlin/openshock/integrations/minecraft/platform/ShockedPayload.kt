package openshock.integrations.minecraft.platform

//? if >=1.21 {
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import openshock.integrations.minecraft.ShockCraft

/**
 * Client to server: a shock just landed on me, draw it.
 *
 * The only thing in this mod that travels *up* the wire and says something about a person.
 * [RemoteConfigPayload] goes the same way but is only a player editing an item in their own hand;
 * this one is sent after the OpenShock API has accepted a shock, so unlike everything else here it
 * is a statement of fact rather than a request - which is exactly why it is worth being careful
 * about, and why the wearer decides whether it is sent at all.
 *
 * It says a shock happened and how big it was, and nothing about what caused it: a mob, a fall, a
 * level-up and somebody's remote all look identical on the wire. What a shock is *for* stays on
 * the machine that decided it, the same way [RemoteFirePayload] carries no sender.
 *
 * Sending it does tell the room that a shock got through - which is a thing a remote holder could
 * otherwise never learn, because [openshock.integrations.minecraft.RemoteControl] deliberately
 * never answers. That is the trade this packet makes to put the effect on screen.
 *
 * [intensity] and [duration] are only ever particle dressing. The server clamps both before it
 * draws anything, so the worst a hand-written client can do with them is ask for a slightly bigger
 * puff of sparks around itself.
 *
 * Only exists from 1.21 on, like the rest of the channel - see [Net].
 */
data class ShockedPayload(
    /**
     * Which kind of control landed, by [openshock.integrations.minecraft.api.RemoteMode] name.
     *
     * Carried so the room can tell a jolt from a buzz from a beep. An unreadable name resolves to
     * Shock, which is the only thing this packet ever reported before the other modes existed.
     */
    val mode: String,
    val intensity: Int,
    val duration: Int,
    /**
     * Which effects the person being shocked wants the room to get.
     *
     * The wearer's own choice, carried rather than assumed, because this packet is the only reason
     * anybody else learns a shock landed at all. Someone who wants the sparks but not the noise -
     * or neither - says so here, and a client that wants neither does not send the packet at all.
     */
    val particles: Boolean,
    val sound: Boolean,
) : CustomPacketPayload {

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

    companion object {
        val TYPE: CustomPacketPayload.Type<ShockedPayload> =
            CustomPacketPayload.Type(McCompat.identifier(ShockCraft.MOD_ID, "shocked"))

        // Hand-written for the same reason RemoteFirePayload's is: five fields is not worth the
        // generic gymnastics, and the wire format stays readable at a glance.
        val CODEC: StreamCodec<RegistryFriendlyByteBuf, ShockedPayload> = StreamCodec.of(
            { buffer, payload ->
                buffer.writeUtf(payload.mode)
                buffer.writeVarInt(payload.intensity)
                buffer.writeVarInt(payload.duration)
                buffer.writeBoolean(payload.particles)
                buffer.writeBoolean(payload.sound)
            },
            { buffer ->
                ShockedPayload(
                    buffer.readUtf(),
                    buffer.readVarInt(),
                    buffer.readVarInt(),
                    buffer.readBoolean(),
                    buffer.readBoolean(),
                )
            },
        )
    }
}
//?}
