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