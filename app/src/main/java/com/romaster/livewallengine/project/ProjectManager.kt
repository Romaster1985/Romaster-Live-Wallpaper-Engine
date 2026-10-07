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

package com.romaster.livewallengine.project

import com.romaster.livewallengine.model.WallpaperProject
import com.romaster.livewallengine.model.PositionCoords

object ProjectManager {

    private var currentProject =
        DefaultProject.create()

    private var revision = 0

    fun getProject(): WallpaperProject {
        return currentProject
    }

    fun saveProject(
        project: WallpaperProject
    ) {

        currentProject = project

        revision++

    }

    fun setProject(
        project: WallpaperProject
    ) {
        migratePositions(project)
        currentProject = project
        revision++
    }

    fun resetProject() {

        currentProject =
            DefaultProject.create()

        revision++

    }

    fun setWallpaperVideo(
        path: String
    ) {

        currentProject.wallpaperVideo = path

        revision++

    }

    fun setOverlayVideo(
        path: String
    ) {

        currentProject.overlayVideo = path

        revision++

    }

    fun getRevision(): Int {
        return revision
    }


    /**
     * Convierte posiciones normalizadas antiguas (centro 0.5) de reloj/imágenes/widgets
     * al sistema unificado centro=0 en % (-200..200). Video-BG/OL no se tocan.
     */
    private fun migratePositions(project: WallpaperProject) {
        if (project.positionCoordSpace >= 1) {
            project.clock.x = PositionCoords.clamp(project.clock.x)
            project.clock.y = PositionCoords.clamp(project.clock.y)
            for (layer in project.imageLayers) {
                layer.x = PositionCoords.clamp(layer.x)
                layer.y = PositionCoords.clamp(layer.y)
            }
            for (layer in project.widgetLayers) {
                layer.x = PositionCoords.clamp(layer.x)
                layer.y = PositionCoords.clamp(layer.y)
            }
            for (layer in project.layers) {
                layer.x = PositionCoords.clamp(layer.x)
                layer.y = PositionCoords.clamp(layer.y)
            }
            project.overlay.x = PositionCoords.clamp(project.overlay.x)
            project.overlay.y = PositionCoords.clamp(project.overlay.y)
            return
        }
        // Legacy: normalizado centro 0.5 → % centro 0
        project.clock.x = PositionCoords.fromLegacyNormalized(project.clock.x)
        project.clock.y = PositionCoords.fromLegacyNormalized(project.clock.y)
        for (layer in project.imageLayers) {
            layer.x = PositionCoords.fromLegacyNormalized(layer.x)
            layer.y = PositionCoords.fromLegacyNormalized(layer.y)
        }
        for (layer in project.widgetLayers) {
            layer.x = PositionCoords.fromLegacyNormalized(layer.x)
            layer.y = PositionCoords.fromLegacyNormalized(layer.y)
        }
        for (layer in project.layers) {
            layer.x = PositionCoords.clamp(layer.x)
            layer.y = PositionCoords.clamp(layer.y)
        }
        project.overlay.x = PositionCoords.clamp(project.overlay.x)
        project.overlay.y = PositionCoords.clamp(project.overlay.y)
        project.positionCoordSpace = 1
    }


}