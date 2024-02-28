package openshock.integrations.minecraft.config

import com.google.gson.GsonBuilder
import dev.isxander.yacl3.config.v2.api.ConfigClassHandler
import dev.isxander.yacl3.config.v2.api.SerialEntry
import dev.isxander.yacl3.config.v2.api.serializer.GsonConfigSerializerBuilder
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.util.Identifier


class ShockCraftConfig {

    // <--- Server --->

    @SerialEntry(comment = "Base API Url of the OpenShock Backend. Official instance: https://api.shocklink.net")
    var apiBaseUrl: String = "https://api.shocklink.net"

    @SerialEntry(comment = "API Token generated on the web")
    var apiToken: String = ""


    // <--- Shockers --->

    @SerialEntry(comment = "Shockers to use")
    var shockers: List<String> = ArrayList()


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


    // <--- On Death --->

    @SerialEntry(comment = "Shock on death?")
    var onDeath: Boolean = true

    @SerialEntry
    var onDeathIntensity: Byte = 50

    @SerialEntry
    var onDeathDuration: UShort = 2500u





    companion object {
        var HANDLER: ConfigClassHandler<ShockCraftConfig> = ConfigClassHandler.createBuilder(ShockCraftConfig::class.java)
            .id(Identifier("shockcraft", "config"))
            .serializer { config: ConfigClassHandler<ShockCraftConfig?>? ->
                GsonConfigSerializerBuilder.create(config)
                    .setPath(FabricLoader.getInstance().configDir.resolve("ShockCraft.json5"))
                    .appendGsonBuilder(GsonBuilder::setPrettyPrinting) // not needed, pretty print by default
                    .setJson5(true)
                    .build()
            }
            .build()
    }
}