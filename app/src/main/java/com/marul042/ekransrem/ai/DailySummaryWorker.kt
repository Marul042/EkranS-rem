package com.marul042.ekransrem.ai

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.marul042.ekransrem.data.SwipeDatabase
import com.marul042.ekransrem.data.UsageDateRange
import com.marul042.ekransrem.data.UsageDay
import com.marul042.ekransrem.data.UsageRepository
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class DailySummaryWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val preferences = applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val language = preferences.getString("language", "en") ?: "en"
        val day = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val swipes = SwipeDatabase.get(applicationContext).swipeDao().totalForDay(day)
        val usage = UsageRepository(applicationContext).getUsage(UsageDateRange.forDay(UsageDay.TODAY), false)
        val report = JSONObject().apply {
            put("date", day)
            put("swipes", swipes)
            put("totalTimeMs", usage.sumOf { it.totalTimeMs })
            put("apps", JSONArray().apply {
                usage.forEach { app ->
                    put(JSONObject().put("packageName", app.packageName).put("label", app.label).put("timeMs", app.totalTimeMs))
                }
            })
        }
        val prompt = if (language == "tr") {
            "Bu JSON raporuna göre Türkçe, kısa ve yargılamayan bir dijital denge önerisi yaz: $report"
        } else {
            "Based on this JSON report, write a short, non-judgmental digital balance recommendation in English: $report"
        }
        return when (val result = GeminiClient().summarize(prompt)) {
            is GeminiResult.Success -> {
                preferences.edit()
                    .putString("daily_summary", result.text)
                    .putString("daily_summary_date", day)
                    .apply()
                Result.success()
            }
            GeminiResult.MissingApiKey, GeminiResult.PermanentFailure -> Result.success()
            GeminiResult.TemporaryFailure -> Result.retry()
        }
    }
}
