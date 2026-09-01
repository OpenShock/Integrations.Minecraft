package openshock.integrations.minecraft

//? if >=1.21.5 {
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.ConfirmScreen
import net.minecraft.network.chat.Component
import openshock.integrations.minecraft.config.AccountConfig
import openshock.integrations.minecraft.content.Collars
import openshock.integrations.minecraft.content.ModContent
import openshock.integrations.minecraft.platform.McCompat
import openshock.integrations.minecraft.utils.shortCode
import org.slf4j.LoggerFactory

/**
 * The moment a collar becomes real: putting one on.
 *
 * A collar can arrive already linked to somebody else's remotes - that is the point, you can hand
 * someone a working collar - but it also means whoever gave it to you chose who can press it. So
 * wearing one is a decision, and this is where it gets made, on the wearer's own machine, before
 * anything can fire. An operator can put a collar on you; they cannot make it work.
 *
 * Saying yes arms that collar by id. Copies of a collar share an id and so count as the same
 * collar, which is deliberate: they are the same collar, and agreeing to one is agreeing to the
 * thing all of them address.
 */
object CollarConsent {

    private val logger = LoggerFactory.getLogger(ShockCraft.MOD_ID)

    /** What the collar slot held last tick, so equipping is noticed once rather than every tick. */
    private var lastSeen: String? = null

    /** Suppresses a re-prompt while the screen from the last one is still up. */
    private var asking = false

    /**
     * Watched from the client tick because there is no equip event that fires on the wearer for
     * items put on by any means - dragging in the inventory, right-clicking, a dispenser, or a
     * server-side swap all have to count.
     */
    fun tick() {
        val player = Minecraft.getInstance().player ?: run { lastSeen = null; return }

        val stack = Collars.wornStack(player)
        if (!stack.`is`(ModContent.COLLAR)) { lastSeen = null; return }

        // A collar only gains an id when a remote is bound to it, so a blank one is nobody's yet
        // and there is nothing to agree to.
        val collarId = stack.get(ModContent.COLLAR_ID) ?: run { lastSeen = null; return }

        if (collarId == lastSeen) return
        lastSeen = collarId

        if (armed(collarId)) return
        if (asking) return

        ask(collarId)
    }

    /** Whether this collar has been agreed to. */
    fun armed(collarId: String): Boolean =
        collarId in AccountConfig.HANDLER.instance().armedCollars

    private fun ask(collarId: String) {
        val account = AccountConfig.HANDLER.instance()

        // The master switch still comes first. Someone who has never turned remotes on is not
        // asking to be prompted about a hat.
        if (!account.allowRemoteControl) return

        // Spelled out rather than summarised as "control you": a remote can be set to shock,
        // vibrate or beep, those are switched separately, and someone deciding whether to put a
        // collar on should be told which of them they are actually agreeing to.
        val allowed = buildList {
            if (account.allowRemoteShock) add("shock")
            if (account.allowRemoteVibrate) add("vibrate")
            if (account.allowRemoteSound) add("beep at")
        }

        // Every mode switched off, so wearing it does nothing at all until one is turned back on.
        // Still worth asking: the collar is still a collar, and saying yes now means it starts
        // working the moment that changes rather than silently at some later point.
        val what =
            if (allowed.isEmpty()) "nothing at all right now - you have every remote mode switched off"
            else allowed.joinToString(", ").replaceFirstChar { it.uppercase() } +
                " you, up to ${account.remoteMaxIntensity}% for ${account.remoteMaxDuration}ms, no more"

        asking = true
        McCompat.setScreen(
            ConfirmScreen(
                { accepted -> answer(collarId, accepted) },
                Component.literal("Wear collar ${shortCode(collarId)}?"),
                Component.literal(
                    "This collar has remotes linked to it.\n\n" +
                        "Wearing it lets whoever holds them do $what.\n" +
                        "Take it off at any time to stop."
                ).withStyle(ChatFormatting.WHITE),
            )
        )
    }

    private fun answer(collarId: String, accepted: Boolean) {
        McCompat.closeScreen()
        asking = false

        if (!accepted) {
            // Nothing is sent anywhere. Declining looks exactly like wearing it with remotes
            // switched off, which is the same silence every other refusal in this mod produces.
            logger.debug("Declined to arm collar {}", shortCode(collarId))
            return
        }

        val account = AccountConfig.HANDLER.instance()
        account.armedCollars = account.armedCollars + collarId
        AccountConfig.HANDLER.save()

        logger.info("Armed collar {}", shortCode(collarId))
    }
}
//?}
