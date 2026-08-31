package openshock.integrations.minecraft.content

//? if >=1.21.5 {
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.TooltipFlag
import net.minecraft.world.item.component.TooltipDisplay
import java.util.function.Consumer

/**
 * The collar. Worn by whoever gets shocked.
 *
 * All of its behaviour is elsewhere - it works by being worn and by carrying an id, both of which
 * are read from outside. The only reason this class exists rather than a plain Item is the
 * tooltip, which is what makes a collar tellable from another collar.
 */
class CollarItem(properties: Properties) : Item(properties) {

    override fun appendHoverText(
        stack: ItemStack,
        context: TooltipContext,
        display: TooltipDisplay,
        adder: Consumer<Component>,
        flag: TooltipFlag,
    ) {
        val id = stack.get(ModContent.COLLAR_ID)

        if (id == null) {
            adder.accept(
                Component.literal("Unlinked - no remotes bound to it yet")
                    .withStyle(ChatFormatting.DARK_GRAY)
            )
            return
        }

        // The same code the remotes bound to it show. Six characters is enough to tell a handful
        // of collars apart at a glance, which is all anyone needs; the full id stays in the
        // component for the code to use.
        adder.accept(
            Component.literal("Collar ${ModContent.shortCode(id)}")
                .withStyle(ChatFormatting.GRAY)
        )
    }
}
//?}
