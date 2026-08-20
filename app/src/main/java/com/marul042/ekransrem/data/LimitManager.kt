package com.marul042.ekransrem.data

import com.marul042.ekransrem.blocking.BlockRule

/**
 * Represents information about an app hitting its daily limit.
 */
data class LimitExceededInfo(
    val packageName: String,
    val appLabel: String,
    val dailyLimitMs: Long,
    val usedTimeMs: Long,
    val quotaPercentage: Int = 100 // Always 100+ when exceeded
)

/**
 * Checks if an app has exceeded its daily limit.
 */
fun checkLimitExceeded(
    appUsage: AppUsage,
    blockRule: BlockRule?
): LimitExceededInfo? {
    if (blockRule?.dailyLimitMs == null || blockRule.dailyLimitMs!! <= 0) {
        return null
    }
    
    if (appUsage.totalTimeMs > blockRule.dailyLimitMs!!) {
        return LimitExceededInfo(
            packageName = appUsage.packageName,
            appLabel = appUsage.label,
            dailyLimitMs = blockRule.dailyLimitMs!!,
            usedTimeMs = appUsage.totalTimeMs,
            quotaPercentage = ((appUsage.totalTimeMs * 100) / blockRule.dailyLimitMs!!).toInt()
        )
    }
    
    return null
}

/**
 * Collection of motivational quotes for blocking screens.
 */
object MotivationalQuotes {
    val quotes = listOf(
        "\"Disiplinin yokluğu ayılı hale gelmişse, hatanı düzeltme gücüdür.\" - Mandela",
        "\"İnsan, belki de en özgür varlık olarak seçimlerimiz bizi tanımlar.\" - Seneca",
        "\"Başarı, kötü alışkanlıkları iyi alışkanlıklarla değiştirmekten geçer.\" - Aristotelis",
        "\"Her an, yeni bir başlangıçtır.\" - Ralph Waldo Emerson",
        "\"Ekrandan uzak kal, zihinle yakın ol.\" - Unknown",
        "\"Zamanını kontrol et, aksi takdirde zaman seni kontrol eder.\" - Seneca",
        "\"Teknoloji hizmetçi olmalı, efendi değil.\" - Steve Jobs",
        "\"Odaklanma, dikkatsizliğe direniştir.\" - Unknown",
        "\"Her harekette kasıt yok mu? Zamanı sınırla.\" - Seneca",
        "\"Bilgelik ertelemeyı reddetmektir.\" - Aristoteles",
        "\"Dikkat şudur: birkaç şeye yoğunlaşmak, birçok şeyi görmezden gelmek.\" - Unknown",
        "\"Yaşamak, seçmektir.\" - Jean-Paul Sartre"
    )
    
    fun getRandomQuote(): String {
        return quotes.random()
    }
    
    fun getQuoteByIndex(index: Int): String {
        return quotes[index % quotes.size]
    }
}
