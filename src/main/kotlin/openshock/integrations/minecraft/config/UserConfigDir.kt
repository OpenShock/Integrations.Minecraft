package openshock.integrations.minecraft.config

import openshock.integrations.minecraft.platform.Platform
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * The per-user OpenShock directory, outside of the Minecraft instance.
 *
 * The instance `config/` directory is the one people zip up for a friend, that launchers export
 * with a modpack and that gets attached to bug reports, so it is the wrong place for an API token
 * that can shock its owner. Anything account-scoped lives here instead, which has the pleasant side
 * effect of being shared by every instance and launcher profile on the machine.
 *
 * This is not a security boundary - the token is still plaintext on disk. It only stops the
 * credential from travelling with a copied instance.
 */
object UserConfigDir {

    private val logger = LoggerFactory.getLogger("ShockCraft")

    private const val VENDOR = "OpenShock"

    /** Resolved once: the lookup touches the filesystem and the answer cannot change while we run. */
    val path: Path by lazy { resolve() }

    private fun resolve(): Path {
        val candidate = try {
            osSpecificDir()
        } catch (e: Exception) {
            logger.warn("Failed to determine the user config directory", e)
            null
        } ?: return fallback("no user config directory could be determined for this platform")

        return try {
            Files.createDirectories(candidate)
            // Some launchers redirect %APPDATA% into a sandbox we may not own.
            if (Files.isWritable(candidate)) candidate else fallback("'$candidate' is not writable")
        } catch (e: Exception) {
            logger.warn("Failed to create '{}'", candidate, e)
            fallback("'$candidate' could not be created")
        }
    }

    private fun osSpecificDir(): Path? {
        val os = System.getProperty("os.name").orEmpty().lowercase()
        val home = System.getProperty("user.home")?.takeIf { it.isNotBlank() }

        return when {
            os.contains("win") -> {
                val appData = System.getenv("APPDATA")?.takeIf { it.isNotBlank() }
                if (appData != null) Paths.get(appData, VENDOR)
                else home?.let { Paths.get(it, "AppData", "Roaming", VENDOR) }
            }

            os.contains("mac") || os.contains("darwin") ->
                home?.let { Paths.get(it, "Library", "Application Support", VENDOR) }

            else -> {
                // The XDG spec says a relative XDG_CONFIG_HOME must be ignored.
                val xdg = System.getenv("XDG_CONFIG_HOME")
                    ?.takeIf { it.isNotBlank() }
                    ?.let { Paths.get(it) }
                    ?.takeIf { it.isAbsolute }

                if (xdg != null) xdg.resolve(VENDOR)
                else home?.let { Paths.get(it, ".config", VENDOR) }
            }
        }
    }

    /**
     * Better a token in the instance config than a mod that cannot save at all, so portable and
     * sandboxed setups quietly keep the old behaviour.
     */
    private fun fallback(reason: String): Path {
        logger.warn("Falling back to the instance config directory for OpenShock credentials: {}", reason)
        return Platform.configDir
    }
}
