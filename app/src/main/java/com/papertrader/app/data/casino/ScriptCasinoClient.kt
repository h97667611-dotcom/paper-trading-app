package com.papertrader.app.data.casino

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.URLEncoder
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

data class CasinoGame(
    val uuid: String,
    val name: String,
    val provider: String,
    val category: String,
    val thumb: String?
)

data class CasinoGamePage(val games: List<CasinoGame>, val page: Int, val lastPage: Int?, val received: Int)

class CasinoApiException(val code: Int, val detail: String) : IOException("HTTP $code")

/**
 * Script.Casino Games API (https://p1.docs-casino.xyz/docs): game catalog and DEMO launches.
 *
 * Requests carry X-Merchant-Id, X-Timestamp, X-Nonce and X-Sign. X-Sign is the hex HMAC-SHA1 of the
 * alphabetically sorted, URL-encoded query/body parameters merged with the three header values,
 * using the merchant secret. The documentation only says "HMAC-SHA1 ... merge POST params into the
 * sign calculation", so this follows the common scheme of this API family and is not verified
 * without merchant credentials.
 */
object ScriptCasinoClient {
    private const val API_VERSION = "2026-06-09"
    private const val PER_PAGE = 100
    private val FORM_TYPE = "application/x-www-form-urlencoded".toMediaType()

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

    private fun buildQuery(params: Map<String, String>): String =
        params.toSortedMap().entries.joinToString("&") { "${encode(it.key)}=${encode(it.value)}" }

    private fun hmacSha1Hex(data: String, key: String): String {
        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(key.toByteArray(Charsets.UTF_8), "HmacSHA1"))
        return mac.doFinal(data.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }

    private suspend fun call(
        baseUrl: String,
        merchantId: String,
        secret: String,
        method: String,
        path: String,
        params: Map<String, String>
    ): JsonElement = withContext(Dispatchers.IO) {
        val timestamp = (System.currentTimeMillis() / 1000).toString()
        val nonce = UUID.randomUUID().toString().replace("-", "")
        val signed = params + mapOf("X-Merchant-Id" to merchantId, "X-Timestamp" to timestamp, "X-Nonce" to nonce)
        val sign = hmacSha1Hex(buildQuery(signed), secret)
        val base = baseUrl.trim().trimEnd('/')
        val builder = Request.Builder()
            .header("X-Merchant-Id", merchantId)
            .header("X-Timestamp", timestamp)
            .header("X-Nonce", nonce)
            .header("X-Sign", sign)
            .header("X-Api-Version", API_VERSION)
            .header("Accept", "application/json")
        val request = if (method == "GET") {
            val query = if (params.isEmpty()) "" else "?" + buildQuery(params)
            builder.url(base + path + query).get().build()
        } else {
            builder.url(base + path).post(buildQuery(params).toRequestBody(FORM_TYPE)).build()
        }
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw CasinoApiException(response.code, text.take(160))
            JsonParser.parseString(text)
        }
    }

    private fun JsonObject.firstText(vararg keys: String): String {
        for (key in keys) {
            val el = get(key) ?: continue
            if (el.isJsonNull) continue
            val text = when {
                el.isJsonPrimitive -> runCatching { el.asString }.getOrNull()
                el.isJsonObject -> el.asJsonObject.get("name")?.takeIf { it.isJsonPrimitive }?.asString
                else -> null
            }
            if (!text.isNullOrEmpty()) return text
        }
        return ""
    }

    private fun extractArray(root: JsonElement): JsonArray {
        if (root.isJsonArray) return root.asJsonArray
        if (!root.isJsonObject) return JsonArray()
        for (key in listOf("items", "data", "games", "results")) {
            val el = root.asJsonObject.get(key)
            if (el != null && el.isJsonArray) return el.asJsonArray
        }
        return JsonArray()
    }

    private fun pageCount(root: JsonObject): Int? {
        for (container in listOf("_meta", "meta", "pagination")) {
            val meta = root.get(container)?.takeIf { it.isJsonObject }?.asJsonObject ?: continue
            for (key in listOf("pageCount", "last_page", "total_pages", "pages")) {
                val value = meta.get(key)?.takeIf { it.isJsonPrimitive }
                    ?.let { runCatching { it.asInt }.getOrNull() }
                if (value != null) return value
            }
        }
        return null
    }

    /** One catalog page (up to 100 games). The catalog is rate limited, so it is loaded page by page. */
    suspend fun games(baseUrl: String, merchantId: String, secret: String, page: Int): CasinoGamePage {
        val params = mapOf("page" to page.toString(), "per_page" to PER_PAGE.toString())
        val root = call(baseUrl, merchantId, secret, "GET", "/catalog/games", params)
        val array = extractArray(root)
        val games = array.mapNotNull { element ->
            if (!element.isJsonObject) return@mapNotNull null
            val o = element.asJsonObject
            val uuid = o.firstText("uuid", "game_uuid", "id")
            if (uuid.isEmpty()) return@mapNotNull null
            CasinoGame(
                uuid = uuid,
                name = o.firstText("name", "title").ifEmpty { uuid },
                provider = o.firstText("provider", "provider_name"),
                category = o.firstText("category", "type"),
                thumb = o.firstText("image", "thumbnail", "image_url", "thumb").ifEmpty { null }
            )
        }
        val last = if (root.isJsonObject) pageCount(root.asJsonObject) else null
        return CasinoGamePage(games, page, last, array.size())
    }

    val pageSize: Int get() = PER_PAGE

    /** Launch URL of one game in demo mode (no player, no wallet). */
    suspend fun demoUrl(baseUrl: String, merchantId: String, secret: String, gameUuid: String, returnUrl: String): String {
        val params = mapOf(
            "game_uuid" to gameUuid,
            "language" to "en",
            "device" to "mobile",
            "return_url" to returnUrl
        )
        val root = call(baseUrl, merchantId, secret, "POST", "/sessions/launch/demo", params)
        if (root.isJsonObject) {
            val obj = root.asJsonObject
            val direct = obj.firstText("url", "launch_url", "redirect_url", "game_url")
            if (direct.isNotEmpty()) return direct
            val nested = obj.get("data")?.takeIf { it.isJsonObject }?.asJsonObject
            val inner = nested?.firstText("url", "launch_url", "redirect_url", "game_url").orEmpty()
            if (inner.isNotEmpty()) return inner
        }
        throw IOException("no launch url in response")
    }

    fun describeError(e: Exception): String = when {
        e is CasinoApiException && e.detail.contains("Invalid merchant", ignoreCase = true) ->
            "The casino API does not know this merchant ID."
        e is CasinoApiException && (e.code == 401 || e.code == 403) ->
            "Authentication failed (HTTP ${e.code}). Check merchant ID, secret key and base URL. ${e.detail}"
        e is CasinoApiException && e.code == 404 -> "This game could not be opened in demo mode. ${e.detail}"
        e is CasinoApiException && e.code == 429 -> "The casino catalog is rate limited. Try again in a minute."
        e is CasinoApiException -> "The casino API answered with HTTP ${e.code}. ${e.detail}"
        e is IllegalArgumentException -> "The base URL looks invalid."
        e is IOException -> "No connection or unexpected answer from the casino API."
        else -> "Unexpected response from the casino API."
    }
}
