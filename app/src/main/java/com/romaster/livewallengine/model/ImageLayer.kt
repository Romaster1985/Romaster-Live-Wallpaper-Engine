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
 * Capa de imagen (Pics-OL).
 *
 * [zSlot] (3 referencias fijas + capas de imagen arriba):
 * - 1 = detrás del Video-BG
 * - 2 = entre VBG y la siguiente capa fija (VOL o CkOL según orden del reloj)
 * - 3 = entre las dos capas fijas superiores (VOL/CkOL)
 * - 4+ = encima de VBG, VOL y reloj
 *
 * Si el reloj NO está detrás del VOL: orden fijo VBG → VOL → CkOL
 * Si el reloj SÍ está detrás del VOL: orden fijo VBG → CkOL → VOL
 */
@Serializable
data class ImageLayer(
    var id: String = UUID.randomUUID().toString(),
    /** Nombre de archivo en files/images/ */
    var fileName: String? = null,
    /** 0f..1f */
    var opacity: Float = 1f,
    /** 0f..1f (centro normalizado) */
    var x: Float = 0.5f,
    /** 0f..1f (centro normalizado) */
    var y: Float = 0.5f,
    /** Zoom relativo (1 = 100%) */
    var zoom: Float = 1f,
    /** Grados */
    var rotation: Float = 0f,
    /** @deprecated Usar WallpaperProject.layerStack */
    var zSlot: Int = 4
)
