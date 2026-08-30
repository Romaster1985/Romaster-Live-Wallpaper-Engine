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
import java.io.File

object AppDirectories {

    fun projects(
        context: Context
    ): File {

        return File(
            context.filesDir,
            "projects"
        ).apply {
            mkdirs()
        }
    }

    fun videos(
        context: Context
    ): File {

        return File(
            context.filesDir,
            "videos"
        ).apply {
            mkdirs()
        }
    }

    fun fonts(
        context: Context
    ): File {

        return File(
            context.filesDir,
            "fonts"
        ).apply {
            mkdirs()
        }
    }

    fun exports(
        context: Context
    ): File {

        return File(
            context.filesDir,
            "exports"
        ).apply {
            mkdirs()
        }
    }

    /**
     * Clips invertidos para ping-pong del overlay.
     */
    fun pingpong(context: Context): File {
        return File(
            context.filesDir,
            "pingpong"
        ).apply {
            mkdirs()
        }
    }

    /**
     * Carpeta pública de la app en Documentos
     * (misma base que usa FileLogger).
     */
    fun publicDocumentsRoot(): File {
        return File(
            "/storage/emulated/0/Documents/Romaster_LiveWall_Engine"
        ).apply {
            mkdirs()
        }
    }

    /**
     * Destino de los ZIP descargados desde la galería de GitHub.
     */
    fun galleryDownloads(context: Context): File {
        return try {
            File(publicDocumentsRoot(), "downloads").apply {
                if (!exists()) {
                    mkdirs()
                }
                if (!canWrite()) {
                    throw SecurityException("No write access")
                }
            }
        } catch (_: Exception) {
            File(
                context.getExternalFilesDir(null) ?: context.filesDir,
                "gallery_downloads"
            ).apply {
                mkdirs()
            }
        }
    }
}
