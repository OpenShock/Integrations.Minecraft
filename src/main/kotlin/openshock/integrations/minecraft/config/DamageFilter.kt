package openshock.integrations.minecraft.config

import net.minecraft.world.damagesource.DamageSource
import openshock.integrations.minecraft.platform.McCompat

/**
 * Whether a hit may shock, decided by whichever list [ShockCraftConfig.damageFilterMode] put in
 * charge. The two lists never both apply: one of them is the filter, the other is ignored until
 * the mode is switched back, which keeps "why did that not shock me" answerable by looking at one
 * place.
 */
object DamageFilter {

    fun allows(config: ShockCraftConfig, source: DamageSource?): Boolean =
        when (config.damageFilterMode) {
            DamageFilterMode.Categories -> DamageCategory.of(source) in config.damageCategories

            DamageFilterMode.DamageTypes -> {
                // A hit whose type never reached the client cannot be matched against an id list,
                // and guessing would mean shocking for something the user did not pick.
                val id = typeIdOf(source)
                id != null && id in config.damageTypes
            }
        }

    /** The registry id of what hurt us, e.g. `minecraft:cactus`, or null if we were never told. */
    fun typeIdOf(source: DamageSource?): String? = McCompat.damageTypeId(source)

    /** What to call a hit in the log: its exact type when we know it, its bucket otherwise. */
    fun describe(source: DamageSource?): String = typeIdOf(source) ?: DamageCategory.of(source).name

    /**
     * A type id the way the config screen labels it: vanilla without its namespace, everything
     * else in full so a modded type stays unambiguous.
     *
     * Shared with the shock name, so what you read on screen matches what you tick in the picker.
     */
    fun shortName(typeId: String): String = typeId.removePrefix("minecraft:")
}
