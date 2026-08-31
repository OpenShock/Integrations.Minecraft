package openshock.integrations.minecraft.content

//? if >=1.21.5 {
import net.minecraft.ChatFormatting
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.TooltipFlag
import net.minecraft.world.item.component.TooltipDisplay
import net.minecraft.world.level.Level
import openshock.integrations.minecraft.platform.Net
import java.util.function.Consumer

/**
 * The remote: bind it to a collar held in your other hand, then right-click to press it.
 *
 * Linking always needs the collar itself in hand, which is what stops a remote being added to a
 * collar that is already on someone's head. To wire one up you have to take it off them first -
 * and that both stops it working and makes their client ask again before it works anew.
 *
 * Everything here runs on the server and decides nothing. Firing means asking a wearer's client to
 * consider a shock; that client checks its own switch, the collar on its own head, its own
 * cooldown and its own caps, and this side never learns which of those it hit.
 *
 * Holding the remote is not permission either. A stolen one works only while its link is still on
 * the collar and the wearer still has that collar on and armed - three things they control and a
 * thief does not.
 */
class RemoteItem(properties: Properties) : Item(properties) {

    /**
     * What a press asks for. The wearer's caps clamp it, so this is a request and not a setting -
     * the honest answer to "how hard can this get me" is always a number the wearer chose.
     */
    private val askIntensity = 25
    private val askDuration = 1000

    /**
     * Links a blank remote to a collar held in the player's other hand. The only way to link.
     *
     * Nobody has to be wearing it and nobody is asked, which is the point - a collar can be
     * prepared and then handed over working. It is also why
     * [openshock.integrations.minecraft.CollarConsent] exists: a collar wired up this way does
     * nothing at all until whoever ends up wearing it agrees to it, with exactly the remotes it
     * carries at that moment.
     */
    private fun bindToCollarInOtherHand(
        stack: ItemStack,
        player: Player,
        hand: InteractionHand,
    ): InteractionResult {

        val otherHand = if (hand == InteractionHand.MAIN_HAND) InteractionHand.OFF_HAND else InteractionHand.MAIN_HAND
        val collar = player.getItemInHand(otherHand)

        val collarId = Collars.ensureId(collar) ?: return InteractionResult.PASS
        bind(stack, collarId)

        // Through ServerPlayer, which is what this always is by here: Player itself has
        // displayClientMessage at 1.21.5 and sendSystemMessage at 26.2, and neither on both.
        (player as? ServerPlayer)?.sendSystemMessage(
            Component.literal("This remote now controls collar " + ModContent.shortCode(collarId) + ".")
        )
        return InteractionResult.SUCCESS
    }

    /** Everything that makes a remote a bound remote. */
    private fun bind(stack: ItemStack, collarId: String) {
        stack.set(ModContent.COLLAR_ID, collarId)

        // The same name the collar carries, so a bound pair reads as a set in the hotbar, plus a
        // glint - which is the only part visible without hovering at all.
        stack.set(DataComponents.ITEM_NAME, ModContent.labelled("item.shockcraft.remote", collarId))
        stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true)
    }

    /** Right-click the air: bind it if it is blank, press it if it is not. */
    override fun use(level: Level, player: Player, hand: InteractionHand): InteractionResult {
        if (level.isClientSide) return InteractionResult.SUCCESS

        val stack = player.getItemInHand(hand)

        // A blank remote in one hand and a collar in the other is the bench gesture: link them
        // with nobody wearing anything, so a collar can be prepared and then handed over working.
        // Safe to do without asking anyone, because agreeing to a collar happens when it goes on
        // a head, not when a remote is bound to it.
        val collarId = stack.get(ModContent.COLLAR_ID)
            ?: return bindToCollarInOtherHand(stack, player, hand)

        // Through Level, whose getServer() Kotlin can see as a property - ServerPlayer and
        // ServerLevel both have a private `server` field that hides theirs.
        val server = level.server ?: return InteractionResult.PASS

        if (!Net.SUPPORTED) return InteractionResult.SUCCESS

        // Everyone wearing a collar with this id, which may be several - copies of a collar share
        // its id, so a press reaches the whole group at once.
        for (wearer in Collars.findWearers(server, collarId)) {
            if (!Net.canReach(wearer)) continue
            Net.fire(wearer, collarId, askIntensity, askDuration)
        }

        // Always the same answer, however many that was - including none. A remote that behaved
        // differently when nobody was wearing the collar would be a way to check up on people.
        return InteractionResult.SUCCESS
    }
}
//?}
