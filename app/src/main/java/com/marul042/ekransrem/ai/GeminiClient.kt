package com.marul042.ekransrem.ai

import com.marul042.ekransrem.BuildConfig
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

sealed interface GeminiResult {
    data class Success(val text: String) : GeminiResult
    data object MissingApiKey : GeminiResult
    data object TemporaryFailure : GeminiResult
    data object PermanentFailure : GeminiResult
}

class GeminiClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()
) {
    fun summarize(prompt: String): GeminiResult {
        val key = BuildConfig.GEMINI_API_KEY.trim()
        if (key.isEmpty()) return GeminiResult.MissingApiKey
        val body = JSONObject().put("contents", org.json.JSONArray().put(JSONObject().put("parts", org.json.JSONArray().put(JSONObject().put("text", prompt))))).toString()
        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$key")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()
        return runCatching {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    JSONObject(response.body?.string().orEmpty())
                    .optJSONArray("candidates")?.optJSONObject(0)
                    ?.optJSONObject("content")?.optJSONArray("parts")?.optJSONObject(0)
                    ?.optString("text")?.takeIf { it.isNotBlank() }
                    ?.let(GeminiResult::Success)
                    ?: GeminiResult.PermanentFailure
                } else if (response.code in 408..499 || response.code >= 500) {
                    if (response.code == 429 || response.code >= 500 || response.code == 408) {
                        GeminiResult.TemporaryFailure
                    } else {
                        GeminiResult.PermanentFailure
                    }
                } else {
                    GeminiResult.PermanentFailure
                }
            }
        }.getOrElse { GeminiResult.TemporaryFailure }
    }
}
