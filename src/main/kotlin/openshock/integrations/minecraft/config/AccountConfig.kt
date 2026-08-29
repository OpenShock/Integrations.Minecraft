package openshock.integrations.minecraft.config

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.stream.JsonReader
import dev.isxander.yacl3.config.v2.api.ConfigClassHandler
import dev.isxander.yacl3.config.v2.api.SerialEntry
import dev.isxander.yacl3.config.v2.api.serializer.GsonConfigSerializerBuilder
import openshock.integrations.minecraft.platform.McCompat
import openshock.integrations.minecraft.platform.Platform
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path

/**
 * The account-scoped half of the config: which backend we talk to, the credential we talk to it
 * with, and which shockers that account owns. None of it belongs to a single Minecraft instance,
 * and the token in particular must not travel with one, so this lives in [UserConfigDir] while
 * [ShockCraftConfig] keeps the per-instance behaviour settings.
 */
class AccountConfig {

    @SerialEntry(comment = "Base API Url of the OpenShock Backend. Official instance: https://api.openshock.app")
    var apiBaseUrl: String = "https://api.openshock.app"

    @SerialEntry(comment = "API Token generated on the web. Keep this to yourself - anyone holding it can shock you")
    var apiToken: String = ""

    @SerialEntry(comment = "Accept TLS certificates that are not signed by a trusted CA (self-signed). Only for self hosted instances, this disables a security check")
    var ignoreCertificateErrors: Boolean = false

    @SerialEntry(comment = "Shockers to use")
    var shockers: List<String> = ArrayList()

    companion object {

        private val logger = LoggerFactory.getLogger("ShockCraft")

        /** Under a per-mod folder so the other OpenShock integrations can share the vendor directory. */
        val path: Path by lazy { UserConfigDir.path.resolve("ShockCraft").resolve("account.json5") }

        var HANDLER: ConfigClassHandler<AccountConfig> = ConfigClassHandler.createBuilder(AccountConfig::class.java)
            .id(McCompat.identifier("shockcraft", "account"))
            .serializer { config: ConfigClassHandler<AccountConfig?>? ->
                GsonConfigSerializerBuilder.create(config)
                    .setPath(path)
                    .appendGsonBuilder(GsonBuilder::setPrettyPrinting)
                    .setJson5(true)
                    .build()
            }
            .build()

        /**
         * Loads the account config, pulling the values out of the older instance config the
         * first time around.
         *
         * @return whether a migration happened, in which case the caller must re-save
         * [ShockCraftConfig] so the credential stops living in the instance config as well.
         */
        fun loadOrMigrate(): Boolean {
            if (Files.notExists(path) && migrateFromInstanceConfig()) {
                HANDLER.save()
                return true
            }

            HANDLER.load()
            return false
        }

        /** Copies the legacy fields onto the working instance. Returns whether anything was found. */
        private fun migrateFromInstanceConfig(): Boolean {
            val legacyPath = Platform.configDir.resolve(ShockCraftConfig.FILE_NAME)
            val legacy = readLegacy(legacyPath) ?: return false

            val instance = HANDLER.instance()
            var found = false

            legacy.string("apiBaseUrl")?.let { instance.apiBaseUrl = it; found = true }
            legacy.string("apiToken")?.let { instance.apiToken = it; found = true }
            legacy.boolean("ignoreCertificateErrors")?.let { instance.ignoreCertificateErrors = it; found = true }
            legacy.strings("shockers")?.let { instance.shockers = it; found = true }

            if (found) logger.info("Migrated OpenShock credentials from '{}' to '{}'", legacyPath, path)
            return found
        }

        /**
         * Reads the legacy file by hand rather than through YACL: the fields are gone from
         * [ShockCraftConfig] by now, so the deserializer would simply skip them.
         */
        private fun readLegacy(legacyPath: Path): JsonObject? {
            if (Files.notExists(legacyPath)) return null

            return try {
                Files.newBufferedReader(legacyPath).use { reader ->
                    // Lenient covers the json5-isms YACL writes - comments and unquoted names.
                    val json = JsonReader(reader)
                    json.isLenient = true
                    JsonParser.parseReader(json) as? JsonObject
                }
            } catch (e: Exception) {
                logger.warn("Failed to read '{}' for credential migration", legacyPath, e)
                null
            }
        }

        private fun JsonObject.string(name: String): String? =
            get(name)?.takeIf { it.isJsonPrimitive }?.asString?.takeIf { it.isNotBlank() }

        private fun JsonObject.boolean(name: String): Boolean? =
            get(name)?.takeIf { it.isJsonPrimitive }?.asBoolean

        private fun JsonObject.strings(name: String): List<String>? =
            get(name)?.takeIf { it.isJsonArray }?.asJsonArray
                ?.mapNotNull { it.takeIf { e -> e.isJsonPrimitive }?.asString }
                ?.takeIf { it.isNotEmpty() }
    }
}
