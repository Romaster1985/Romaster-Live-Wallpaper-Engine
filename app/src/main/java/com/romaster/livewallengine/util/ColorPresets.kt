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

package com.romaster.livewallengine.util

import com.romaster.livewallengine.model.ColorPreset

object ColorPresets {

    private const val CUSTOM_NAME =
        "HEX personalizado"

    private const val CUSTOM_COLOR =
        "#12ABCD"

    val items = listOf(

        ColorPreset(
            "Blanco",
            "#FFFFFF"
        ),

        ColorPreset(
            "Negro",
            "#000000"
        ),

        ColorPreset(
            "Rojo",
            "#FF0000"
        ),

        ColorPreset(
            "Verde",
            "#00FF00"
        ),

        ColorPreset(
            "Azul",
            "#0000FF"
        ),

        ColorPreset(
            "Amarillo",
            "#FFFF00"
        ),

        ColorPreset(
            "Magenta",
            "#FF00FF"
        ),

        ColorPreset(
            "Cian",
            "#00FFFF"
        ),

        ColorPreset(
            CUSTOM_NAME,
            "CUSTOM"
        )
    )

    fun names(): List<String> {

        return items.map {

            it.name
        }
    }

    fun findByName(
        name: String
    ): ColorPreset? {

        return items.find {

            it.name == name
        }
    }

    fun findByHex(
        hex: String
    ): ColorPreset? {

        return items.find {

            it.hex.equals(
                hex,
                ignoreCase = true
            )
        }
    }

    fun getHex(
        name: String
    ): String {

        return findByName(
            name
        )?.hex ?: "#FFFFFF"
    }

    fun getPresetName(
        hex: String
    ): String {

        return findByHex(
            hex
        )?.name ?: CUSTOM_NAME
    }

    fun isCustom(
        name: String
    ): Boolean {

        return name ==
            CUSTOM_NAME
    }

    fun defaultCustomColor():
        String {

        return CUSTOM_COLOR
    }
}