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

/** Qué dispara / controla el progreso de la animación. */
@Serializable
enum class AnimationReaction {
    /** No anima; no se muestran más controles. */
    DISABLED,
    /** 0 = bloqueado, 1 = desbloqueado. */
    UNLOCK,
    /** Al terminar reinicia desde el inicio. */
    LOOP,
    /** Ida y vuelta (avance + reversa). */
    LOOP_REVERSE,
    /** 1 cuando la página del launcher coincide con [homeScreenIndex]. */
    HOME_SCROLL,
    /** 1 cuando la inclinación alcanza [gyroDegrees]. */
    GYROSCOPE,
    /** 0 = wallpaper no visible, 1 = visible. */
    VISIBILITY,
    /** Resultado de [formula] (típicamente 0/1 vía $if$). */
    FORMULA
}

/** Qué transformación aplica sobre la capa objetivo. */
@Serializable
enum class AnimationAction {
    TRANSLATE,
    TRANSLATE_INVERSE,
    ZOOM_IN,
    ZOOM_OUT,
    FADE_OUT,
    FADE_IN,
    ROTATE
}

/** Manecilla del reloj analógico (rotación sincronizada con la hora). */
@Serializable
enum class ClockHand {
    HOUR,
    MINUTE,
    SECOND
}

/** Movimiento de la manecilla: saltos discretos o continuo. */
@Serializable
enum class ClockHandMotion {
    STEP,
    CONTINUOUS
}

/**
 * Regla de animación (tab Animaciones).
 *
 * [targetKey]:
 * - "vbg" = Video-BG
 * - "vol" = Video-OL
 * - "ckol" = Clock-OL
 * - "img:<id>" = ImageLayer
 * - "wdg:<id>" = WidgetLayer
 */
@Serializable
data class AnimationLayer(
    var id: String = UUID.randomUUID().toString(),

    /** Capa a animar (ver [targetKey]). */
    var targetKey: String = "vbg",

    var reaction: AnimationReaction = AnimationReaction.DISABLED,

    var action: AnimationAction = AnimationAction.TRANSLATE,

    // --- Reacción: Desplazamiento Bg ---
    /** Página del launcher (0 = home, negativo = izquierda, positivo = derecha). */
    var homeScreenIndex: Int = 0,

    // --- Reacción: Giroscopio ---
    /** Ángulo de inclinación objetivo en grados (0..359). */
    var gyroDegrees: Int = 90,

    // --- Reacción: Fórmula ---
    var formula: String = "\$if(1, 1, 0)\$",

    // --- Acción: desplazamiento / desplazamiento invertido ---
    /**
     * Dirección del desplazamiento en grados, sentido horario.
     * 0 = arriba, 90 = derecha, 180 = abajo, 270 = izquierda.
     */
    var moveAngleDeg: Float = 0f,

    /** Distancia total del desplazamiento en px de diseño. */
    var moveDistancePx: Float = 100f,

    // --- Acción: zoom ---
    /** Intensidad de zoom 0..1000 (unidades de diseño). */
    var zoomAmount: Float = 100f,

    // --- Acción: fade in / fade out ---
    /** Duración del fade en ms. */
    var fadeDurationMs: Long = 1000L,

    // --- Acción: rotación ---
    /** Grados totales de rotación (0..359). */
    var rotationDegrees: Float = 90f,

    /** true = antihorario. */
    var rotationCounterClockwise: Boolean = false,

    /**
     * Rotación de reloj (solo con reacción Bucle + acción Rotación).
     * La capa sigue la manecilla de hora/minuto/segundo.
     * [rotationDegrees] se usa como offset del 12:00 (dónde apunta las 12).
     */
    var clockRotation: Boolean = false,

    var clockHand: ClockHand = ClockHand.HOUR,

    var clockHandMotion: ClockHandMotion = ClockHandMotion.CONTINUOUS,

    // --- Velocidad / aceleración (desplazamiento, zoom, rotación) ---
    /**
     * Velocidad nominal.
     * Unidades: px/ms (desplazamiento), unidades-zoom/ms (zoom), °/ms (rotación).
     */
    var speed: Float = 0.25f,

    /**
     * Aceleración / desaceleración.
     * 0 = velocidad constante.
     * Unidades: px/ms², unidades-zoom/ms² o °/ms² según la acción.
     */
    var acceleration: Float = 0f,

    /**
     * Si true, al pasar la reacción de 1→0 se anima el retorno (inversa).
     * Si false (default), se deja terminar el avance y se vuelve al origen de golpe
     * (o snap inmediato si ya estaba en el final).
     */
    var reverseOnDeactivate: Boolean = false
)
