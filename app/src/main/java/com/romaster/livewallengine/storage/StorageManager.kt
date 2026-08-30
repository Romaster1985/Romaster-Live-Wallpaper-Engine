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

import android.content.Context
import com.romaster.livewallengine.model.WallpaperProject
import java.io.File

object StorageManager {

    private const val FILE_NAME =
        "current_project.json"

    private const val TEMP_FILE_NAME =
        "current_project.json.tmp"

    fun saveProject(
        context: Context,
        project: WallpaperProject
    ) {

        val directory =
            AppDirectories.projects(context)

        if (!directory.exists()) {
            directory.mkdirs()
        }

        val file =
            File(
                directory,
                FILE_NAME
            )

        val tempFile =
            File(
                directory,
                TEMP_FILE_NAME
            )

        val json =
            ProjectSerializer.encode(project)

        tempFile.writeText(
            json,
            Charsets.UTF_8
        )

        if (file.exists()) {
            file.delete()
        }

        if (!tempFile.renameTo(file)) {
            throw IllegalStateException(
                "No se pudo reemplazar current_project.json"
            )
        }
    }

    fun loadProject(
        context: Context
    ): WallpaperProject? {

        val file =
            File(
                AppDirectories.projects(context),
                FILE_NAME
            )

        if (!file.exists()) {
            return null
        }

        return try {

            ProjectSerializer.decode(
                file.readText(
                    Charsets.UTF_8
                )
            )

        } catch (e: Exception) {

            e.printStackTrace()

            null
        }
    }
}