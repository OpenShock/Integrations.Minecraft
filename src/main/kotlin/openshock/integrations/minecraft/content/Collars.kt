package openshock.integrations.minecraft.content

//? if >=1.21.4 {
import net.minecraft.core.component.DataComponents
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.ItemStack
import openshock.integrations.minecraft.platform.CollarSlot
import java.util.UUID

/**
 * Reading and writing collars, whether they are on a leg or in a hand.
 *
 * The stack-level half is what lets a collar be wired up before anyone wears it - linked on a
 * bench and handed over ready to work. The worn half finds who a press should reach.
 *
 * There is deliberately no registry of collars anywhere. A collar is worn or it is not, and that
 * is a fact about a player's slots that can be read whenever it is needed - so there is no
 * bookkeeping to go stale when someone dies, logs out, or hands the thing over. It is also why
 * taking the collar off is an honest off switch: nothing to update, nothing left pointing at you.
 */
object Collars {

    // <--- Any collar, wherever it is --->

    /** The collar's id, or null if this is not a collar or has never been linked to. */
    fun idOf(stack: ItemStack): String? {
        if (!stack.`is`(ModContent.COLLAR)) return null
        return stack.get(ModContent.COLLAR_ID)
    }

    /**
     * The same, but stamps a fresh id on a collar that has none yet.
     *
     * Collars are minted blank - out of the creative menu, out of a command - and only become a
     * particular collar the first time a remote is bound to one. Doing it lazily means no per-tick
     * work and nothing written to items nobody is using.
     */
    fun ensureId(stack: ItemStack): String? {
        if (!stack.`is`(ModContent.COLLAR)) return null

        stack.get(ModContent.COLLAR_ID)?.let { return it }

        val id = UUID.randomUUID().toString()
        stack.set(ModContent.COLLAR_ID, id)

        // Named as well as stamped, so the code is visible in the hotbar and not only on hover.
        // ITEM_NAME rather than CUSTOM_NAME: it is the item's own name, not a player's rename, so
        // it renders upright and an anvil can still override it.
        stack.set(DataComponents.ITEM_NAME, ModContent.labelled("item.shockcraft.collar", id))
        return id
    }

    // <--- The collar on a leg --->
    //
    // Read-only, and there is nothing on a worn collar that anyone could change anyway. Who a
    // press reaches is read off what people are actually wearing, never trusted from a client.

    /**
     * Where a worn collar actually is.
     *
     * The accessory slot wins when there is one, and the leggings slot is the fallback for builds
     * and setups without an accessory mod. The collar stays leg-equippable either way, so nobody
     * is forced to install anything to use one - they just give up a pair of leggings.
     */
    fun wornStack(entity: LivingEntity): ItemStack {
        CollarSlot.wornStack(entity)?.let { if (it.`is`(ModContent.COLLAR)) return it }
        return entity.getItemBySlot(EquipmentSlot.LEGS)
    }

    fun wornId(entity: LivingEntity): String? = idOf(wornStack(entity))

    /**
     * Everyone online wearing this collar.
     *
     * Plural on purpose. A collar id belongs to the item, so copies of a collar share one - wire a
     * collar up, copy it, hand it round, and a single press reaches the whole group. Nothing here
     * decides whether any of them is actually shocked: each of their clients answers that alone,
     * against the collar its own player is wearing and settings only that person can change.
     *
     * A linear scan over the player list, run once per remote press. That is cheap next to what a
     * press actually costs, which is an HTTP round trip on somebody's client.
     */
    fun findWearers(server: MinecraftServer, collarId: String): List<ServerPlayer> =
        server.playerList.players.filter { wornId(it) == collarId }
}
//?}
