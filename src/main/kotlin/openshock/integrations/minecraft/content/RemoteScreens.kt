package openshock.integrations.minecraft.content

//? if >=1.21.4 {
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.ItemStack

/**
 * The seam between [RemoteItem], which a dedicated server loads, and the screen it opens, which no
 * server may ever resolve.
 *
 * `Item.use` runs on both sides, so the branch that opens a settings screen sits in a class the
 * server loads too. Naming the screen from there would be enough to drag a client-only class onto
 * a server's classpath, which is the same trap `ClientBootstrap` and [openshock.integrations
 * .minecraft.platform.NetClient] exist to avoid - so the client hands its opener in at startup and
 * the item only ever calls through this.
 *
 * Null on a server, and on a client until the entrypoint has run. Both are fine: [open] does
 * nothing, which is exactly what opening a screen on a machine with no screen should do.
 */
object RemoteScreens {

    var opener: ((ItemStack, InteractionHand) -> Unit)? = null

    fun open(stack: ItemStack, hand: InteractionHand) {
        opener?.invoke(stack, hand)
    }
}
//?}
