/*
 * Copyright 2026 Román Ignacio Romero (Romaster)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Nota: Este proyecto incluye ColorPickerView (skydoves) licenciado bajo Apache 2.0.
 */

package com.romaster.livewallengine

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

/**
 * Preferencia de idioma de la app (independiente del idioma del sistema).
 * Recursos: values/ (ES por defecto) y values-<tag>/ para el resto.
 *
 * Lista en orden alfabético por abreviatura:
 * BR, CN, DE, EN, ES, FR, IT, JP, RU
 *
 * Portugués de Brasil usa el tag BCP-47 [pt-BR] y la carpeta
 * res/values-pt-rBR/.
 */
object LocaleHelper {

    private const val PREFS = "app_settings"
    private const val KEY_LANGUAGE = "language"

    const val LANG_PT_BR = "pt-BR" // Português [BR]
    const val LANG_ZH = "zh" // 中文 [CN]
    const val LANG_DE = "de"
    const val LANG_EN = "en"
    const val LANG_ES = "es"
    const val LANG_FR = "fr"
    const val LANG_IT = "it"
    const val LANG_JA = "ja" // 日本語 [JP]
    const val LANG_RU = "ru"

    data class LanguageOption(
        val tag: String,
        /** Nombre en el idioma propio + abreviatura, p.ej. "Português [BR]" */
        val label: String
    )

    /** Orden alfabético por abreviatura: BR → CN → DE → EN → ES → FR → IT → JP → RU */
    val SUPPORTED: List<LanguageOption> = listOf(
        LanguageOption(LANG_PT_BR, "Português [BR]"),
        LanguageOption(LANG_ZH, "中文 [CN]"),
        LanguageOption(LANG_DE, "Deutsch [DE]"),
        LanguageOption(LANG_EN, "English [EN]"),
        LanguageOption(LANG_ES, "Español [ES]"),
        LanguageOption(LANG_FR, "Français [FR]"),
        LanguageOption(LANG_IT, "Italiano [IT]"),
        LanguageOption(LANG_JA, "日本語 [JP]"),
        LanguageOption(LANG_RU, "Русский [RU]")
    )

    private val SUPPORTED_TAGS: Set<String> = SUPPORTED.map { it.tag }.toSet()

    fun getLanguage(context: Context): String {
        val stored = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_LANGUAGE, LANG_ES)
            ?: LANG_ES
        // Tags retirados (p.ej. "ar") u otros desconocidos → español
        return if (stored in SUPPORTED_TAGS) stored else LANG_ES
    }

    fun applyStoredLocale(context: Context) {
        val tag = getLanguage(context)
        val locales = LocaleListCompat.forLanguageTags(tag)
        if (AppCompatDelegate.getApplicationLocales() != locales) {
            AppCompatDelegate.setApplicationLocales(locales)
        }
    }

    fun setLanguage(context: Context, languageTag: String) {
        val tag = if (languageTag in SUPPORTED_TAGS) languageTag else LANG_ES
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LANGUAGE, tag)
            .apply()
        AppCompatDelegate.setApplicationLocales(
            LocaleListCompat.forLanguageTags(tag)
        )
    }

    fun languageDisplayName(tag: String): String {
        return SUPPORTED.find { it.tag == tag }?.label
            ?: SUPPORTED.find { it.tag == LANG_ES }!!.label
    }

    @Suppress("UNUSED_PARAMETER")
    fun languageDisplayName(context: Context, languageTag: String): String {
        return languageDisplayName(languageTag)
    }

    /**
     * Context con el locale de la app (útil para diálogos / inflate
     * cuando el Context base no refleja aún AppCompatDelegate).
     */
    fun wrap(context: Context): Context {
        val tag = getLanguage(context)
        val locale = Locale.forLanguageTag(tag)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        config.setLocales(LocaleList(locale))
        return context.createConfigurationContext(config)
    }
}
