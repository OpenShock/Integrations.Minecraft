package openshock.integrations.minecraft.api

import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import openshock.integrations.minecraft.config.ShockCraftConfig
import openshock.integrations.minecraft.platform.McCompat
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

object OpenShockApi {

    private val logger = LoggerFactory.getLogger("OpenShockApi")

    private const val SUFFIX: String = " (Integrations.Minecraft)"

    // The JDK client keeps us free of a shaded HTTP library, which would otherwise have to be
    // bundled differently for each loader.
    private val client: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build()

    suspend fun control(type: ControlType, intensity: Byte, duration: UShort, name: String) {
        logger.info("Sending $type with $intensity intensity for $duration ms [$name]")
        val config = ShockCraftConfig.HANDLER.instance()

        val shocks = config.shockers.map { ControlItem(it, type, intensity, duration) }

        val requestObject = ControlRequest(shocks, name + SUFFIX)
        val json = Gson().toJson(requestObject)

        val url = URI.create(config.apiBaseUrl.trimEnd('/') + "/2/shockers/control")

        val request = HttpRequest.newBuilder(url)
            .header("Content-Type", "application/json")
            .header("OpenShockToken", config.apiToken)
            .header(
                "User-Agent",
                "Integrations.Minecraft/1.0.0 (Minecraft ${Minecraft.getInstance().launchedVersion}; Java ${System.getProperty("java.version")})"
            )
            .POST(HttpRequest.BodyPublishers.ofString(json))
            .build()

        val response = withContext(Dispatchers.IO) {
            client.send(request, HttpResponse.BodyHandlers.ofString())
        }

        logger.debug(response.body())

        val inSeconds = (duration.toFloat() / 1000f)

        if (!config.displayShocksInActionBar) return

        McCompat.sendActionBar(
            Component.literal("$type at $intensity% for ${String.format("%.1f", inSeconds)}s [$name]")
        )
    }
}
