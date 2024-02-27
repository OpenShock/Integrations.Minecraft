package openshock.integrations.minecraft.openshock

import com.google.gson.Gson
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

    private val JSON: MediaType = "application/json".toMediaType()

    private val client: OkHttpClient = OkHttpClient()

    suspend fun control(type: ControlType, intensity: Byte, duration: UShort, name: String? = null) {

        val shocks = ArrayList<ControlItem>()

        ShockCraftConfig.HANDLER.instance().shockers.forEach {
            shocks.add(ControlItem(it, type, intensity, duration))
        }

        val customName = name ?: "Integrations.Minecraft"

        val requestObject = ControlRequest(shocks, customName)
        val json = Gson().toJson(requestObject)

        val url = ShockCraftConfig.HANDLER.instance().apiBaseUrl.toHttpUrl()
        val concatUrl = url.resolve("/2/shockers/control")

        val body: RequestBody = json.toRequestBody(JSON)
        val request: Request = Request.Builder()
            .url(concatUrl!!)
            .header("OpenShockToken", ShockCraftConfig.HANDLER.instance().apiToken)
            .post(body)
            .build()

        val response = client.newCall(request).await()

        ShockCraft.logger.info(response.body!!.string())

        ShockCraft.logger.info("ONLY NOW ???!!")
    }
}