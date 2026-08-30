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
import android.graphics.Bitmap
import android.net.Uri
import com.romaster.livewallengine.debug.FileLogger
import com.romaster.livewallengine.project.ProjectManager
import com.romaster.livewallengine.video.VideoStorage
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object ProjectExporter {

    /**
     * @param onProgress 0f..1f (opcional)
     * @return true si el ZIP se escribió sin error fatal
     */
    fun export(
        context: Context,
        destination: Uri,
        previewBitmap: Bitmap?,
        onProgress: ((Float) -> Unit)? = null
    ): Boolean {

        val output = context.contentResolver.openOutputStream(destination)
            ?: run {
                FileLogger.log(context, "ProjectExporter FAIL: no se pudo abrir destino")
                return false
            }

        // Pasos estimados: json + preview + N recursos
        val resourceSlots = countResourceSlots(context)
        val totalSteps = 2 + resourceSlots.coerceAtLeast(1)
        var step = 0

        fun tick() {
            step++
            onProgress?.invoke((step.toFloat() / totalSteps).coerceIn(0f, 1f))
        }

        return try {
            ZipOutputStream(output).use { zip ->

                try {
                    writeProjectJson(zip)
                } catch (e: Exception) {
                    FileLogger.log(context, "ProjectExporter project.json ERROR: ${e.message}")
                }
                tick()

                try {
                    writePreview(zip, previewBitmap)
                } catch (e: Exception) {
                    FileLogger.log(context, "ProjectExporter preview.png ERROR: ${e.message}")
                }
                tick()

                try {
                    writeResources(context, zip) { tick() }
                } catch (e: Exception) {
                    FileLogger.log(context, "ProjectExporter resources ERROR: ${e.message}")
                }
            }
            onProgress?.invoke(1f)
            FileLogger.log(context, "ProjectExporter OK")
            true
        } catch (e: Exception) {
            FileLogger.log(context, "ProjectExporter FAIL: ${e.message}")
            false
        }
    }

    private fun countResourceSlots(context: Context): Int {
        val project = ProjectManager.getProject()
        var n = 0
        if (!project.wallpaperVideo.isNullOrBlank()) n++
        if (!project.overlayVideo.isNullOrBlank()) n++
        n += 2 // reverse locked + unlocked (se intentan siempre)
        if (!project.layers.firstOrNull()?.soundPath.isNullOrBlank()) n++
        if (!project.overlay.soundPath.isNullOrBlank()) n++
        if (!project.clock.clockFont.isNullOrBlank()) n++
        if (!project.clock.dateFont.isNullOrBlank()) n++
        return n
    }

    private fun writeProjectJson(zip: ZipOutputStream) {
        val json = ProjectSerializer.encode(ProjectManager.getProject())
        zip.putNextEntry(ZipEntry("project.json"))
        zip.write(json.toByteArray())
        zip.closeEntry()
    }

    private fun writePreview(
        zip: ZipOutputStream,
        bitmap: Bitmap?
    ) {
        if (bitmap == null) return

        zip.putNextEntry(ZipEntry("preview.png"))
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, zip)
        zip.closeEntry()
    }

    private fun addFile(
        zip: ZipOutputStream,
        file: File,
        entryName: String,
        context: Context
    ): Boolean {
        if (!file.exists() || file.length() <= 0L) return false
        return try {
            zip.putNextEntry(ZipEntry(entryName))
            file.inputStream().use { it.copyTo(zip) }
            zip.closeEntry()
            true
        } catch (e: Exception) {
            FileLogger.log(context, "ProjectExporter addFile ERROR $entryName: ${e.message}")
            false
        }
    }

    private fun writeResources(
        context: Context,
        zip: ZipOutputStream,
        onFileDone: () -> Unit
    ) {
        val project = ProjectManager.getProject()

        project.wallpaperVideo?.let {
            addFile(
                zip,
                File(context.filesDir, "videos/$it"),
                "videos/$it",
                context
            )
            onFileDone()
        }

        project.overlayVideo?.let {
            addFile(
                zip,
                File(context.filesDir, "videos/$it"),
                "videos/$it",
                context
            )
            onFileDone()
        }

        addFile(
            zip,
            VideoStorage.getReverseLockedFile(context),
            "videos/${VideoStorage.OVERLAY_REVERSE_LOCKED}",
            context
        )
        onFileDone()

        addFile(
            zip,
            VideoStorage.getReverseUnlockedFile(context),
            "videos/${VideoStorage.OVERLAY_REVERSE_UNLOCKED}",
            context
        )
        onFileDone()

        project.layers.firstOrNull()?.soundPath?.let {
            addFile(
                zip,
                File(context.filesDir, "audio/$it"),
                "audio/$it",
                context
            )
            onFileDone()
        }

        project.overlay.soundPath?.let {
            addFile(
                zip,
                File(context.filesDir, "audio/$it"),
                "audio/$it",
                context
            )
            onFileDone()
        }

        project.clock.clockFont?.let {
            addFile(
                zip,
                File(context.filesDir, "fonts/$it"),
                "fonts/$it",
                context
            )
            onFileDone()
        }

        project.clock.dateFont?.let {
            addFile(
                zip,
                File(context.filesDir, "fonts/$it"),
                "fonts/$it",
                context
            )
            onFileDone()
        }
    }
}
