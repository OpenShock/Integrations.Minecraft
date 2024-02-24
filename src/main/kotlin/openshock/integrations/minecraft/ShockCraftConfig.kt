package openshock.integrations.minecraft

import com.google.gson.GsonBuilder
import dev.isxander.yacl3.config.v2.api.ConfigClassHandler
import dev.isxander.yacl3.config.v2.api.SerialEntry
import dev.isxander.yacl3.config.v2.api.serializer.GsonConfigSerializerBuilder
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.util.Identifier


class ShockCraftConfig {

    @SerialEntry(comment = "Base API Url of the OpenShock Backend. Official instance: https://api.shocklink.net")
    var apiBaseUrl: String = "https://api.shocklink.net"

    @SerialEntry(comment = "API Token generated on the web")
    var apiToken: String = ""

    @SerialEntry(comment = "Intensity Min")
    var intensityMin: Int = 0;

    @SerialEntry(comment = "Intensity Max")
    var intensityMax: Int = 100;


    @SerialEntry(comment = "Shockers to use")
    var shockers: List<String> = ArrayList()

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