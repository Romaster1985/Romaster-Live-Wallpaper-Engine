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

import kotlin.math.roundToInt

/**
 * Sistema unificado de posición:
 * - Centro de pantalla = 0
 * - Rango típico de sliders = [-200, 200]
 * - 100 ≈ del centro al borde (la capa centrada en el borde)
 * - 200 ≈ una pantalla completa fuera del centro
 *
 * Video-BG / Video-OL ya usaban este sistema.
 * Reloj / imágenes / widgets usaban normalizado [0,1] con centro 0.5.
 */
object PositionCoords {
    const val MIN = -200f
    const val MAX = 200f

    fun clamp(v: Float): Float = v.coerceIn(MIN, MAX)

    /** Valor seguro para Slider con stepSize = 1. */
    fun forSlider(v: Float): Float = clamp(v).roundToInt().toFloat()

    /**
     * Convierte coordenada normalizada antigua (centro 0.5, rango típico [-1, 2])
     * a porcentaje con centro 0: new = (old - 0.5) * 200.
     */
    fun fromLegacyNormalized(v: Float): Float {
        return clamp((v - 0.5f) * 200f)
    }
}
