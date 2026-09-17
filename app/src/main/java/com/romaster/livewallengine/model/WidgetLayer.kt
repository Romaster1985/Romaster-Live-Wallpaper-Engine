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

/**
 * Capa de widget de texto/fórmula (Widgets-OL).
 * Mismo sistema de capas Z que Pics-OL vía [WallpaperProject.layerStack].
 */
@Serializable
data class WidgetLayer(
    var id: String = UUID.randomUUID().toString(),

    /** Fórmula estilo KLWP, p.ej. $df(hh:mm)$ o $bi(level)$% */
    var formula: String = "\$df(hh:mm)\$",

    /** Nombre de fuente de texto (archivo en fonts/) o null = sistema */
    var fontName: String? = null,

    /**
     * true = interpretar el resultado como clave de glifo en fuente de íconos.
     * false = dibujar el texto con [fontName].
     */
    var useIconFont: Boolean = false,

    /** Nombre del par TTF+JSON de íconos (carpeta Icons del repo de temas). */
    var iconFontName: String? = null,

    /** Color ARGB del texto */
    var textColor: Int = 0xFFFFFFFF.toInt(),

    /** Tamaño base en px lógicos (antes de zoom) */
    var textSize: Float = 64f,

    /** 0f..1f */
    var opacity: Float = 1f,

    /** Centro normalizado 0..1 */
    var x: Float = 0.5f,

    /** Centro normalizado 0..1 */
    var y: Float = 0.35f,

    /** Zoom relativo (1 = 100%) */
    var zoom: Float = 1f,

    /** Grados */
    var rotation: Float = 0f,

    /** false = visible en bloqueo; true = oculto en bloqueo */
    var disableOnLockScreen: Boolean = false,

    var fadeDurationMs: Long = 1000L,

    var delayStartMs: Long = 0L
)
