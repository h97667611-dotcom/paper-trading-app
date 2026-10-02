package com.papertrader.app.data.casino

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.papertrader.app.data.remote.multi.HttpStatusException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

data class SlotGame(val id: String, val name: String, val provider: String, val thumb: String?, val url: String)
data class SlotProvider(val id: String, val name: String)
data class SlotPage(val games: List<SlotGame>, val page: Int, val lastPage: Int)

/**
 * SlotsLaunch catalog (https://slotslaunch.com/api): free demo slots from many providers.
 * Needs a free API token and the registered origin host. Demo play only, no real money.
 */
object SlotsLaunchClient {
    private const val BASE = "https://slotslaunch.com/api"

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private fun normalizeHost(host: String): String =
        host.trim().removePrefix("https://").removePrefix("http://").trimEnd('/')

    private suspend fun fetch(path: String, token: String, host: String, params: List<Pair<String, String>>): com.google.gson.JsonElement =
        withContext(Dispatchers.IO) {
            val builder = "$BASE$path".toHttpUrl().newBuilder().addQueryParameter("token", token)
            params.forEach { builder.addQueryParameter(it.first, it.second) }
            val request = Request.Builder()
                .url(builder.build())
                .header("Accept", "application/json")
                .header("Origin", "https://" + normalizeHost(host))
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw HttpStatusException(response.code)
                JsonParser.parseString(response.body?.string().orEmpty())
            }
        }

    private fun JsonObject.text(key: String): String =
        get(key)?.takeIf { !it.isJsonNull }?.let { runCatching { it.asString }.getOrNull() }.orEmpty()

    suspend fun games(token: String, host: String, page: Int, providerId: String?): SlotPage {
        val params = arrayListOf("page" to page.toString(), "per_page" to "60", "published" to "1")
        if (providerId != null) params.add("provider[]" to providerId)
        val root = fetch("/games", token, host, params).asJsonObject
        val data = root.getAsJsonArray("data") ?: JsonArray()
        val games = data.mapNotNull { element ->
            val o = element.asJsonObject
            val url = o.text("url")
            if (url.isEmpty()) null
            else SlotGame(
                id = o.text("id").ifEmpty { url },
                name = o.text("name").ifEmpty { "Slot" },
                provider = o.text("provider"),
                thumb = o.text("thumb").ifEmpty { null },
                url = url
            )
        }
        val last = root.get("last_page")?.takeIf { !it.isJsonNull }?.asInt ?: page
        return SlotPage(games, page, last)
    }

    suspend fun providers(token: String, host: String): List<SlotProvider> {
        val root = fetch("/providers", token, host, emptyList())
        val array = if (root.isJsonArray) root.asJsonArray else root.asJsonObject.getAsJsonArray("data") ?: JsonArray()
        return array.mapNotNull { element ->
            val o = element.asJsonObject
            val id = o.text("id")
            val name = o.text("name")
            if (id.isEmpty() || name.isEmpty()) null else SlotProvider(id, name)
        }
    }
}
