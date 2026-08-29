package openshock.integrations.minecraft.api

import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import openshock.integrations.minecraft.config.ShockCraftConfig
import openshock.integrations.minecraft.platform.McCompat
import org.slf4j.LoggerFactory
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.HttpTimeoutException
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.time.Duration
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

object OpenShockApi {

    private val logger = LoggerFactory.getLogger("OpenShockApi")

    private const val SUFFIX: String = " (Integrations.Minecraft)"

    private val CONNECT_TIMEOUT: Duration = Duration.ofSeconds(10)

    // connectTimeout only bounds connection setup, so without this a stalled server would leave
    // the request hanging on a Dispatchers.IO thread forever.
    private val REQUEST_TIMEOUT: Duration = Duration.ofSeconds(15)

    // The JDK client keeps us free of a shaded HTTP library, which would otherwise have to be
    // bundled differently for each loader. OkHttp followed redirects by default; the JDK client
    // does not, so it has to be asked for explicitly or a 3xx would silently drop the POST.
    private val client: HttpClient = newClientBuilder().build()

    // A separate client rather than a reconfigured shared one, so the validating path stays
    // untouched, and lazy so the trust-everything context only ever exists if the user opted in.
    private val insecureClient: HttpClient by lazy {
        logger.warn("Certificate validation is disabled - the connection to the OpenShock API is not protected against interception")

        val trustEverything = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }

        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, arrayOf<TrustManager>(trustEverything), SecureRandom())

        newClientBuilder().sslContext(sslContext).build()
    }

    private fun newClientBuilder(): HttpClient.Builder = HttpClient.newBuilder()
        .connectTimeout(CONNECT_TIMEOUT)
        .followRedirects(HttpClient.Redirect.NORMAL)

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
            .timeout(REQUEST_TIMEOUT)
            .build()

        // A network failure must not escape into the coroutine's uncaught handler - the shock is
        // fire-and-forget, so log it and give up rather than take the game down with us.
        val response = try {
            withContext(Dispatchers.IO) {
                val http = if (config.ignoreCertificateErrors) insecureClient else client
                http.send(request, HttpResponse.BodyHandlers.ofString())
            }
        } catch (e: HttpTimeoutException) {
            // Must precede IOException: HttpTimeoutException is a subclass of it.
            logger.error("Timed out sending $type to the OpenShock API after $REQUEST_TIMEOUT", e)
            return
        } catch (e: IOException) {
            logger.error("Failed to send $type to the OpenShock API", e)
            return
        }

        if (response.statusCode() !in 200..299) {
            logger.error("OpenShock API returned ${response.statusCode()}: ${response.body()}")
            return
        }

        logger.debug(response.body())

        val inSeconds = (duration.toFloat() / 1000f)

        if (!config.displayShocksInActionBar) return

        McCompat.sendActionBar(
            Component.literal("$type at $intensity% for ${String.format("%.1f", inSeconds)}s [$name]")
        )
    }
}
