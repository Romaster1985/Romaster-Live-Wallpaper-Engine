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

        return json.decodeFromString(
            WallpaperProject.serializer(),
            text
        )
    }
}