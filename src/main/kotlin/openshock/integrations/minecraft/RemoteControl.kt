package openshock.integrations.minecraft

import openshock.integrations.minecraft.api.ControlType
import openshock.integrations.minecraft.api.OpenShockApi
import openshock.integrations.minecraft.config.AccountConfig
import openshock.integrations.minecraft.utils.shortCode
import org.slf4j.LoggerFactory

//? if >=1.21.5 {
import net.minecraft.client.Minecraft
import openshock.integrations.minecraft.content.Collars
import openshock.integrations.minecraft.content.ModContent
//?}

/**
 * The gate every shock somebody else asked for has to pass.
 *
 * This is the entire permission model, deliberately in one object: nothing else in the mod may
 * shock on another player's behalf. It runs on this player's own client, which is the only machine
 * that holds an API token, so anything that gets through here has already been measured against
 * settings nobody else can change.
 *
 * A collar can arrive already linked to anyone's remotes, and a server could claim anything. That
 * is exactly why [wearingArmed] reads the collar off this player's own head and checks it against
 * what they agreed to, rather than trusting the packet. An operator can put a collar on you; they
 * cannot make it work.
 */
object RemoteControl {

    private val logger = LoggerFactory.getLogger(ShockCraft.MOD_ID)

    /** The OpenShock backend rejects anything outside these, whatever the caps are set to. */
    private val MIN_INTENSITY: Byte = 1
    private val MIN_DURATION: UShort = 300u

    /**
     * What became of a request, for the log on this machine.
     *
     * It must never travel back to whoever pressed the remote. "Not armed" and "switched off" are
     * different answers, and being able to tell them apart turns a remote into a way to probe
     * someone's settings - or to find out whether they are running the mod at all. Over the wire
     * there is one answer to everything except success, which is nothing.
     */
    enum class Outcome { Fired, SwitchedOff, NotArmed, OnCooldown }

    private var lastFired: Long = -1

    /**
     * A remote someone else pressed.
     *
     * [intensity] and [duration] are what the remote asked for, never what it gets: the caps clamp
     * them on the way through. That is what makes a collar safe to accept - the answer to "how
     * hard can they get me" is always a number this player set, not one that came with the item.
     */
    suspend fun onRemoteFired(collarId: String, intensity: Byte, duration: UShort): Outcome {
        val account = AccountConfig.HANDLER.instance()

        if (!account.allowRemoteControl) return refused(Outcome.SwitchedOff, collarId)
        if (!wearingArmed(collarId)) return refused(Outcome.NotArmed, collarId)

        // Taken before the shock is sent, so a burst of presses cannot queue up behind one call.
        val now = System.currentTimeMillis()
        if (lastFired + account.remoteCooldown.toLong() > now) return refused(Outcome.OnCooldown, collarId)
        lastFired = now

        OpenShockApi.control(
            ControlType.Shock,
            // coerceAtMost then coerceAtLeast rather than coerceIn: a hand-edited config with a
            // cap below the floor would make coerceIn throw, and this must never be the thing
            // that breaks.
            intensity.coerceAtMost(account.remoteMaxIntensity).coerceAtLeast(MIN_INTENSITY),
            duration.coerceAtMost(account.remoteMaxDuration).coerceAtLeast(MIN_DURATION),
            "Collar ${shortCode(collarId)}",
        )

        return Outcome.Fired
    }

    /**
     * Whether this player is, right now, wearing the collar that was pressed and has agreed to it.
     *
     * Read live off the player every time rather than remembered, so taking the collar off
     * stops shocks immediately and no stale state can keep one alive.
     */
    private fun wearingArmed(collarId: String): Boolean {
        //? if >=1.21.5 {
        val player = Minecraft.getInstance().player ?: return false

        val stack = Collars.wornStack(player)
        if (!stack.`is`(ModContent.COLLAR)) return false
        if (stack.get(ModContent.COLLAR_ID) != collarId) return false

        return CollarConsent.armed(collarId)
        //?} else {
        /*// No items below 1.21.5, so there is no collar to be wearing and nothing can be armed.
        return false
        *///?}
    }

    /**
     * Fires at exactly the caps, so this player can feel the strongest thing a remote could do to
     * them before putting a collar on.
     *
     * Deliberate and self-inflicted, so it skips the switch, the collar and the cooldown. The
     * ceiling is the only thing it is testing.
     */
    suspend fun test() {
        val account = AccountConfig.HANDLER.instance()

        OpenShockApi.control(
            ControlType.Shock,
            account.remoteMaxIntensity.coerceAtLeast(MIN_INTENSITY),
            account.remoteMaxDuration.coerceAtLeast(MIN_DURATION),
            "Remote ceiling test",
        )
    }

    private fun refused(outcome: Outcome, collarId: String): Outcome {
        logger.debug("Turned down a remote shock for collar {}: {}", shortCode(collarId), outcome)
        return outcome
    }
}
