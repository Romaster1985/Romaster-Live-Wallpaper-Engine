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

@Serializable
data class ClockSettings(

    var enabledOnLockScreen: Boolean = true,
    
    var enabled: Boolean = true,

    var showDate: Boolean = true,

    var timeFormat: TimeFormat =
        TimeFormat.HH_MM,

    var dateFormat: DateFormat =
        DateFormat.DOW_DD_MON,

    var fontFile: String? = null,

    var clockSize: Float = 64f,

    var dateSize: Float = 32f,

    var clockColor: String = "#FFFFFF",

    var dateColor: String = "#FFFFFF",
    
    var clockColorPreset: String = "Blanco",

    var dateColorPreset: String = "Blanco",

    var alignment: TextAlignment = TextAlignment.CENTER,

    var x: Float = 0.5f,

    var y: Float = 0.5f,

    var dateSpacing: Float = 20f,
    
    var clockFont: String? = null,

    var dateFont: String? = null,

    /**
     * Si true, el reloj se dibuja entre el video de fondo y el Video-OL
     * (queda "detrás" del overlay de video).
     */
    var behindVideoOverlay: Boolean = false,

    /**
     * Si true, la fecha va arriba y la hora debajo.
     */
    var swapTimeAndDate: Boolean = false,

    /**
     * Deformación vertical de la hora en px (-500…+500). 0 = normal.
     */
    var clockVerticalDeform: Float = 0f,

    /**
     * Deformación vertical de la fecha en px (-500…+500). 0 = normal.
     */
    var dateVerticalDeform: Float = 0f,

    /**
     * Si true, hora y fecha comparten la misma línea (baseline).
     * Con spacing 0 quedan superpuestos; la hora se dibuja detrás de la fecha.
     * El valor de dateSpacing desplaza en vertical la separación entre ambos.
     */
    var allowOverlap: Boolean = false,

    /** Grosor del borde de la hora (0 = sin borde). */
    var clockBorderWidth: Float = 0f,

    /** Grosor del borde de la fecha (0 = sin borde). */
    var dateBorderWidth: Float = 0f,

    var clockBorderColor: String = "#000000",

    var dateBorderColor: String = "#000000",

    // --- Ejes de fuente variable (OpenType / Roboto Flex) ---
    // Defaults = valores "neutros"; en fuentes estáticas se ignoran.
    /** Width (wdth) 25–151 */
    var fontWidth: Float = 100f,
    /** Weight (wght) 100–1000 */
    var fontWeight: Float = 400f,
    /** Optical Size (opsz) 8–144 */
    var fontOpticalSize: Float = 28f,
    /** Grade (GRAD) -200–150 */
    var fontGrade: Float = 0f,
    /** Slant (slnt) -10–0 */
    var fontSlant: Float = 0f,
    /** Thick stroke / stems (XOPQ) 27–175 */
    var fontXopq: Float = 96f,
    /** Thin stroke / bars (YOPQ) 25–135 */
    var fontYopq: Float = 79f,
    /** Counter width (XTRA) 323–603 */
    var fontXtra: Float = 468f,
    /** Uppercase height (YTUC) 528–760 */
    var fontYtuc: Float = 712f,
    /** Lowercase height (YTLC) 416–570 */
    var fontYtlc: Float = 514f,
    /** Ascender height (YTAS) 649–854 */
    var fontYtas: Float = 750f,
    /** Descender depth (YTDE) -305–-98 */
    var fontYtde: Float = -203f,
    /** Figure height (YTFI) 560–788 — útil en relojes */
    var fontYtfi: Float = 738f,

    /** Modo cristal: relleno translúcido + textura opcional */
    var crystalMode: Boolean = false,

    /** Transparencia del relleno del cristal (0–50; más alto = más transparente) */
    var crystalBlur: Float = 12f,

    /**
     * Nivel de desenfoque del fondo detrás de los glifos.
     * 0 = Ninguno, 1 = Bajo, 2 = Medio, 3 = Alto.
     * Independiente de la transparencia.
     */
    var crystalDefocusLevel: Int = 0,

    /** PNG de textura (en files/images/), opcional */
    var crystalTextureFile: String? = null,

    /** Reflejo vertical debajo del texto, con degradado de desvanecimiento */
    var reflectionEnabled: Boolean = false,

    /** Intensidad del reflejo en el borde superior (0–100) */
    var reflectionOpacity: Float = 45f,

    /**
     * Separación del reflejo (px).
     * 0 = lo invertido queda superpuesto al derecho (eje en el centro del bloque).
     * Valores altos empujan el reflejo hacia abajo (puede salir de pantalla).
     */
    var reflectionGap: Float = 0f,

    /** Relieve: bordes claros/oscuros que dan sensación de profundidad */
    var bevelEnabled: Boolean = false,

    /** Ángulo de la luz del relieve en grados (0 = derecha, 90 = abajo, 180 = izquierda, 270 = arriba) */
    var bevelAngle: Float = 315f,

    /** Intensidad del relieve (0–100) */
    var bevelStrength: Float = 40f
)

