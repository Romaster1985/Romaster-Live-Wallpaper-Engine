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
data class OverlaySettings(

    var enabled: Boolean = false,

    var videoPath: String? = null,

    var x: Float = 0f,

    var y: Float = 0f,

    var scale: Float = 1f,
    
    var aspectMode: OverlayAspectMode =
        OverlayAspectMode.ORIGINAL,
    
    var rotation: Float = 0f,

    var opacity: Float = 1f,

    var chromaEnabled: Boolean = false,

    var chromaColor: Int = 0xFF00FF00.toInt(),

    var threshold: Float = 50f,

    var softness: Float = 20f,
    
    var soundPath: String? = null,

    var soundDisplayName: String? = null,
    
    var soundDuration: Long = 0L,
    
    var soundVolume: Float = 1f,
    
    var soundEnabled: Boolean = true,

    /**
     * Si true, Video-OL oculto en pantalla de bloqueo.
     * Al desbloquear se revela con Soft Start.
     * Default false = visible (proyectos viejos).
     */
    var disableOnLockScreen: Boolean = false

)