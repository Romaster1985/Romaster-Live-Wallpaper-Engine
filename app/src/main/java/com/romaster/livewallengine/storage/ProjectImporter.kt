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
import android.net.Uri
import com.romaster.livewallengine.model.WallpaperProject
import com.romaster.livewallengine.project.ProjectManager
import com.romaster.livewallengine.video.ReverseVideoProcessor
import com.romaster.livewallengine.video.VideoStorage
import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream

object ProjectImporter {

    fun import(

        context: Context,

        uri: Uri

    ) {
        ReverseVideoProcessor.clearAll(context)

        context.contentResolver
            .openInputStream(uri)
            ?.use { input ->
                importFromStream(context, input)
            }

        val project =
            StorageManager.loadProject(context)
                ?: return

        normalizeReverseNames(context, project)
        ProjectManager.setProject(project)
        StorageManager.saveProject(context, project)
    }

    /**
     * Importa un proyecto desde un archivo ZIP local
     * (por ejemplo uno descargado de la galería).
     */
    fun importFromFile(

        context: Context,

        zipFile: File

    ) {
        ReverseVideoProcessor.clearAll(context)

        zipFile.inputStream().use { input ->
            importFromStream(context, input)
        }

        val project =
            StorageManager.loadProject(context)
                ?: throw IllegalStateException(
                    "No se pudo leer project.json del ZIP"
                )

        normalizeReverseNames(context, project)
        ProjectManager.setProject(project)
        StorageManager.saveProject(context, project)
    }

    private fun importFromStream(

        context: Context,

        input: InputStream

    ) {

        ZipInputStream(input).use { zip ->

            var entry = zip.nextEntry

            while (entry != null) {

                val outFile = when {

                    entry.name == "project.json" ->
                        File(
                            AppDirectories.projects(context),
                            "current_project.json"
                        )

                    entry.name.startsWith("videos/") ->
                        File(
                            context.filesDir,
                            entry.name
                        )

                    entry.name.startsWith("audio/") ->
                        File(
                            context.filesDir,
                            entry.name
                        )

                    entry.name.startsWith("fonts/") ->
                        File(
                            context.filesDir,
                            entry.name
                        )

                    // Compat ZIPs viejos: pingpong/ → videos/ con nombres fijos
                    entry.name.startsWith("pingpong/") -> {
                        val base = entry.name.removePrefix("pingpong/")
                        val fixed = when {
                            base.contains("locked", ignoreCase = true) ->
                                VideoStorage.OVERLAY_REVERSE_LOCKED
                            base.contains("unlocked", ignoreCase = true) ->
                                VideoStorage.OVERLAY_REVERSE_UNLOCKED
                            else -> base
                        }
                        File(
                            AppDirectories.videos(context),
                            fixed
                        )
                    }

                    else -> null
                }

                if (outFile != null && !entry.isDirectory) {
                    outFile.parentFile?.mkdirs()
                    outFile.outputStream().use { zip.copyTo(it) }
                }

                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
    }

    /**
     * Asegura nombres fijos de reversa según lo que exista en videos/.
     */
    private fun normalizeReverseNames(
        context: Context,
        project: WallpaperProject
    ) {
        val locked = VideoStorage.getReverseLockedFile(context)
        val unlocked = VideoStorage.getReverseUnlockedFile(context)

        if (locked.exists() && locked.length() > 0L) {
            project.cueLockedReverseFile = VideoStorage.OVERLAY_REVERSE_LOCKED
        } else {
            project.cueLockedReverseFile = null
            project.cueLockedPingPong = false
        }

        if (unlocked.exists() && unlocked.length() > 0L) {
            project.cueUnlockedReverseFile = VideoStorage.OVERLAY_REVERSE_UNLOCKED
        } else {
            project.cueUnlockedReverseFile = null
            project.cueUnlockedPingPong = false
        }
    }
}
