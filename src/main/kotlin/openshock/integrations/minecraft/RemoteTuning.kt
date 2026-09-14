package openshock.integrations.minecraft

//? if >=1.21.4 {
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.ItemStack
import openshock.integrations.minecraft.content.ModContent
import openshock.integrations.minecraft.content.RemoteSettings
import openshock.integrations.minecraft.platform.McCompat
import openshock.integrations.minecraft.platform.NetClient
import kotlin.math.abs
import kotlin.math.ceil
//?}

/**
 * Sneak and scroll on a bound remote to change how hard it presses, without opening anything.
 *
 * Intensity is the one setting worth reaching for mid-conversation, so it gets a gesture; mode and
 * duration are decided once and live in [RemoteScreen].
 *
 * ### Where the scroll comes from
 *
 * [onScroll] is called from `MouseHandlerMixin`, which sits on the head of the one method vanilla
 * routes every wheel event through. Returning true from there cancels it, so while the gesture is
 * running the wheel never reaches the hotbar at all.
 *
 * That mixin is the whole reason this works cleanly, and it replaced two attempts that did not.
 * Reading the selected hotbar slot from a tick and putting it back looks like it should be enough
 * and is not: the wheel moves the selection during input polling, which happens every frame, while
 * a tick runs twenty times a second - so the moved slot is drawn for several frames before it can
 * be restored, and the hotbar visibly flickers. There is no restoring it fast enough; the scroll
 * has to not happen.
 */
object RemoteTuning {

    //? if >=1.21.4 {
    /** Quiet time before a run of notches is sent as one packet. Roughly a third of a second. */
    private const val SEND_DELAY_TICKS = 6

    /** The intensity being dialled in, or -1 when nothing is pending. */
    private var pending = -1
    private var ticksSinceChange = 0

    /**
     * A wheel event, straight off the mouse. Returns whether it was ours.
     *
     * True means the scroll is swallowed and the hotbar does not move - so this must be false for
     * every scroll that is not deliberately part of the gesture, or the wheel stops working.
     */
    fun onScroll(deltaY: Double): Boolean {
        if (deltaY == 0.0) return false

        val stack = tunableStack() ?: return false

        // Any movement is at least one notch, so a trackpad's fractions are not swallowed
        // silently, and a hard flick is capped so it cannot slam the value end to end.
        val steps = ceil(abs(deltaY)).toInt().coerceIn(1, 3)
        adjust(stack, if (deltaY > 0) steps else -steps)
        return true
    }

    /** Called at the start of every client tick, from both loaders' entrypoints. */
    fun tick() {
        if (pending < 0) return

        // Sending stops the moment the gesture does, rather than waiting out the quiet time with
        // the remote already put away.
        if (tunableStack() == null) {
            flush()
            return
        }

        ticksSinceChange++
        if (ticksSinceChange >= SEND_DELAY_TICKS) flush()
    }

    /**
     * The remote this gesture applies to, or null when the gesture is not running.
     *
     * Main hand only. The off hand cannot be scrolled to and taking it into account would mean
     * guessing which of two remotes a scroll meant.
     */
    private fun tunableStack(): ItemStack? {
        val player = Minecraft.getInstance().player ?: return null
        if (!player.isShiftKeyDown) return null

        // A screen of its own is where the wheel belongs to whatever is on screen.
        if (McCompat.currentScreen != null) return null

        val stack = player.getItemInHand(InteractionHand.MAIN_HAND)
        if (!stack.`is`(ModContent.REMOTE)) return null
        if (stack.get(ModContent.COLLAR_ID) == null) return null

        return stack
    }

    private fun adjust(stack: ItemStack, notches: Int) {
        // Carries on from whatever is already being dialled in, so a fast flick of several
        // notches adds up instead of each one starting again from what the item last said.
        val from = if (pending >= 0) pending else RemoteSettings.intensity(stack)

        pending = RemoteSettings.clampIntensity(from + notches * RemoteSettings.INTENSITY_STEP)
        ticksSinceChange = 0

        McCompat.sendActionBar(
            Component.literal(
                "${RemoteSettings.mode(stack).label} · $pending% · " +
                    RemoteSettings.durationLabel(RemoteSettings.duration(stack))
            ).withStyle(ChatFormatting.GRAY)
        )
    }

    /** Sends what has been dialled in, if anything, and stops tracking it. */
    private fun flush() {
        val intensity = pending
        pending = -1
        ticksSinceChange = 0

        if (intensity < 0) return

        val player = Minecraft.getInstance().player ?: return
        val stack = player.getItemInHand(InteractionHand.MAIN_HAND)
        if (!stack.`is`(ModContent.REMOTE)) return

        // Mode and duration go back unchanged: the packet sets all three at once, and this
        // gesture is only ever about the one.
        NetClient.sendRemoteConfig(
            InteractionHand.MAIN_HAND,
            RemoteSettings.mode(stack),
            intensity,
            RemoteSettings.duration(stack),
        )
    }
    //?} else {
    /*// Stubs below 1.21.4, which has no remote to tune. They exist rather than the whole object
    // being compiled away because MouseHandlerMixin is applied on every version and calls into
    // here; a scroll simply never belongs to us there.
    fun onScroll(deltaY: Double): Boolean = false

    fun tick() {}
    *///?}
}
