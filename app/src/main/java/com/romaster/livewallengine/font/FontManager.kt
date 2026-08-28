package com.romaster.livewallengine.font

import android.content.Context
import android.graphics.Typeface
import android.os.Build
import java.io.File

object FontManager {

    /**
     * Carga una fuente desde files/fonts.
     * Si [variationSettings] no es null/blank y API >= 26, intenta
     * aplicar ejes variables (p. ej. "'wdth' 30,'wght' 400").
     * En fuentes estáticas los ejes desconocidos suelen ignorarse.
     */
    fun loadTypeface(
        context: Context,
        fileName: String?,
        variationSettings: String? = null
    ): Typeface? {

        if (fileName.isNullOrBlank()) {
            return null
        }

        return try {
            val file = FontStorage.getFontFile(context, fileName)
            if (!file.exists()) return null

            loadFromFile(file, variationSettings)
        } catch (_: Exception) {
            null
        }
    }

    fun loadFromFile(
        file: File,
        variationSettings: String? = null
    ): Typeface? {
        return try {
            if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                !variationSettings.isNullOrBlank()
            ) {
                try {
                    Typeface.Builder(file)
                        .setFontVariationSettings(variationSettings)
                        .build()
                } catch (_: Exception) {
                    Typeface.createFromFile(file)
                }
            } else {
                Typeface.createFromFile(file)
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Cadena OpenType para setFontVariationSettings.
     * Rangos orientativos (v-fonts): wdth 30–150, wght 1–1000,
     * opsz 17–96, GRAD 0–1000.
     */
    fun buildVariationSettings(
        width: Float,
        weight: Float,
        opticalSize: Float,
        grade: Float
    ): String {
        return "'wdth' ${width}," +
            "'wght' ${weight}," +
            "'opsz' ${opticalSize}," +
            "'GRAD' ${grade}"
    }
}
