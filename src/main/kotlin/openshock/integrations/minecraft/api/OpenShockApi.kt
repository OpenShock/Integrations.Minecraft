package openshock.integrations.minecraft.api

import com.google.gson.Gson
import com.google.gson.JsonParseException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import openshock.integrations.minecraft.config.AccountConfig
import openshock.integrations.minecraft.config.ShockCraftConfig
import openshock.integrations.minecraft.platform.McCompat
import openshock.integrations.minecraft.platform.NetClient
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

    private val gson = Gson()

    /**
     * Anything that stopped a request from producing a usable response. The message is written for
     * the user to read, because the config screen puts it on screen when listing shockers fails.
     */
    class ApiException(message: String, cause: Throwable? = null) : Exception(message, cause)

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

    private val userAgent: String by lazy {
        "Integrations.Minecraft/1.0.0 (Minecraft ${Minecraft.getInstance().launchedVersion}; Java ${System.getProperty("java.version")})"
    }

    suspend fun control(type: ControlType, intensity: Byte, duration: UShort, name: String) {
        logger.info("Sending $type with $intensity intensity for $duration ms [$name]")
        val account = AccountConfig.HANDLER.instance()

        val shocks = account.shockers.map { ControlItem(it, type, intensity, duration) }

        val requestObject = ControlRequest(shocks, name + SUFFIX)
        val json = gson.toJson(requestObject)

        // A network failure must not escape into the coroutine's uncaught handler - the shock is
        // fire-and-forget, so log it and give up rather than take the game down with us.
        val response = try {
            val request = requestBuilder(account.apiBaseUrl, account.apiToken, "/2/shockers/control")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build()

            send(request, account.ignoreCertificateErrors)
        } catch (e: ApiException) {
            logger.error("Failed to send $type to the OpenShock API: ${e.message}", e.cause ?: e)
            return
        }

        if (response.statusCode() !in 200..299) {
            logger.error("OpenShock API returned ${response.statusCode()}: ${response.body()}")
            return
        }

        logger.debug(response.body())

        // Only once the backend has taken it. This is the single funnel every control in the mod
        // goes through, so damage, death, level-ups, chat phrases and remotes are all covered
        // here - and each mode is dressed differently at the far end, so a jolt, a buzz and a
        // beep do not look alike to the room. Stop maps to no mode and is not drawn at all.
        //
        // Both effects off means the packet is never sent, so nobody is told anything landed -
        // which is the only way to keep that private, since the packet is the thing that tells
        // them. See ShockedPayload.
        val effect = RemoteMode.fromControl(type)
        if (effect != null && (account.showEffectParticles || account.showEffectSounds)) {
            NetClient.sendShocked(
                effect,
                intensity,
                duration,
                account.showEffectParticles,
                account.showEffectSounds,
            )
        }

        val inSeconds = (duration.toFloat() / 1000f)

        if (!ShockCraftConfig.HANDLER.instance().displayShocksInActionBar) return

        McCompat.sendActionBar(
            Component.literal("$type at $intensity% for ${String.format("%.1f", inSeconds)}s [$name]")
        )
    }

    /**
     * Every shocker the token may control: the account's own ones plus the ones shared with it.
     *
     * The credentials are passed in rather than read from [AccountConfig], so the config screen can
     * try out a token that has been typed in but not saved yet.
     */
    suspend fun fetchShockers(
        baseUrl: String,
        apiToken: String,
        ignoreCertificateErrors: Boolean
    ): List<Shocker> {
        if (apiToken.isBlank()) throw ApiException("No API token set")

        val own = get(baseUrl, apiToken, ignoreCertificateErrors, "/1/shockers/own", OwnShockersResponse::class.java)
        val shared = get(baseUrl, apiToken, ignoreCertificateErrors, "/1/shockers/shared", SharedShockersResponse::class.java)

        val ownShockers = own.data.orEmpty().flatMap { it.toShockers(owner = null) }
        val sharedShockers = shared.data.orEmpty()
            .flatMap { owner -> owner.devices.orEmpty().flatMap { it.toShockers(owner.name) } }

        // Own shockers first - a null owner sorts before any name - then grouped by hub, so the
        // checkboxes in the config screen come out in a stable, readable order.
        return (ownShockers + sharedShockers).sortedWith(compareBy({ it.owner ?: "" }, { it.hub }, { it.name }))
    }

    private suspend fun <T> get(
        baseUrl: String,
        apiToken: String,
        ignoreCertificateErrors: Boolean,
        path: String,
        type: Class<T>
    ): T {
        val response = send(requestBuilder(baseUrl, apiToken, path).GET().build(), ignoreCertificateErrors)

        when (response.statusCode()) {
            in 200..299 -> {}
            401, 403 -> throw ApiException("The API token was rejected")
            404 -> throw ApiException("This backend has no $path - is the API URL right?")
            else -> throw ApiException("The backend answered with HTTP ${response.statusCode()}")
        }

        val parsed: T? = try {
            gson.fromJson(response.body(), type)
        } catch (e: JsonParseException) {
            throw ApiException("The backend sent a response we could not read", e)
        }

        return parsed ?: throw ApiException("The backend sent an empty response")
    }

    private fun requestBuilder(baseUrl: String, apiToken: String, path: String): HttpRequest.Builder =
        try {
            HttpRequest.newBuilder(URI.create(baseUrl.trimEnd('/') + path))
                .header("OpenShockToken", apiToken)
                .header("User-Agent", userAgent)
                .timeout(REQUEST_TIMEOUT)
        } catch (e: IllegalArgumentException) {
            // URI.create and newBuilder both reject a malformed or relative URL this way, and the
            // URL is user input, so it has to come out as an ApiException like every other failure.
            throw ApiException("This is not a valid API URL: $baseUrl", e)
        }

    private suspend fun send(request: HttpRequest, ignoreCertificateErrors: Boolean): HttpResponse<String> =
        withContext(Dispatchers.IO) {
            val http = if (ignoreCertificateErrors) insecureClient else client

            try {
                http.send(request, HttpResponse.BodyHandlers.ofString())
            } catch (e: HttpTimeoutException) {
                // Must precede IOException: HttpTimeoutException is a subclass of it.
                throw ApiException("The backend did not answer within ${REQUEST_TIMEOUT.toSeconds()} seconds", e)
            } catch (e: IOException) {
                throw ApiException(e.message ?: e.javaClass.simpleName, e)
            }
        }
}
