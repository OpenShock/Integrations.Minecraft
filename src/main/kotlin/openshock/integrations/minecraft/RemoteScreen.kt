package openshock.integrations.minecraft

//? if >=1.21.5 {
import net.minecraft.client.gui.components.AbstractSliderButton
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.ItemStack
import openshock.integrations.minecraft.api.RemoteMode
import openshock.integrations.minecraft.content.ModContent
import openshock.integrations.minecraft.content.RemoteSettings
import openshock.integrations.minecraft.platform.NetClient
import openshock.integrations.minecraft.utils.shortCode

/**
 * Setting what a remote asks for: sneak and right-click one to get here.
 *
 * Client-only, and reached through [openshock.integrations.minecraft.content.RemoteScreens] rather
 * than named from the item, so a dedicated server never resolves it.
 *
 * Nothing is written here. The screen edits three numbers in memory and sends them once, on close;
 * the server writes the components, clamps everything, and is the only thing that ever touches the
 * stack. That also means a rejected or lost packet simply leaves the remote as it was.
 *
 * Every label lives on a widget rather than being drawn: `Screen.render` moved off `Screen` in
 * 26.1, and `Button` and `AbstractSliderButton` are identical from 1.21.5 all the way up - so a
 * screen built only out of those needs no per-version handling at all.
 */
class RemoteScreen(
    private val stack: ItemStack,
    private val hand: InteractionHand,
) : Screen(Component.literal("Remote")) {

    private var mode: RemoteMode = RemoteSettings.mode(stack)
    private var intensity: Int = RemoteSettings.intensity(stack)
    private var duration: Int = RemoteSettings.duration(stack)

    /** Set once the values have gone up the wire, so leaving by Escape does not send them twice. */
    private var sent = false

    override fun init() {
        val width = 200
        val height = 20
        val left = (this.width - width) / 2
        var top = this.height / 4

        fun next(): Int {
            val y = top
            top += height + 6
            return y
        }

        // The collar this remote presses, as a label rather than a control - there is nothing to
        // set here, and it is the one thing that says which remote you are holding.
        val collarId = stack.get(ModContent.COLLAR_ID)
        addRenderableWidget(
            Button.builder(
                Component.literal(
                    if (collarId == null) "Unbound remote"
                    else "Remote for collar ${shortCode(collarId)}"
                )
            ) { }
                .bounds(left, next(), width, height)
                .build()
                // A caption, not a control. Kept as a button so the screen needs no text drawing.
                .also { it.active = false }
        )

        addRenderableWidget(
            Button.builder(Component.literal("Mode: ${mode.label}")) { button ->
                mode = mode.next()
                button.message = Component.literal("Mode: ${mode.label}")
            }
                .bounds(left, next(), width, height)
                .build()
        )

        addRenderableWidget(
            object : AbstractSliderButton(
                left, next(), width, height,
                Component.literal("Intensity: $intensity"),
                fraction(intensity, RemoteSettings.MIN_INTENSITY, RemoteSettings.MAX_INTENSITY),
            ) {
                override fun updateMessage() {
                    message = Component.literal("Intensity: $intensity")
                }

                override fun applyValue() {
                    intensity = whole(value, RemoteSettings.MIN_INTENSITY, RemoteSettings.MAX_INTENSITY)
                }
            }
        )

        addRenderableWidget(
            object : AbstractSliderButton(
                left, next(), width, height,
                Component.literal("Duration: ${RemoteSettings.durationLabel(duration)}"),
                fraction(duration, RemoteSettings.MIN_DURATION, RemoteSettings.MAX_DURATION),
            ) {
                override fun updateMessage() {
                    message = Component.literal("Duration: ${RemoteSettings.durationLabel(duration)}")
                }

                override fun applyValue() {
                    // Rounded to tenths of a second: the slider spans thirty seconds, and a value
                    // nobody can reproduce by dragging is not worth being able to store.
                    val raw = whole(value, RemoteSettings.MIN_DURATION, RemoteSettings.MAX_DURATION)
                    duration = RemoteSettings.clampDuration((raw / 100) * 100)
                }
            }
        )

        addRenderableWidget(
            Button.builder(Component.literal("Done")) { onClose() }
                .bounds(left, next() + 6, width, height)
                .build()
        )
    }

    /**
     * Sends on the way out rather than on every drag, so one visit to the screen is at most one
     * packet however much the sliders were moved - and none at all if they were only looked at.
     */
    override fun onClose() {
        if (!sent) {
            sent = true

            val changed = mode != RemoteSettings.mode(stack) ||
                intensity != RemoteSettings.intensity(stack) ||
                duration != RemoteSettings.duration(stack)

            if (changed) NetClient.sendRemoteConfig(hand, mode, intensity, duration)
        }
        super.onClose()
    }

    /** The game keeps running behind it: this is a setting on an item, not a pause. */
    override fun isPauseScreen(): Boolean = false

    private fun fraction(value: Int, min: Int, max: Int): Double =
        (value - min).toDouble() / (max - min).toDouble()

    private fun whole(fraction: Double, min: Int, max: Int): Int =
        Math.round(min + fraction * (max - min)).toInt()
}
//?}
