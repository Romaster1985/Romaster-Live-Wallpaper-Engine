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
 * Capa de widget (Widgets-OL): texto/ícono o barra de progreso.
 * Mismo sistema de capas Z que Pics-OL vía [WallpaperProject.layerStack].
 */
@Serializable
data class WidgetLayer(
    var id: String = UUID.randomUUID().toString(),

    /**
     * Nombre visible opcional. Vacío = "Widget N".
     * Se muestra en la card, orden Z y selector de animaciones.
     */
    var customName: String = "",

    /** Tipo de widget: texto/ícono (default) o progreso. */
    var widgetType: WidgetType = WidgetType.TEXT_ICON,

    // --- Progreso (solo si widgetType == PROGRESS) ---
    /** Fórmula del mínimo (inicio de la barra). */
    var progressMinFormula: String = "0",
    /** Fórmula del máximo (fin de la barra). */
    var progressMaxFormula: String = "100",
    /** Fórmula del nivel actual (ej. $bi(level)$). */
    var progressLevelFormula: String = "\$bi(level)\$",
    /** Plano (sólido) o Dividido (segmentos). */
    var progressFillMode: ProgressFillMode = ProgressFillMode.FLAT,
    /** Número de secciones en modo dividido (2..100). */
    var progressSections: Int = 20,
    /** Lineal o Circular. */
    var progressShape: ProgressShape = ProgressShape.LINEAR,
    /**
     * Tamaño en px de diseño (ref. alto 1920):
     * lineal = largo de la barra; circular = diámetro.
     */
    var progressSize: Float = 400f,
    /** Grosor de la barra / anillo en px de diseño. */
    var progressThickness: Float = 40f,
    /** Color del progreso: plano o gradiente. */
    var progressColorMode: ProgressColorMode = ProgressColorMode.FLAT,
    /** Color del progreso actual (ARGB). */
    var progressFgColor: Int = 0xFFFFDC00.toInt(),
    /** Color del extremo del gradiente (ARGB); solo en modo Gradiente. */
    var progressGrColor: Int = 0xFFFF0000.toInt(),
    /** Color del tramo restante / fondo (ARGB). */
    var progressBgColor: Int = 0xFFFFFFFF.toInt(),
    /** Color del contorno/marco exterior (ARGB). */
    var progressBorderColor: Int = 0xFF000000.toInt(),
    /** Grosor del contorno exterior en px de diseño (crece hacia afuera; 0 = sin borde). */
    var progressBorderWidth: Float = 0f,

    /** Fórmula estilo KLWP, p.ej. $df(hh:mm)$ o $bi(level)$% */
    var formula: String = "\$df(hh:mm)\$",

    /** Nombre de fuente de texto (archivo en fonts/) o null = sistema */
    var fontName: String? = null,

    /**
     * Compatibilidad con proyectos antiguos.
     * En runtime se usa [iconFontName] != null como indicador de modo íconos.
     */
    var useIconFont: Boolean = false,

    /** Nombre del par TTF+JSON de íconos (carpeta Icons del repo de temas). */
    var iconFontName: String? = null,

    /**
     * Reasignación de nombres de íconos por widget.
     * Clave = nombre del provider (p.ej. RAIN, CLEAR).
     * Valor = nombre del glifo en la fuente de íconos (p.ej. rain, cloud_rain).
     * Vacío = usar el nombre del provider tal cual contra el JSON de la fuente.
     */
    var iconGlyphMap: Map<String, String> = emptyMap(),

    /** Color ARGB del texto */
    var textColor: Int = 0xFFFFFFFF.toInt(),

    /** Color ARGB del borde */
    var borderColor: Int = 0xFF000000.toInt(),

    /** Grosor del borde en px de diseño (0 = sin borde) */
    var borderWidth: Float = 0f,

    /** Tamaño base en px lógicos (referencia 1920 de alto) */
    var textSize: Float = 64f,

    /** 0f..1f */
    var opacity: Float = 1f,

    /** Centro normalizado 0..1 */
    var x: Float = 0.5f, // default legacy (centro 0.5); al crear capas nuevas se pone 0

    /** Centro normalizado 0..1 */
    var y: Float = 0.5f, // default legacy (centro 0.5); al crear capas nuevas se pone 0

    /** Zoom relativo (1 = 100%) */
    var zoom: Float = 1f,

    /** Grados */
    var rotation: Float = 0f,

    /** Alineación horizontal del texto y ancla de posición X. */
    var alignH: TextAlignment = TextAlignment.CENTER,

    /** Alineación vertical del texto y ancla de posición Y. */
    var alignV: VerticalAlignment = VerticalAlignment.MIDDLE,

    /** true = oculto en pantalla de bloqueo */
    var disableOnLockScreen: Boolean = false,

    /** true = oculto en launcher (dispositivo desbloqueado) */
    var disableOnLauncher: Boolean = false,

    var fadeDurationMs: Long = 1000L,

    var delayStartMs: Long = 0L,

    /** Si true, aplica ejes de fuente variable (OpenType). */
    var enableFontVariations: Boolean = false,

    // Ejes de fuente variable (mismos defaults que Clock-OL)
    var fontWidth: Float = 100f,
    var fontWeight: Float = 400f,
    var fontOpticalSize: Float = 28f,
    var fontGrade: Float = 0f,
    var fontSlant: Float = 0f,
    var fontXopq: Float = 96f,
    var fontYopq: Float = 79f,
    var fontXtra: Float = 468f,
    var fontYtuc: Float = 712f,
    var fontYtlc: Float = 514f,
    var fontYtas: Float = 750f,
    var fontYtde: Float = -203f,
    var fontYtfi: Float = 738f
)
