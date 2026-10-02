package com.papertrader.app.data.casino

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
import java.security.GeneralSecurityException
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64
import java.util.concurrent.TimeUnit

data class Hub88Game(
    val code: String,
    val name: String,
    val product: String,
    val category: String,
    val thumb: String?
)

class Hub88Exception(val code: Int, val detail: String) : IOException("HTTP $code")

/**
 * Hub88 Operator Games API (https://docs.hub88.io). Every request body is signed with the
 * operator's RSA private key (SHA256withRSA, Base64) and sent in the X-Hub88-Signature header.
 * Only the game list and DEMO launches (currency "XXX") are used here, so no wallet is involved.
 */
object Hub88Client {
    private val JSON_TYPE = "application/json".toMediaType()

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private fun parsePrivateKey(pem: String): PrivateKey {
        if (pem.contains("BEGIN RSA PRIVATE KEY")) throw IllegalArgumentException("pkcs1")
        val cleaned = pem
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replace("\\s".toRegex(), "")
        val bytes = Base64.getDecoder().decode(cleaned)
        return KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(bytes))
    }

    private fun sign(body: String, pem: String): String {
        val signature = Signature.getInstance("SHA256withRSA")
        signature.initSign(parsePrivateKey(pem))
        signature.update(body.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(signature.sign())
    }

    private suspend fun post(baseUrl: String, path: String, body: String, privateKey: String): JsonElement =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(baseUrl.trim().trimEnd('/') + path)
                .header("X-Hub88-Signature", sign(body, privateKey))
                .header("Accept", "application/json")
                .post(body.toRequestBody(JSON_TYPE))
                .build()
            client.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw Hub88Exception(response.code, text.take(160))
                JsonParser.parseString(text)
            }
        }

    private fun JsonObject.text(key: String): String =
        get(key)?.takeIf { !it.isJsonNull }?.let { runCatching { it.asString }.getOrNull() }.orEmpty()

    /** All games that have a demo mode. The list is big, so it is loaded once and filtered locally. */
    suspend fun demoGames(baseUrl: String, operatorId: Long, privateKey: String): List<Hub88Game> {
        val body = JsonObject().apply { addProperty("operator_id", operatorId) }.toString()
        val root = post(baseUrl, "/operator/generic/v2/game/list", body, privateKey)
        return root.asJsonArray.mapNotNull { element ->
            val o = element.asJsonObject
            val demo = o.get("demo_game_support")?.takeIf { !it.isJsonNull }?.asBoolean ?: false
            val enabled = o.get("enabled")?.takeIf { !it.isJsonNull }?.asBoolean ?: true
            val code = o.text("game_code")
            if (!demo || !enabled || code.isEmpty()) null
            else Hub88Game(
                code = code,
                name = o.text("name").ifEmpty { code },
                product = o.text("product"),
                category = o.text("category"),
                thumb = o.text("url_thumb").ifEmpty { null }
            )
        }
    }

    /** Landing URL of one game in DEMO mode (no user, no token, currency XXX). */
    suspend fun demoUrl(baseUrl: String, operatorId: Long, privateKey: String, gameCode: String, lobbyUrl: String): String {
        val body = JsonObject().apply {
            addProperty("operator_id", operatorId)
            addProperty("game_code", gameCode)
            addProperty("platform", "GPL_MOBILE")
            addProperty("lang", "en")
            addProperty("lobby_url", lobbyUrl)
            addProperty("currency", "XXX")
            addProperty("game_currency", "XXX")
            addProperty("country", "XX")
        }.toString()
        val root = post(baseUrl, "/operator/generic/v2/game/url", body, privateKey)
        return root.asJsonObject.get("url")?.takeIf { !it.isJsonNull }?.asString
            ?: throw IOException("no url in response")
    }

    fun describeError(e: Exception): String = when {
        e is Hub88Exception && (e.code == 401 || e.code == 403) ->
            "Hub88 rejected the request (HTTP ${e.code}). Check operator ID, region URL and that your public key is registered with Hub88."
        e is Hub88Exception && e.code == 404 ->
            "Hub88 could not open this game. Not every game has a demo mode. ${e.detail}"
        e is Hub88Exception -> "Hub88 answered with HTTP ${e.code}. ${e.detail}"
        e is IllegalArgumentException && e.message == "pkcs1" ->
            "The private key must be PKCS#8 (starts with BEGIN PRIVATE KEY). Convert it with: openssl pkcs8 -topk8 -nocrypt -in key.pem"
        e is GeneralSecurityException || e is IllegalArgumentException ->
            "The private key or the API URL could not be read. Check the PEM text and the URL."
        e is IOException -> "No connection or unexpected answer from Hub88."
        else -> "Unexpected response from Hub88."
    }
}
