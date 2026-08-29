package openshock.integrations.minecraft.config

import dev.isxander.yacl3.api.NameableEnum
import net.minecraft.network.chat.Component

/** Which of the two lists in the config screen decides whether a hit is allowed to shock. */
enum class DamageFilterMode(private val displayName: String) : NameableEnum {

    /** The [DamageCategory] checkboxes, which sort every kind of damage into one of nine buckets. */
    Categories("Categories"),

    /** The exact damage type ids, for when the buckets are not fine grained enough. */
    DamageTypes("Exact damage types");

    override fun getDisplayName(): Component = Component.literal(displayName)
}
