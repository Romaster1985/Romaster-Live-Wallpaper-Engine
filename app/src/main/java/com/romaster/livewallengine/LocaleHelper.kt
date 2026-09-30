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
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/**
 * Preferencia de idioma de la app (independiente del idioma del sistema).
 * Usa AppCompat [AppCompatDelegate.setApplicationLocales] + recursos
 * `values/` (ES por defecto) y `values-en/` (inglés).
 */
object LocaleHelper {

    private const val PREFS = "app_settings"
    private const val KEY_LANGUAGE = "language"

    const val LANG_ES = "es"
    const val LANG_EN = "en"

    fun getLanguage(context: Context): String {
        return context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_LANGUAGE, LANG_ES)
            ?: LANG_ES
    }

    /** Aplica el locale guardado (llamar al inicio de la app). */
    fun applyStoredLocale(context: Context) {
        val tag = getLanguage(context)
        val locales = LocaleListCompat.forLanguageTags(tag)
        if (AppCompatDelegate.getApplicationLocales() != locales) {
            AppCompatDelegate.setApplicationLocales(locales)
        }
    }

    /**
     * Guarda el idioma y lo aplica. La Activity que llama debería
     * hacer [android.app.Activity.recreate] si no se recrea sola.
     */
    fun setLanguage(context: Context, languageTag: String) {
        val tag = when (languageTag) {
            LANG_EN -> LANG_EN
            else -> LANG_ES
        }
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LANGUAGE, tag)
            .apply()
        AppCompatDelegate.setApplicationLocales(
            LocaleListCompat.forLanguageTags(tag)
        )
    }

    fun languageDisplayName(context: Context, languageTag: String): String {
        return when (languageTag) {
            LANG_EN -> context.getString(R.string.language_english)
            else -> context.getString(R.string.language_spanish)
        }
    }

    /**
     * Context con el locale de la app (útil para diálogos / inflate
     * cuando el Context base no refleja aún AppCompatDelegate).
     */
    fun wrap(context: Context): Context {
        val tag = getLanguage(context)
        val locale = java.util.Locale.forLanguageTag(tag)
        val config = android.content.res.Configuration(context.resources.configuration)
        config.setLocale(locale)
        config.setLocales(android.os.LocaleList(locale))
        return context.createConfigurationContext(config)
    }
}
