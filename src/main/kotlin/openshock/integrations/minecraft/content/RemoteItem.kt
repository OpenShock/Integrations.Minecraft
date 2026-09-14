package openshock.integrations.minecraft.content

//? if >=1.21.4 {
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
import net.minecraft.world.level.Level
import openshock.integrations.minecraft.platform.Net
// 1.21.5 rebuilt appendHoverText around a TooltipDisplay and a Consumer, where 1.21.4 hands over
// the list itself. Only the signature differs - see the two overrides below, which share a body.
//? if >=1.21.5 {
import net.minecraft.world.item.component.TooltipDisplay
import java.util.function.Consumer
//?}

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

    /**
     * Right-click the air: bind it if it is blank, press it if it is not. Sneak to set it instead.
     */
    override fun use(level: Level, player: Player, hand: InteractionHand): InteractionResult {
        val stack = player.getItemInHand(hand)
        val collarId = stack.get(ModContent.COLLAR_ID)

        // Sneaking on a bound remote opens its settings rather than pressing it, so the gesture
        // that changes what a remote does can never be the gesture that fires it.
        //
        // Handled on the client and only there: the screen lives on that side, and the server's
        // half of this interaction is the packet the screen sends when it closes, not this call.
        // Binding is deliberately left out of the check - a blank remote has nothing to set, so
        // sneaking with one still links it rather than doing nothing.
        if (player.isShiftKeyDown && collarId != null) {
            if (level.isClientSide) RemoteScreens.open(stack, hand)
            return InteractionResult.SUCCESS
        }

        if (level.isClientSide) return InteractionResult.SUCCESS

        // A blank remote in one hand and a collar in the other is the bench gesture: link them
        // with nobody wearing anything, so a collar can be prepared and then handed over working.
        // Safe to do without asking anyone, because agreeing to a collar happens when it goes on
        // a head, not when a remote is bound to it.
        if (collarId == null) return bindToCollarInOtherHand(stack, player, hand)

        // Through Level, whose getServer() Kotlin can see as a property - ServerPlayer and
        // ServerLevel both have a private `server` field that hides theirs.
        val server = level.server ?: return InteractionResult.PASS

        if (!Net.SUPPORTED) return InteractionResult.SUCCESS

        // Read off the item at the moment of the press, so two remotes bound to the same collar
        // can ask for different things and handing one over hands over what it is set to.
        val mode = RemoteSettings.mode(stack)
        val intensity = RemoteSettings.intensity(stack)
        val duration = RemoteSettings.duration(stack)

        // Everyone wearing a collar with this id, which may be several - copies of a collar share
        // its id, so a press reaches the whole group at once.
        for (wearer in Collars.findWearers(server, collarId)) {
            if (!Net.canReach(wearer)) continue
            Net.fire(wearer, collarId, mode.name, intensity, duration)
        }

        // Always the same answer, however many that was - including none. A remote that behaved
        // differently when nobody was wearing the collar would be a way to check up on people.
        return InteractionResult.SUCCESS
    }

    /**
     * What this remote is bound to and what it will ask for.
     *
     * Only ever what the item itself says. It cannot show whether a press would land, because
     * that depends on the wearer's switches and caps, and nothing on this side is ever told.
     */
    //? if >=1.21.5 {
    override fun appendHoverText(
        stack: ItemStack,
        context: TooltipContext,
        display: TooltipDisplay,
        adder: Consumer<Component>,
        flag: TooltipFlag,
    ) = lines(stack, adder::accept)
    //?} else {
    /*override fun appendHoverText(
        stack: ItemStack,
        context: TooltipContext,
        tooltip: MutableList<Component>,
        flag: TooltipFlag,
    ) = lines(stack) { tooltip.add(it) }
    *///?}

    /** The tooltip itself, written once for both shapes of [appendHoverText]. */
    private fun lines(stack: ItemStack, adder: (Component) -> Unit) {
        val id = stack.get(ModContent.COLLAR_ID)

        if (id == null) {
            adder(
                Component.literal("Unbound - hold a collar in your other hand to link it")
                    .withStyle(ChatFormatting.DARK_GRAY)
            )
            return
        }

        adder(
            Component.literal("Presses collar ${ModContent.shortCode(id)}")
                .withStyle(ChatFormatting.GRAY)
        )

        adder(
            Component.literal(
                "${RemoteSettings.mode(stack).label} · " +
                    "${RemoteSettings.intensity(stack)}% · " +
                    RemoteSettings.durationLabel(RemoteSettings.duration(stack))
            ).withStyle(ChatFormatting.DARK_GRAY)
        )

        adder(
            Component.literal("Sneak and use to change · sneak and scroll for intensity")
                .withStyle(ChatFormatting.DARK_GRAY)
        )
    }
}
//?}
