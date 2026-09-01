package openshock.integrations.minecraft.content

//? if >=1.21.5 {
import net.minecraft.world.item.ItemStack
import openshock.integrations.minecraft.api.RemoteMode

/**
 * What a remote asks for, kept on the remote itself.
 *
 * All three are a request and never a setting. The wearer's caps clamp intensity and duration on
 * the way through [openshock.integrations.minecraft.RemoteControl], and a mode they have not
 * allowed is refused outright - so nothing anyone does to a remote can raise a ceiling. The honest
 * answer to "how hard can this get me" stays a number the wearer chose.
 *
 * An absent component reads as the value remotes used before any of this was adjustable, so a
 * remote bound in an older world keeps behaving exactly as it did.
 */
object RemoteSettings {

    /** The window the OpenShock backend accepts, so a remote cannot ask for one it would reject. */
    const val MIN_INTENSITY: Int = 1
    const val MAX_INTENSITY: Int = 100
    const val MIN_DURATION: Int = 300
    const val MAX_DURATION: Int = 30_000

    /** What a remote asked for when the numbers were hard-coded in [RemoteItem]. */
    const val DEFAULT_INTENSITY: Int = 25
    const val DEFAULT_DURATION: Int = 1000

    /** How far one notch of the scroll wheel moves the intensity. */
    const val INTENSITY_STEP: Int = 5

    fun mode(stack: ItemStack): RemoteMode = RemoteMode.byName(stack.get(ModContent.REMOTE_MODE))

    fun intensity(stack: ItemStack): Int =
        clampIntensity(stack.get(ModContent.REMOTE_INTENSITY) ?: DEFAULT_INTENSITY)

    fun duration(stack: ItemStack): Int =
        clampDuration(stack.get(ModContent.REMOTE_DURATION) ?: DEFAULT_DURATION)

    /**
     * Writes all three at once.
     *
     * Only ever reached from a packet a client sent, so every value is clamped here rather than
     * trusted - the same rule [openshock.integrations.minecraft.ShockEffects] applies to what a
     * client claims about its own sparks. Clamping rather than rejecting because an out-of-range
     * number is a bug or an old build, not an attack: the ceilings that matter are the wearer's,
     * and those are enforced on the wearer's own machine no matter what is written here.
     */
    fun apply(stack: ItemStack, mode: RemoteMode, intensity: Int, duration: Int) {
        stack.set(ModContent.REMOTE_MODE, mode.name)
        stack.set(ModContent.REMOTE_INTENSITY, clampIntensity(intensity))
        stack.set(ModContent.REMOTE_DURATION, clampDuration(duration))
    }

    fun clampIntensity(value: Int): Int = value.coerceIn(MIN_INTENSITY, MAX_INTENSITY)

    fun clampDuration(value: Int): Int = value.coerceIn(MIN_DURATION, MAX_DURATION)

    /** `1.2s`, for a tooltip or a slider label. */
    fun durationLabel(millis: Int): String = String.format("%.1fs", millis / 1000f)
}
//?}
