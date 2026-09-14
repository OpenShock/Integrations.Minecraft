package openshock.integrations.minecraft.platform

//? if >=1.21.4 {
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.ItemStack
import openshock.integrations.minecraft.content.ModContent

//? if >=26.1 {
import eu.pb4.trinkets.api.TrinketsApi

/**
 * The accessory slot a collar is worn in, when one is available.
 *
 * Three different mods provide this across the versions we build for, so this file is the only
 * place that knows which: Trinkets Updated from 26.1 on (one jar, both loaders), and below that
 * Trinkets on Fabric and Curios on NeoForge. All three answer the same question, so everything
 * else in the mod only ever sees [wornStack].
 *
 * None of them is required. [available] is a class-presence check rather than a loader lookup - it
 * works the same on both loaders - and when it comes back false the collar falls back to the head
 * slot, which is what every build did before any of this existed. Nobody has to install an
 * accessory mod to use the rest of ShockCraft.
 */
object CollarSlot {

    /**
     * Whether the slot mod is actually present.
     *
     * Resolved once and never initialised (`initialize = false`), so a missing mod costs one failed
     * lookup rather than an exception on every press.
     *
     * Public because it decides more than where to look for a worn collar: [ModContent] reads it
     * at registration to decide whether the collar is equippable in the vanilla leggings slot at
     * all. Safe to read from there - this object's own initialisation touches nothing but the lazy
     * delegate, and [Api], which is the half that names [ModContent], is a separate object that is
     * not initialised until something actually calls it.
     */
    val available: Boolean by lazy {
        runCatching {
            Class.forName("eu.pb4.trinkets.api.TrinketsApi", false, CollarSlot::class.java.classLoader)
        }.isSuccess
    }

    /** The collar this entity is wearing in an accessory slot, or null for none and none possible. */
    fun wornStack(entity: LivingEntity): ItemStack? {
        if (!available) return null
        return Api.wornStack(entity)
    }

    /**
     * Kept in its own class so the API is only loaded on a machine that has it. Naming a missing
     * class is harmless; touching one is a NoClassDefFoundError.
     */
    private object Api {
        fun wornStack(entity: LivingEntity): ItemStack? =
            TrinketsApi.getAttachment(entity)
                ?.findFirst { it.`is`(ModContent.COLLAR) }
                ?.orElse(null)
                ?.get()
    }
}
//?} elif fabric {
/*import dev.emi.trinkets.api.TrinketsApi

/**
 * The accessory slot a collar is worn in - Trinkets, on Fabric below 26.1. See the 26.1+ variant
 * of this file for why it is optional and what happens when it is missing.
 */
object CollarSlot {

    /** @see the 26.1+ variant of this file, which explains why this is public. */
    val available: Boolean by lazy {
        runCatching {
            Class.forName("dev.emi.trinkets.api.TrinketsApi", false, CollarSlot::class.java.classLoader)
        }.isSuccess
    }

    fun wornStack(entity: LivingEntity): ItemStack? {
        if (!available) return null
        return Api.wornStack(entity)
    }

    private object Api {
        fun wornStack(entity: LivingEntity): ItemStack? =
            TrinketsApi.getTrinketComponent(entity)
                ?.orElse(null)
                ?.getEquipped(ModContent.COLLAR)
                ?.firstOrNull()
                ?.b
    }
}
*///?} else {
/*import top.theillusivec4.curios.api.CuriosApi

/**
 * The accessory slot a collar is worn in - Curios, on NeoForge below 26.1. See the 26.1+ variant
 * of this file for why it is optional and what happens when it is missing.
 */
object CollarSlot {

    /** @see the 26.1+ variant of this file, which explains why this is public. */
    val available: Boolean by lazy {
        runCatching {
            Class.forName("top.theillusivec4.curios.api.CuriosApi", false, CollarSlot::class.java.classLoader)
        }.isSuccess
    }

    fun wornStack(entity: LivingEntity): ItemStack? {
        if (!available) return null
        return Api.wornStack(entity)
    }

    private object Api {
        fun wornStack(entity: LivingEntity): ItemStack? =
            CuriosApi.getCuriosInventory(entity)
                ?.orElse(null)
                ?.findFirstCurio(ModContent.COLLAR)
                ?.orElse(null)
                ?.stack()
    }
}
*///?}
//?} else {
/*import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.ItemStack

/** Stub below 1.21.4, which has no collar to wear in the first place. */
object CollarSlot {
    fun wornStack(entity: LivingEntity): ItemStack? = null
}
*///?}
