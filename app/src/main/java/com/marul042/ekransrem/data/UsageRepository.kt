package com.marul042.ekransrem.data

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Process
import java.util.Calendar

data class AppUsage(
    val packageName: String,
    val label: String,
    val icon: android.graphics.drawable.Drawable,
    val totalTimeMs: Long,
    val swipeCount: Int = 0
)

enum class UsageDay { TODAY, YESTERDAY }

enum class AppCategory {
    ALL,      // Tüm Uygulamalar
    GAMES,    // Oyunlar
    SOCIAL,   // Sosyal
    SYSTEM;   // Sistem

    fun matches(appInfo: ApplicationInfo?): Boolean {
        if (appInfo == null) return false
        return when (this) {
            ALL -> true
            GAMES -> appInfo.category == ApplicationInfo.CATEGORY_GAME
            SOCIAL -> {
                // Simple social detection based on package name patterns
                val socialPatterns = listOf(
                    "twitter", "facebook", "instagram", "tiktok", "telegram", 
                    "whatsapp", "snapchat", "reddit", "discord", "pinterest"
                )
                socialPatterns.any { appInfo.packageName.contains(it, ignoreCase = true) }
            }
            SYSTEM -> {
                val isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                val isUpdatedSystemApp = (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
                isSystemApp && !isUpdatedSystemApp
            }
        }
    }
}

/**
 * Represents usage data for a single hour (00:00-00:59, 01:00-01:59, etc.)
 */
data class HourlyUsage(
    val hour: Int,                    // 0-23
    val totalTimeMs: Long,            // Total screen time in this hour
    val totalSwipeCount: Int = 0      // Total swipes in this hour
) {
    val displayHour: String get() = String.format("%02d:00", hour)
}

data class UsageDateRange(
    val startOfDayMillis: Long,
    val endOfDayMillis: Long
) {
    companion object {
        fun forDay(day: UsageDay, now: Calendar = Calendar.getInstance()): UsageDateRange {
            val selectedDay = (now.clone() as Calendar).apply {
                if (day == UsageDay.YESTERDAY) add(Calendar.DAY_OF_YEAR, -1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val endOfDay = (selectedDay.clone() as Calendar).apply {
                set(Calendar.HOUR_OF_DAY, 23)
                set(Calendar.MINUTE, 59)
                set(Calendar.SECOND, 59)
                set(Calendar.MILLISECOND, 999)
            }
            val endMillis = if (day == UsageDay.TODAY) {
                minOf(endOfDay.timeInMillis, now.timeInMillis)
            } else {
                endOfDay.timeInMillis
            }
            return UsageDateRange(selectedDay.timeInMillis, endMillis)
        }

        fun forDate(dateMillis: Long): UsageDateRange {
            val selectedDay = Calendar.getInstance().apply {
                timeInMillis = dateMillis
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val endOfDay = (selectedDay.clone() as Calendar).apply {
                set(Calendar.HOUR_OF_DAY, 23)
                set(Calendar.MINUTE, 59)
                set(Calendar.SECOND, 59)
                set(Calendar.MILLISECOND, 999)
            }
            val now = System.currentTimeMillis()
            return UsageDateRange(selectedDay.timeInMillis, minOf(endOfDay.timeInMillis, now))
        }
    }
}

class UsageRepository(private val context: Context) {
    private val packageManager = context.packageManager
    private val usageStatsManager = context.getSystemService(UsageStatsManager::class.java)

    fun hasUsageAccess(): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java)
        return appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        ) == AppOpsManager.MODE_ALLOWED
    }

    fun getUsage(dateRange: UsageDateRange, gamesOnly: Boolean): List<AppUsage> {
        // Query usage stats for the date range
        // We query from the start of the day to the end to ensure we capture all usage
        val stats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            dateRange.startOfDayMillis,
            dateRange.endOfDayMillis
        ) ?: return emptyList()

        return stats.asSequence()
            .filter { it.totalTimeInForeground > 0L }
            .mapNotNull { stat ->
                runCatching {
                    val appInfo = packageManager.getApplicationInfo(stat.packageName, 0)
                    if (appInfo.packageName != context.packageName && !isUserVisibleApp(appInfo)) {
                        return@mapNotNull null
                    }
                    if (gamesOnly && appInfo.category != ApplicationInfo.CATEGORY_GAME) {
                        return@mapNotNull null
                    }
                    
                    // Clip the usage time at the day boundary
                    // Sessions can span across multiple days, so we need to clip them
                    val clippedTime = clipSessionToDayBoundary(
                        stat.firstTimeStamp,
                        stat.lastTimeStamp,
                        stat.totalTimeInForeground,
                        dateRange
                    )
                    
                    AppUsage(
                        packageName = stat.packageName,
                        label = packageManager.getApplicationLabel(appInfo).toString(),
                        icon = packageManager.getApplicationIcon(appInfo),
                        totalTimeMs = clippedTime
                    )
                }.getOrNull()
            }
            .groupingBy(AppUsage::packageName)
            .reduce { _, accumulated, current ->
                accumulated.copy(totalTimeMs = accumulated.totalTimeMs + current.totalTimeMs)
            }
            .values
            .sortedByDescending(AppUsage::totalTimeMs)
            .toList()
    }

    /**
     * Get hourly usage breakdown for a specific date range.
     * Returns an array of 24 HourlyUsage objects (one for each hour 0-23).
     */
    fun getHourlyUsage(dateRange: UsageDateRange): Array<HourlyUsage> {
        val stats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            dateRange.startOfDayMillis,
            dateRange.endOfDayMillis
        ) ?: return Array(24) { HourlyUsage(it, 0L) }

        val hourlyData = Array(24) { 0L }

        stats.forEach { stat ->
            if (stat.totalTimeInForeground > 0L) {
                // Calculate which hour this usage belongs to
                val firstTimeStamp = stat.firstTimeStamp
                val lastTimeStamp = stat.lastTimeStamp
                
                // Clip to date range
                val clippedStart = maxOf(firstTimeStamp, dateRange.startOfDayMillis)
                val clippedEnd = minOf(lastTimeStamp, dateRange.endOfDayMillis)
                
                if (clippedEnd > clippedStart) {
                    // Determine the hour
                    val calendar = Calendar.getInstance().apply { timeInMillis = clippedStart }
                    val hour = calendar.get(Calendar.HOUR_OF_DAY)
                    val clippedTime = clipSessionToDayBoundary(firstTimeStamp, lastTimeStamp, stat.totalTimeInForeground, dateRange)
                    hourlyData[hour] += clippedTime
                }
            }
        }

        return Array(24) { hour -> HourlyUsage(hour, hourlyData[hour]) }
    }

    /**
     * Clip a session duration to a specific day boundary.
     * 
     * This ensures that sessions spanning midnight are properly attributed
     * to the correct day.
     */
    private fun clipSessionToDayBoundary(
        firstTimeStamp: Long,
        lastTimeStamp: Long,
        totalTime: Long,
        dateRange: UsageDateRange
    ): Long {
        // If the session is entirely outside the range, return 0
        if (lastTimeStamp < dateRange.startOfDayMillis || firstTimeStamp > dateRange.endOfDayMillis) {
            return 0L
        }

        // Clip the session to the day boundary
        val clippedStart = maxOf(firstTimeStamp, dateRange.startOfDayMillis)
        val clippedEnd = minOf(lastTimeStamp, dateRange.endOfDayMillis)

        return maxOf(0L, clippedEnd - clippedStart)
    }


    private fun isUserVisibleApp(appInfo: ApplicationInfo): Boolean {
        val isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
        val isUpdatedSystemApp = (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
        val hasLauncherIntent = packageManager.getLaunchIntentForPackage(appInfo.packageName) != null

        return !isSystemApp || isUpdatedSystemApp || hasLauncherIntent
    }

    /**
     * Check if any app has exceeded its daily limit.
     * Returns the first app that has exceeded its limit, or null if none.
     */
    suspend fun checkForLimitExceeded(
        dateRange: UsageDateRange,
        database: SwipeDatabase
    ): LimitExceededInfo? {
        val apps = getUsage(dateRange, gamesOnly = false)
        val dao = database.swipeDao()
        
        for (app in apps) {
            val rule = dao.getRuleForPackage(app.packageName)
            if (rule?.enabled == true && rule.dailyLimitMs != null && rule.dailyLimitMs!! > 0) {
                if (app.totalTimeMs > rule.dailyLimitMs!!) {
                    return LimitExceededInfo(
                        packageName = app.packageName,
                        appLabel = app.label,
                        dailyLimitMs = rule.dailyLimitMs!!,
                        usedTimeMs = app.totalTimeMs,
                        quotaPercentage = ((app.totalTimeMs * 100) / rule.dailyLimitMs!!).toInt()
                    )
                }
            }
        }
        
        return null
    }

}
