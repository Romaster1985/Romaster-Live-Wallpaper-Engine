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

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import android.view.View
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

/**
 * Preferencia de idioma de la app (independiente del idioma del sistema).
 *
 * Importante sobre el árabe y otros idiomas RTL:
 * - Se cargan los strings del locale (p.ej. values-ar/).
 * - La UI **no** se espeja: el layout se fuerza siempre a LTR.
 *   El texto árabe sigue leyéndose de derecha a izquierda dentro de cada
 *   TextView gracias al algoritmo bidireccional de Unicode, sin invertir
 *   tabs, sliders ni la estructura de la app.
 */
object LocaleHelper {

    private const val PREFS = "app_settings"
    private const val KEY_LANGUAGE = "language"

    const val LANG_AR = "ar"
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
        /** Nombre en el idioma propio + abreviatura, p.ej. "日本語 [JP]" */
        val label: String
    )

    /** Orden alfabético por abreviatura: AR → CN → DE → EN → ES → FR → IT → JP → RU */
    val SUPPORTED: List<LanguageOption> = listOf(
        LanguageOption(LANG_AR, "العربية [AR]"),
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
     * Context con el locale de la app para recursos/strings, pero con
     * dirección de layout **siempre LTR** (no espeja la UI en árabe).
     */
    fun wrap(context: Context): Context {
        val tag = getLanguage(context)
        val locale = Locale.forLanguageTag(tag)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        config.setLocales(LocaleList(locale))
        // Forzar LTR aunque el idioma sea RTL (árabe, etc.)
        config.setLayoutDirection(Locale.ENGLISH)
        return context.createConfigurationContext(config)
    }

    /**
     * Fuerza LTR en la ventana de una Activity (tabs, sliders, menús, etc.).
     * Llamar tras [Activity.setContentView].
     */
    fun applyLtrLayout(activity: Activity) {
        activity.window?.decorView?.layoutDirection = View.LAYOUT_DIRECTION_LTR
        activity.findViewById<View>(android.R.id.content)?.layoutDirection =
            View.LAYOUT_DIRECTION_LTR
    }
}
