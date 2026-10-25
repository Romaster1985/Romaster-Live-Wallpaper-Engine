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

package com.romaster.livewallengine.model

import kotlinx.serialization.Serializable
import java.util.UUID

/** Tipo de efecto de color / máscara (tab Efectos). */
@Serializable
enum class EffectType {
    /** Saturación 0–200 (100 = normal, 0 = B&N). */
    SATURATION,
    /** Inversión de colores RGB. */
    INVERT,
    /** Solarizado por umbral de luminancia. */
    SOLARIZE,
    /** Tinte con color e intensidad. */
    TINT,
    /** Desplazamiento de matiz (hue) −180…+180. */
    HUE,
    /** Máscara de recorte (PNG blanco/transparente). */
    CLIP_MASK
}

/**
 * Regla de efecto (tab Efectos).
 *
 * [targetKey]:
 * - "vbg" = Video-BG
 * - "vol" = Video-OL
 * - "ckol" = Clock-OL
 * - "img:<id>" = ImageLayer
 * - "wdg:<id>" = WidgetLayer
 */
@Serializable
data class EffectLayer(
    var id: String = UUID.randomUUID().toString(),

    /** Nombre visible opcional. Vacío = "Efecto N". */
    var customName: String = "",

    /** Capa objetivo (mismo esquema que animaciones). */
    var targetKey: String = "vbg",

    var type: EffectType = EffectType.SATURATION,

    // --- Saturación ---
    /** 0 = B&N, 100 = normal, 200 = hiper. */
    var saturation: Float = 100f,

    // --- Invertir ---
    /** Si true, invierte solo el tono preservando luminancia. */
    var invertLumaPreserve: Boolean = false,

    // --- Solarizar ---
    /** Umbral 0–100. */
    var solarizeThreshold: Float = 50f,

    // --- Tinte ---
    var tintColor: Int = 0xFFFF0000.toInt(),
    /** 0–100. */
    var tintStrength: Float = 50f,
    /** 0 = mezcla, 1 = multiplicar. */
    var tintMode: Int = 0,

    // --- Matiz (Hue) ---
    /** Desplazamiento de matiz en grados −180…+180. */
    var hueShift: Float = 0f,

    // --- Máscara de recorte ---
    /** Nombre de archivo en storage de máscaras (p. ej. mask_xxx.png). */
    var maskFileName: String? = null,
    /** Escala % (10–300). */
    var maskScale: Float = 100f,
    var maskX: Float = 0f,
    var maskY: Float = 0f,
    /** Rotación 0–359. */
    var maskRotation: Float = 0f,
    var maskInvert: Boolean = false
)
