package openshock.integrations.minecraft.content

//? if >=1.21.4 {
import com.mojang.serialization.Codec
import net.minecraft.core.component.DataComponentType
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.Registries
import net.minecraft.network.chat.Component
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.resources.ResourceKey
import net.minecraft.sounds.SoundEvent
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.item.Item
import net.minecraft.world.item.equipment.Equippable
import net.minecraft.world.item.equipment.EquipmentAsset
import net.minecraft.world.item.equipment.EquipmentAssets
import openshock.integrations.minecraft.ShockCraft
import openshock.integrations.minecraft.platform.CollarSlot
import openshock.integrations.minecraft.platform.McCompat

/**
 * Everything the mod puts into a registry, and the ids it puts them under.
 *
 * Only the objects and their names live here. Actually handing them to a registry is loader work
 * and lives in [openshock.integrations.minecraft.platform.Content], because Fabric writes straight
 * into the registry while NeoForge insists on doing it from an event.
 *
 * Items exist from 1.21.4 on, which is the oldest target carrying the whole set the collar needs:
 * the Equippable component, so a collar can be worn without being armour; ResourceKey-based
 * EquipmentAssets, so the worn look resolves; Properties.setId; and `assets/<ns>/items/` model
 * definitions. Equippable itself landed in 1.21.2, but 1.21.2 and 1.21.3 are not built for, so
 * 1.21.4 is where the line falls.
 *
 * Below that the `world/item/equipment` package does not exist at all, and the only way onto a
 * player would be to extend ArmorItem and register an armour material - a second implementation of
 * the same idea. Those versions keep everything they already have; they simply have no items.
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

    /**
     * On a remote: what a press asks for. See [RemoteSettings], which is the only thing that reads
     * or writes them and holds the bounds and the fallbacks for when they are absent.
     *
     * On the remote rather than in the config on purpose. A remote is a physical thing that can be
     * handed to somebody, and what it asks for should travel with it - two remotes bound to the
     * same collar can be set differently, and picking one up tells you what it does.
     *
     * The mode is a string for the same reason the ids are: it is read by people, out of a tooltip
     * and into a bug report, far more often than by code. It also survives a mode being inserted
     * into the enum later, which an ordinal would not.
     */
    val REMOTE_MODE: DataComponentType<String> = stringComponent()
    val REMOTE_INTENSITY: DataComponentType<Int> = intComponent()
    val REMOTE_DURATION: DataComponentType<Int> = intComponent()

    val componentEntries: List<Pair<ResourceKey<DataComponentType<*>>, DataComponentType<*>>> = listOf(
        componentKey("collar_id") to COLLAR_ID,
        componentKey("remote_mode") to REMOTE_MODE,
        componentKey("remote_intensity") to REMOTE_INTENSITY,
        componentKey("remote_duration") to REMOTE_DURATION,
    )

    // <--- Sounds --->

    /**
     * One sound per mode, so a jolt, a buzz and a beep are told apart by ear as well as by eye.
     *
     * Registered here but *defined* in `assets/shockcraft/sounds.json`, which is what decides
     * whether each one is a file of ours or a vanilla event standing in. That indirection is the
     * point: swapping in a real recording is a resource change, not a code change, and until one
     * exists the entry points at the closest vanilla sound so nothing is ever silent.
     *
     * Variable range rather than fixed: the volume [openshock.integrations.minecraft.ShockEffects]
     * plays them at scales with intensity, and a fixed range would carry a nudge exactly as far as
     * a full jolt.
     */
    val SHOCK_SOUND: SoundEvent = soundEvent("shock")
    val VIBRATE_SOUND: SoundEvent = soundEvent("vibrate")
    val BEEP_SOUND: SoundEvent = soundEvent("beep")

    val soundEntries: List<Pair<ResourceKey<SoundEvent>, SoundEvent>> = listOf(
        soundKey("shock") to SHOCK_SOUND,
        soundKey("vibrate") to VIBRATE_SOUND,
        soundKey("beep") to BEEP_SOUND,
    )

    // <--- Items --->

    /**
     * The look worn on the leg, for the collar in the vanilla leggings slot.
     *
     * Only ever reached with no accessory mod installed, because that is the only case where the
     * collar carries an Equippable at all - see [collarProperties].
     *
     * Only that slot. An accessory slot draws its own model instead, hung off the leg part from
     * `assets/shockcraft/trinkets/collar.json` - the equipment route is not available there,
     * because the renderer's `equipment_replace` overrides a vanilla armour slot unconditionally
     * and a worn collar would blank out whatever armour was in it.
     *
     * `humanoid_leggings` rather than `humanoid` is what makes the cuff sit close: it is the only
     * layer vanilla draws on the half-pixel-inflated armour model rather than the full-pixel one.
     * The texture paints the right leg cube (x 0-15, y 20-31, hip at the top) and nothing else;
     * the leggings layer also draws the body, which is left blank on purpose. The band sits at
     * the bottom of that range so it lands on the ankle, where the accessory-slot model puts it.
     * A 64x32 armour sheet mirrors the legs, so this way of wearing it shows a cuff on both.
     */
    val EQUIPMENT_ASSET: ResourceKey<EquipmentAsset> =
        ResourceKey.create(EquipmentAssets.ROOT_ID, McCompat.identifier(ShockCraft.MOD_ID, "collar"))

    val COLLAR_KEY: ResourceKey<Item> = itemKey("collar")
    val REMOTE_KEY: ResourceKey<Item> = itemKey("remote")

    /**
     * Worn in an accessory slot where one exists, and the leggings slot otherwise - see
     * [CollarSlot] and [collarProperties]. Exactly one of the two and never both: with an accessory
     * mod installed the collar is not leg-equippable at all, and without one it is. So a collar
     * always has somewhere to go, and never two places it could be at once.
     *
     * The leg is deliberate. A shocker does not go near a neck, and this mod should not be the
     * picture that says otherwise - so the thing worn is a cuff on the lower leg, which is where
     * one actually goes. The item keeps the name: a collar is the agreement, not the placement.
     *
     * Where exactly is set by the `offset` in `assets/shockcraft/trinkets/collar.json`, measured
     * in fractions of the leg part's own bounding box rather than in pixels: 0 is the knee, -1 is
     * the sole of the foot, and -0.3 sits it on the calf. The leggings texture is painted to
     * match, so moving one means moving the other.
     *
     * Everything a collar does, it does by being worn and by carrying an id, both read from the
     * outside; [CollarItem] exists only to put that id on the tooltip.
     */
    val COLLAR: Item = CollarItem(collarProperties())

    /**
     * Equippable only when there is nothing better to wear the collar in.
     *
     * With an accessory mod installed the collar belongs in its accessory slot and nowhere else,
     * so the Equippable component is left off entirely - and that is what keeps it out of the
     * vanilla leggings slot, because a slot only accepts items whose Equippable names it. Nothing
     * else is given up: Trinkets and Curios both equip from a tag rather than from this component,
     * and the worn model comes from `assets/shockcraft/trinkets/collar.json`.
     *
     * Without an accessory mod the component goes back on and the collar costs you a pair of
     * leggings, which is the whole point of the fallback. Otherwise the only way to wear one would
     * be to install another mod.
     *
     * Decided once, at registration, because whether a mod is installed cannot change while the
     * game is running. It does mean a client and a server that disagree about having an accessory
     * mod also disagree about this component - but such a pair cannot show a worn collar to each
     * other anyway, so there is nothing here that was going to work.
     */
    private fun collarProperties(): Item.Properties {
        val properties = Item.Properties().stacksTo(1)

        if (!CollarSlot.available) {
            // The long way round rather than Properties.equippable(slot), which builds an
            // Equippable with no asset - and without an asset nothing is drawn on the wearer.
            properties.component(
                DataComponents.EQUIPPABLE,
                Equippable.builder(EquipmentSlot.LEGS)
                    .setAsset(EQUIPMENT_ASSET)
                    .build()
            )
        }

        return properties.setId(COLLAR_KEY)
    }

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

    private fun intComponent(): DataComponentType<Int> =
        DataComponentType.builder<Int>()
            .persistent(Codec.INT)
            .networkSynchronized(ByteBufCodecs.VAR_INT)
            .build()

    private fun soundEvent(path: String): SoundEvent =
        SoundEvent.createVariableRangeEvent(McCompat.identifier(ShockCraft.MOD_ID, path))

    private fun soundKey(path: String): ResourceKey<SoundEvent> =
        ResourceKey.create(Registries.SOUND_EVENT, McCompat.identifier(ShockCraft.MOD_ID, path))

    private fun itemKey(path: String): ResourceKey<Item> =
        ResourceKey.create(Registries.ITEM, McCompat.identifier(ShockCraft.MOD_ID, path))

    private fun componentKey(path: String): ResourceKey<DataComponentType<*>> =
        ResourceKey.create(Registries.DATA_COMPONENT_TYPE, McCompat.identifier(ShockCraft.MOD_ID, path))
}
//?}
