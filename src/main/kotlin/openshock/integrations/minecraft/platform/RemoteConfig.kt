package openshock.integrations.minecraft.platform

//? if >=1.21.4 {
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import openshock.integrations.minecraft.api.RemoteMode
import openshock.integrations.minecraft.content.ModContent
import openshock.integrations.minecraft.content.RemoteSettings

/**
 * Where a [RemoteConfigPayload] lands: the server writing a player's own edit onto their own item.
 *
 * The only thing that reads the packet, and it trusts none of it. Which item is being edited comes
 * from the connection the packet arrived on rather than from anything inside it, so this cannot
 * reach another player's remote however the packet is written; the mode is resolved by name with a
 * fallback, and the numbers are clamped by [RemoteSettings].
 *
 * Everything it can set is a *request* anyway. A remote asks, and the wearer's own machine decides
 * what that turns into - so the worst a hand-written client achieves here is setting its own
 * remote to something the screen would have set for it.
 */
object RemoteConfig {

    fun receive(player: ServerPlayer, payload: RemoteConfigPayload) {
        val hand = if (payload.mainHand) InteractionHand.MAIN_HAND else InteractionHand.OFF_HAND
        val stack = player.getItemInHand(hand)

        // Not a remote any more, or never was: the player may have swapped hands between opening
        // the screen and closing it, and a settings packet must not write onto whatever is there.
        if (!stack.`is`(ModContent.REMOTE)) return

        // Blank remotes have nothing to press and so nothing worth setting. Skipping them keeps
        // the stack clean until the moment it is actually bound to a collar.
        if (stack.get(ModContent.COLLAR_ID) == null) return

        RemoteSettings.apply(
            stack,
            RemoteMode.byName(payload.mode),
            payload.intensity,
            payload.duration,
        )
    }
}
//?}
