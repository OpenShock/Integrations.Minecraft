package openshock.integrations.minecraft.config

import net.minecraft.tags.DamageTypeTags
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.damagesource.DamageTypes
import net.minecraft.world.entity.player.Player

/**
 * The buckets the on-damage filter switches on and off.
 *
 * Sorted by vanilla's damage type tags rather than by a list of damage type ids, so damage from a
 * mod lands in the right bucket without that mod being known here - saying what a damage type is
 * like is exactly what those tags are for.
 *
 * Declaration order is priority order: [of] takes the first bucket that matches, so a source in
 * several tags at once (a blaze fireball is both a projectile and fire) belongs to whichever comes
 * first. Every source lands in exactly one bucket, which is what makes the checkboxes honest -
 * unticking one cannot leave the same damage coming in through another.
 */
enum class DamageCategory(
    val displayName: String,
    val help: String,
    private val matches: (DamageSource) -> Boolean
) {

    Fall(
        "Fall damage",
        "Falling too far",
        { it.`is`(DamageTypeTags.IS_FALL) }
    ),

    Drowning(
        "Drowning, suffocation and hunger",
        "Running out of air, being stuck inside a block or a crowd, or starving",
        {
            it.`is`(DamageTypeTags.IS_DROWNING) ||
                    it.`is`(DamageTypes.IN_WALL) ||
                    it.`is`(DamageTypes.CRAMMING) ||
                    it.`is`(DamageTypes.STARVE)
        }
    ),

    Freezing(
        "Freezing",
        "Standing in powder snow for too long",
        { it.`is`(DamageTypeTags.IS_FREEZING) }
    ),

    Lightning(
        "Lightning",
        "Being struck by lightning",
        { it.`is`(DamageTypeTags.IS_LIGHTNING) }
    ),

    Explosion(
        "Explosions",
        "Creepers, TNT and anything else that goes off",
        { it.`is`(DamageTypeTags.IS_EXPLOSION) }
    ),

    Projectile(
        "Projectiles",
        "Arrows, tridents, fireballs and anything else thrown or shot at you",
        { it.`is`(DamageTypeTags.IS_PROJECTILE) }
    ),

    Fire(
        "Fire and lava",
        "Burning, standing in fire or lava, campfires and magma blocks",
        { it.`is`(DamageTypeTags.IS_FIRE) }
    ),

    // Who is holding the weapon rather than the IS_PLAYER_ATTACK tag: that tag only exists from
    // 1.21 on, and it covers the plain melee type alone, so a harming potion thrown by a player
    // would land in the mob bucket. Whoever the server blamed for the hit is the better question.
    PlayerAttack(
        "Player attacks",
        "Another player going at you, by hand, weapon or potion",
        { it.entity is Player }
    ),

    // Whatever is left holding the weapon is not a player, so it is something the world sent.
    MobAttack(
        "Mob attacks",
        "A mob going at you, by hand or with a weapon",
        { it.entity != null }
    ),

    Other(
        "Anything else",
        "Everything with no bucket of its own - magic, wither, the void, cacti, and damage from mods that does not say what it is like",
        { true }
    );

    companion object {

        /** [Other] covers a source we have no bucket for as well as one the client never got. */
        fun of(source: DamageSource?): DamageCategory {
            if (source == null) return Other
            return entries.first { it.matches(source) }
        }
    }
}
