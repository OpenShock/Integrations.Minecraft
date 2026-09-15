package openshock.integrations.minecraft.config

import com.google.gson.GsonBuilder
import dev.isxander.yacl3.config.v2.api.ConfigClassHandler
import dev.isxander.yacl3.config.v2.api.SerialEntry
import dev.isxander.yacl3.config.v2.api.serializer.GsonConfigSerializerBuilder
import openshock.integrations.minecraft.platform.McCompat
import openshock.integrations.minecraft.platform.Platform


/**
 * The per-instance half of the config. Everything account-scoped - backend URL, API token,
 * shockers - lives in [AccountConfig] outside of the Minecraft instance, so this file stays safe
 * to ship with a modpack or attach to a bug report.
 */
class ShockCraftConfig {

    // <--- On Damage --->

    @SerialEntry(comment = "Shock on damage?")
    var onDamage: Boolean = true

    @SerialEntry(comment = "How damage shocks you")
    var damageMode: DamageShockMode = DamageShockMode.LowHp

    @SerialEntry
    var intensityMin: Byte = 0

    @SerialEntry
    var intensityMax: Byte = 50

    @SerialEntry
    var durationMin: UShort = 300u

    @SerialEntry
    var durationMax: UShort = 2500u

    @SerialEntry
    var damageThreshold: UInt = 0u

    @SerialEntry
    var cooldown: UShort = 500u

    /**
     * Which kinds of damage may shock you. Everything by default, so an existing config - which
     * has no such key - keeps behaving exactly as it did before the filter existed.
     *
     * Only ever read with `in` and written by adding or removing a single entry: a name this
     * version does not know (an old bucket, or a hand-edited file) deserialises to null, and those
     * operations leave it alone instead of tripping over it.
     */
    @SerialEntry(comment = "Kinds of damage that are allowed to trigger an on damage shock")
    var damageCategories: List<DamageCategory> = DamageCategory.entries.toList()

    @SerialEntry(comment = "Whether on damage shocks are filtered by category or by exact damage type")
    var damageFilterMode: DamageFilterMode = DamageFilterMode.Categories

    /**
     * Damage type ids, `minecraft:cactus` and the like, used instead of [damageCategories] when
     * the mode is [DamageFilterMode.DamageTypes]. Empty by default because the ids that exist
     * depend on the world - there is no sensible list to write here without one.
     */
    @SerialEntry(comment = "Exact damage types allowed to trigger an on damage shock, used when damageFilterMode is DamageTypes")
    var damageTypes: List<String> = ArrayList()


    // <--- On Death --->

    @SerialEntry(comment = "Shock on death?")
    var onDeath: Boolean = true

    @SerialEntry
    var onDeathIntensity: Byte = 50

    @SerialEntry
    var onDeathDuration: UShort = 2500u

    // <--- On Level Up --->

    @SerialEntry(comment = "Shock on level up?")
    var onLevelUp: Boolean = false

    @SerialEntry
    var onLevelUpIntensity: Byte = 20

    @SerialEntry
    var onLevelUpDuration: UShort = 500u

    // <--- On Chat Message --->

    @SerialEntry(comment = "Shock when a specific phrase is sent/received in chat?")
    var onChatEvent: Boolean = false

    @SerialEntry(comment = "The phrase to trigger the shock")
    var chatMessagePhrase: String = "shock me"

    @SerialEntry
    var onChatMessageIntensity: Byte = 30

    @SerialEntry
    var onChatMessageDuration: UShort = 1000u
    
    // <--- General --->

    @SerialEntry
    var displayShocksInActionBar: Boolean = true

    /**
     * Drawn on your screen and nowhere else, which is why it is on by default where the particles
     * and the crackle in [AccountConfig] are a choice about what the room learns. See
     * [openshock.integrations.minecraft.ShockOverlay].
     */
    @SerialEntry
    var shockScreenOverlay: Boolean = true

    companion object {
        const val FILE_NAME: String = "ShockCraft.json5"

        var HANDLER: ConfigClassHandler<ShockCraftConfig> = ConfigClassHandler.createBuilder(ShockCraftConfig::class.java)
            .id(McCompat.identifier("shockcraft", "config"))
            .serializer { config: ConfigClassHandler<ShockCraftConfig?>? ->
                GsonConfigSerializerBuilder.create(config)
                    .setPath(Platform.configDir.resolve(FILE_NAME))
                    .appendGsonBuilder(GsonBuilder::setPrettyPrinting) // not needed, pretty print by default
                    .setJson5(true)
                    .build()
            }
            .build()
    }
}
