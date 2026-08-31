package openshock.integrations.minecraft.content

//? if >=1.21.5 {
import com.mojang.serialization.Codec
import net.minecraft.core.component.DataComponentType
import net.minecraft.core.registries.Registries
import net.minecraft.network.chat.Component
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.resources.ResourceKey
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.item.Item
import openshock.integrations.minecraft.ShockCraft
import openshock.integrations.minecraft.platform.McCompat

/**
 * Everything the mod puts into a registry, and the ids it puts them under.
 *
 * Only the objects and their names live here. Actually handing them to a registry is loader work
 * and lives in [openshock.integrations.minecraft.platform.Content], because Fabric writes straight
 * into the registry while NeoForge insists on doing it from an event.
 *
 * Items exist from 1.21.5 on. That is where the Equippable component landed, which is what lets a
 * collar be worn without being armour - before it, the only way onto a player's head was to extend
 * ArmorItem and register an armour material, which is a second implementation of the same idea.
 * Older versions keep everything they already have; they simply have no items.
 */
object ModContent {

    // <--- Components --->
    //
    // Both are plain strings holding a UUID. A string rather than a UUID component because these
    // ids are read by people - out of a chat message, into a command - far more often than by code.

    /**
     * On a collar: which collar this is. On a remote: which collar it presses.
     *
     * The whole permission model, in one string. Holding a collar is how you learn its id, and
     * knowing its id is what lets a remote address it - so binding a remote requires the collar in
     * hand, and nothing else needs storing anywhere.
     *
     * Copies of a collar share it, which is deliberate: they are the same collar, one press
     * reaches everyone wearing one, and there is no per-item state to drift apart between them.
     * What it costs is the ability to revoke one remote in particular; revoking is per collar,
     * done by taking it off or disarming it in the settings.
     */
    val COLLAR_ID: DataComponentType<String> = stringComponent()

    val componentEntries: List<Pair<ResourceKey<DataComponentType<*>>, DataComponentType<*>>> = listOf(
        componentKey("collar_id") to COLLAR_ID,
    )

    // <--- Items --->

    val COLLAR_KEY: ResourceKey<Item> = itemKey("collar")
    val REMOTE_KEY: ResourceKey<Item> = itemKey("remote")

    /**
     * Worn on the head, because that is the only slot vanilla offers that is anywhere near a neck.
     *
     * It costs a helmet, which is a real price in survival. The alternative was a dedicated
     * accessory slot, and at 1.21.5 and above there is no accessory API that covers both loaders -
     * Trinkets stops at 1.21.1, Accessories at 1.21.10, and Curios has no Fabric build in range.
     *
     * Everything a collar does, it does by being worn and by carrying an id, both read from the
     * outside; [CollarItem] exists only to put that id on the tooltip.
     */
    val COLLAR: Item = CollarItem(
        Item.Properties()
            .stacksTo(1)
            .equippable(EquipmentSlot.HEAD)
            .setId(COLLAR_KEY)
    )

    val REMOTE: Item = RemoteItem(
        Item.Properties()
            .stacksTo(1)
            .setId(REMOTE_KEY)
    )

    val itemEntries: List<Pair<ResourceKey<Item>, Item>> = listOf(
        COLLAR_KEY to COLLAR,
        REMOTE_KEY to REMOTE,
    )

    /** @see openshock.integrations.minecraft.utils.shortCode */
    fun shortCode(id: String): String = openshock.integrations.minecraft.utils.shortCode(id)

    /** Both halves of a bound pair carry the same name, so they read as a set in the hotbar. */
    fun labelled(base: String, collarId: String): Component =
        Component.translatable(base)
            .append(Component.literal(" · ${shortCode(collarId)}"))

    private fun stringComponent(): DataComponentType<String> =
        DataComponentType.builder<String>()
            .persistent(Codec.STRING)
            .networkSynchronized(ByteBufCodecs.STRING_UTF8)
            .build()

    private fun itemKey(path: String): ResourceKey<Item> =
        ResourceKey.create(Registries.ITEM, McCompat.identifier(ShockCraft.MOD_ID, path))

    private fun componentKey(path: String): ResourceKey<DataComponentType<*>> =
        ResourceKey.create(Registries.DATA_COMPONENT_TYPE, McCompat.identifier(ShockCraft.MOD_ID, path))
}
//?}
