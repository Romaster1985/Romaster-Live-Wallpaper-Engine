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
     * Incluye ejes estándar + paramétricos (Roboto Flex).
     * Fuentes que no los tengan suelen ignorarlos.
     */
    fun buildVariationSettings(
        width: Float,
        weight: Float,
        opticalSize: Float,
        grade: Float,
        slant: Float = 0f,
        xopq: Float = 96f,
        yopq: Float = 79f,
        xtra: Float = 468f,
        ytuc: Float = 712f,
        ytlc: Float = 514f,
        ytas: Float = 750f,
        ytde: Float = -203f,
        ytfi: Float = 738f
    ): String {
        return "'wdth' $width," +
            "'wght' $weight," +
            "'opsz' $opticalSize," +
            "'GRAD' $grade," +
            "'slnt' $slant," +
            "'XOPQ' $xopq," +
            "'YOPQ' $yopq," +
            "'XTRA' $xtra," +
            "'YTUC' $ytuc," +
            "'YTLC' $ytlc," +
            "'YTAS' $ytas," +
            "'YTDE' $ytde," +
            "'YTFI' $ytfi"
    }
}
