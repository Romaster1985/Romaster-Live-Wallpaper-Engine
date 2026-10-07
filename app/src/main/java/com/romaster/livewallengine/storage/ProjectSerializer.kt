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

package com.romaster.livewallengine.storage

import com.romaster.livewallengine.model.WallpaperProject
import kotlinx.serialization.json.Json
import com.romaster.livewallengine.model.PositionCoords

object ProjectSerializer {

    private val json = Json {

        prettyPrint = true

        ignoreUnknownKeys = true
    }

    fun encode(
        project: WallpaperProject
    ): String {

        return json.encodeToString(
            WallpaperProject.serializer(),
            project
        )
    }

    fun decode(
        text: String
    ): WallpaperProject {
        val project = json.decodeFromString(
            WallpaperProject.serializer(),
            text
        )
        // Migración aplicada también vía ProjectManager.setProject;
        // aquí se cubre load directo desde StorageManager.
        if (project.positionCoordSpace < 1) {
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
        return project
    }
}