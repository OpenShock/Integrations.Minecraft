package openshock.integrations.minecraft.openshock

import com.google.gson.Gson
import net.minecraft.client.MinecraftClient
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import openshock.integrations.minecraft.ShockCraft
import openshock.integrations.minecraft.config.ShockCraftConfig
import ru.gildor.coroutines.okhttp.await


object OpenShockApi {

    private const val SUFFIX: String = " (Integrations.Minecraft)"
    private val JSON: MediaType = "application/json".toMediaType()

    private val client: OkHttpClient = OkHttpClient()

    suspend fun control(type: ControlType, intensity: Byte, duration: UShort, name: String) {
        ShockCraft.logger.info("Sending $type with $intensity intensity for $duration ms [$name]")
        val shocks = ArrayList<ControlItem>()

        ShockCraftConfig.HANDLER.instance().shockers.forEach {
            shocks.add(ControlItem(it, type, intensity, duration))
        }

        val requestObject = ControlRequest(shocks, name + SUFFIX)
        val json = Gson().toJson(requestObject)

        val url = ShockCraftConfig.HANDLER.instance().apiBaseUrl.toHttpUrl()
        val concatUrl = url.resolve("/2/shockers/control")

        val body: RequestBody = json.toRequestBody(JSON)
        val request: Request = Request.Builder()
            .url(concatUrl!!)
            .header("OpenShockToken", ShockCraftConfig.HANDLER.instance().apiToken)
            .header("User-Agent", "Integrations.Minecraft/1.0.0 (Minecraft ${MinecraftClient.getInstance().gameVersion}; Java ${System.getProperty("java.version")})")
            .post(body)
            .build()

        val response = client.newCall(request).await()

        ShockCraft.logger.info(response.body!!.string())
    }
}