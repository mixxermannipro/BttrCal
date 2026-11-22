package com.bttrcal.app.data.ai

import com.bttrcal.app.domain.model.AiConfig
import com.bttrcal.app.domain.model.AiProviderType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class AiTestResult(
    val success: Boolean,
    val latencyMs: Long,
    val message: String
)

class AiClient(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .build()
) {

    suspend fun complete(
        config: AiConfig,
        apiKey: String?,
        systemPrompt: String,
        userPrompt: String,
        maxTokens: Int = 700,
        temperature: Double = 0.2
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val requiresKey = config.provider != AiProviderType.CUSTOM_OPENAI_COMPATIBLE
            val cleanKey = apiKey?.trim().orEmpty()
            if (requiresKey && cleanKey.isBlank()) {
                error("Kein API-Schlüssel für ${config.provider.displayName} hinterlegt.")
            }

            when (config.provider) {
                AiProviderType.OPENAI, AiProviderType.CUSTOM_OPENAI_COMPATIBLE -> {
                    callOpenAiCompatible(
                        baseUrl = config.baseUrl,
                        model = config.model,
                        apiKey = cleanKey,
                        systemPrompt = systemPrompt,
                        userPrompt = userPrompt,
                        maxTokens = maxTokens,
                        temperature = temperature
                    )
                }
                AiProviderType.GEMINI -> {
                    callGemini(
                        baseUrl = config.baseUrl,
                        model = config.model,
                        apiKey = cleanKey,
                        systemPrompt = systemPrompt,
                        userPrompt = userPrompt,
                        maxTokens = maxTokens,
                        temperature = temperature
                    )
                }
                AiProviderType.ANTHROPIC -> {
                    callAnthropic(
                        baseUrl = config.baseUrl,
                        model = config.model,
                        apiKey = cleanKey,
                        systemPrompt = systemPrompt,
                        userPrompt = userPrompt,
                        maxTokens = maxTokens,
                        temperature = temperature
                    )
                }
            }
        }
    }

    suspend fun testConnection(config: AiConfig, apiKey: String?): AiTestResult {
        val start = System.currentTimeMillis()
        val result = complete(
            config = config,
            apiKey = apiKey,
            systemPrompt = "Antworte nur mit exakt einem Wort: OK",
            userPrompt = "Verbindungstest",
            maxTokens = 16,
            temperature = 0.0
        )
        val latency = (System.currentTimeMillis() - start).coerceAtLeast(1L)
        return result.fold(
            onSuccess = { reply ->
                AiTestResult(
                    success = true,
                    latencyMs = latency,
                    message = "Verbunden mit ${config.provider.displayName} (${config.model}) in ${latency} ms · Antwort: ${reply.take(30)}"
                )
            },
            onFailure = { ex ->
                AiTestResult(
                    success = false,
                    latencyMs = latency,
                    message = "Fehlgeschlagen: ${ex.message ?: "Unbekannter Fehler"}"
                )
            }
        )
    }

    private fun callOpenAiCompatible(
        baseUrl: String,
        model: String,
        apiKey: String,
        systemPrompt: String,
        userPrompt: String,
        maxTokens: Int,
        temperature: Double
    ): String {
        val url = "${baseUrl.trimEnd('/')}/chat/completions"
        val messages = JSONArray()
            .put(JSONObject().put("role", "system").put("content", systemPrompt))
            .put(JSONObject().put("role", "user").put("content", userPrompt))

        val bodyJson = JSONObject().apply {
            put("model", model)
            put("messages", messages)
            put("temperature", temperature)
            put("max_tokens", maxTokens)
        }

        val builder = Request.Builder()
            .url(url)
            .header("Content-Type", "application/json")
            .post(bodyJson.toString().toRequestBody(JSON_MEDIA))
        if (apiKey.isNotBlank()) {
            builder.header("Authorization", "Bearer $apiKey")
        }

        httpClient.newCall(builder.build()).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                error("HTTP ${resp.code}: ${extractErrorMessage(raw)}")
            }
            val json = JSONObject(raw)
            val content = json.getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
            return content.trim()
        }
    }

    private fun callGemini(
        baseUrl: String,
        model: String,
        apiKey: String,
        systemPrompt: String,
        userPrompt: String,
        maxTokens: Int,
        temperature: Double
    ): String {
        val cleanModel = model.removePrefix("models/").trim()
        val url = "${baseUrl.trimEnd('/')}/models/$cleanModel:generateContent?key=$apiKey"

        val bodyJson = JSONObject().apply {
            put(
                "systemInstruction",
                JSONObject().put(
                    "parts",
                    JSONArray().put(JSONObject().put("text", systemPrompt))
                )
            )
            put(
                "contents",
                JSONArray().put(
                    JSONObject().put(
                        "parts",
                        JSONArray().put(JSONObject().put("text", userPrompt))
                    )
                )
            )
            put(
                "generationConfig",
                JSONObject()
                    .put("temperature", temperature)
                    .put("maxOutputTokens", maxTokens)
            )
        }

        val req = Request.Builder()
            .url(url)
            .header("Content-Type", "application/json")
            .post(bodyJson.toString().toRequestBody(JSON_MEDIA))
            .build()

        httpClient.newCall(req).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                error("HTTP ${resp.code}: ${extractErrorMessage(raw)}")
            }
            val json = JSONObject(raw)
            val candidates = json.optJSONArray("candidates") ?: error("Keine Antwort vom Modell")
            val parts = candidates.getJSONObject(0).getJSONObject("content").getJSONArray("parts")
            return buildString {
                for (i in 0 until parts.length()) {
                    append(parts.getJSONObject(i).optString("text", ""))
                }
            }.trim()
        }
    }

    private fun callAnthropic(
        baseUrl: String,
        model: String,
        apiKey: String,
        systemPrompt: String,
        userPrompt: String,
        maxTokens: Int,
        temperature: Double
    ): String {
        val url = "${baseUrl.trimEnd('/')}/messages"
        val bodyJson = JSONObject().apply {
            put("model", model)
            put("max_tokens", maxTokens)
            put("temperature", temperature)
            put("system", systemPrompt)
            put(
                "messages",
                JSONArray().put(
                    JSONObject().put("role", "user").put("content", userPrompt)
                )
            )
        }

        val req = Request.Builder()
            .url(url)
            .header("Content-Type", "application/json")
            .header("x-api-key", apiKey)
            .header("anthropic-version", "2023-06-01")
            .post(bodyJson.toString().toRequestBody(JSON_MEDIA))
            .build()

        httpClient.newCall(req).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                error("HTTP ${resp.code}: ${extractErrorMessage(raw)}")
            }
            val json = JSONObject(raw)
            val contentArr = json.getJSONArray("content")
            return buildString {
                for (i in 0 until contentArr.length()) {
                    val item = contentArr.getJSONObject(i)
                    if (item.optString("type") == "text") {
                        append(item.optString("text", ""))
                    }
                }
            }.trim()
        }
    }

    private fun extractErrorMessage(rawBody: String): String {
        return runCatching {
            val obj = JSONObject(rawBody)
            obj.optJSONObject("error")?.optString("message")?.takeIf { it.isNotBlank() }
                ?: obj.optString("message").takeIf { it.isNotBlank() }
                ?: rawBody.take(180)
        }.getOrElse { rawBody.take(180) }
    }

    companion object {
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    }
}
