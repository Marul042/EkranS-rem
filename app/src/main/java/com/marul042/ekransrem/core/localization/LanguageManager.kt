package com.marul042.ekransrem.core.localization

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

enum class AppLanguage(val tag: String) {
    ENGLISH("en"),
    TURKISH("tr")
}

class LanguageManager(private val context: Context) {
    fun setLanguage(language: AppLanguage) {
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            .edit()
            .putString("language", language.tag)
            .apply()
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language.tag))
    }

    fun currentLanguage(): AppLanguage {
        val tag = AppCompatDelegate.getApplicationLocales().get(0)?.language
            ?: context.resources.configuration.locales[0].language
        return if (tag.equals(AppLanguage.TURKISH.tag, ignoreCase = true)) {
            AppLanguage.TURKISH
        } else {
            AppLanguage.ENGLISH
        }
    }
}
