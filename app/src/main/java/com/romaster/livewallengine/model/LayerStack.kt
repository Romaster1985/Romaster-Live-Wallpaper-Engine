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

/**
 * Orden global de composición (de atrás hacia adelante).
 * Tokens fijos: [ID_VBG], [ID_VOL], [ID_CLOCK]
 * El resto son ids de [ImageLayer].
 */
object LayerStack {

    const val ID_VBG = "vbg"
    const val ID_VOL = "vol"
    const val ID_CLOCK = "ckol"

    fun defaultStack(clockBehindVol: Boolean): MutableList<String> {
        return if (clockBehindVol) {
            mutableListOf(ID_VBG, ID_CLOCK, ID_VOL)
        } else {
            mutableListOf(ID_VBG, ID_VOL, ID_CLOCK)
        }
    }

    /**
     * Asegura que el stack del proyecto sea coherente:
     * - incluye VBG, VOL, CLOCK
     * - incluye todas las image layers
     * - no tiene ids huérfanos
     * - respeta el orden VOL/CLOCK según behindVideoOverlay
     */
    fun ensure(project: WallpaperProject) {
        val imageIds = project.imageLayers.map { it.id }.toSet()
        var stack = project.layerStack

        if (stack.isEmpty()) {
            stack = defaultStack(project.clock.behindVideoOverlay)
            // Migración desde zSlot si existiera
            val sorted = project.imageLayers.sortedBy { it.zSlot }
            sorted.forEach { stack.add(it.id) }
            project.layerStack = stack
        }

        // Quitar huérfanos
        stack.removeAll { id ->
            id != ID_VBG && id != ID_VOL && id != ID_CLOCK && id !in imageIds
        }

        // Asegurar tokens fijos
        if (ID_VBG !in stack) stack.add(0, ID_VBG)
        if (ID_VOL !in stack) stack.add(ID_VOL)
        if (ID_CLOCK !in stack) stack.add(ID_CLOCK)

        // Asegurar cada imagen
        for (layer in project.imageLayers) {
            if (layer.id !in stack) stack.add(layer.id)
        }

        // Sincronizar orden VOL ↔ CLOCK con el switch del reloj
        syncClockVolOrder(stack, project.clock.behindVideoOverlay)

        project.layerStack = stack
    }

    /**
     * Mantiene la posición relativa de las imágenes; solo intercambia VOL y CLOCK
     * si el switch no coincide con el orden actual.
     */
    fun syncClockVolOrder(stack: MutableList<String>, clockBehindVol: Boolean) {
        val iVol = stack.indexOf(ID_VOL)
        val iClock = stack.indexOf(ID_CLOCK)
        if (iVol < 0 || iClock < 0) return

        val clockIsBeforeVol = iClock < iVol
        if (clockBehindVol && !clockIsBeforeVol) {
            // CLOCK debe quedar antes que VOL
            stack.removeAt(iClock)
            val newVol = stack.indexOf(ID_VOL)
            stack.add(newVol, ID_CLOCK)
        } else if (!clockBehindVol && clockIsBeforeVol) {
            // VOL debe quedar antes que CLOCK
            stack.removeAt(iClock)
            val newVol = stack.indexOf(ID_VOL)
            stack.add(newVol + 1, ID_CLOCK)
        }
    }

    fun label(id: String, project: WallpaperProject): String {
        return when (id) {
            ID_VBG -> "Video-BG"
            ID_VOL -> "Video-OL"
            ID_CLOCK -> "Clock-OL"
            else -> {
                val idx = project.imageLayers.indexOfFirst { it.id == id }
                if (idx >= 0) "Capa de Imagen ${idx + 1}" else "Capa"
            }
        }
    }

    fun isFixed(id: String): Boolean =
        id == ID_VBG || id == ID_VOL || id == ID_CLOCK
}
