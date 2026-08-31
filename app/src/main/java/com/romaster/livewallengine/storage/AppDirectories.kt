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
     * Destino PRINCIPAL de los ZIP de la galería (siempre escribible).
     * Almacenamiento privado de la app: sobrevive a actualizaciones de la app
     * y evita el problema de ZIP "huérfanos" en Documentos tras reinstalar
     * (el nuevo UID no puede sobrescribir ni a veces leer esos archivos).
     */
    fun galleryDownloads(context: Context): File {
        return File(
            context.getExternalFilesDir(null) ?: context.filesDir,
            "gallery_downloads"
        ).apply {
            mkdirs()
        }
    }

    /**
     * Copia opcional en Documentos para que el usuario vea los ZIP
     * en el explorador de archivos. Puede fallar tras reinstalar;
     * no se usa como fuente de verdad.
     */
    fun publicGalleryDownloads(): File? {
        return try {
            File(publicDocumentsRoot(), "downloads").apply {
                mkdirs()
            }.takeIf { it.exists() }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * ZIP local usable: prioriza privado; si solo existe uno público legible,
     * intenta copiarlo a privado.
     */
    fun resolveGalleryZip(context: Context, zipFileName: String): File? {
        val private = File(galleryDownloads(context), zipFileName)
        if (isUsableZip(private)) return private

        val publicDir = publicGalleryDownloads() ?: return null
        val public = File(publicDir, zipFileName)
        if (!isUsableZip(public)) return null

        // Migrar a privado para futuros apply / re-descargas
        return try {
            public.copyTo(private, overwrite = true)
            if (isUsableZip(private)) private else public
        } catch (_: Exception) {
            // Público legible pero no copiable: devolver público
            if (public.canRead()) public else null
        }
    }

    fun isUsableZip(file: File): Boolean {
        return try {
            file.exists() && file.isFile && file.length() > 32L && file.canRead()
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Escribe de forma atómica en privado y, si se puede, espeja a Documentos.
     */
    fun saveGalleryZip(context: Context, zipFileName: String, sourceTemp: File): File {
        val privateDir = galleryDownloads(context)
        val privateTarget = File(privateDir, zipFileName)

        // Borrar destino privado si existe (siempre propio de esta instalación)
        if (privateTarget.exists()) {
            privateTarget.delete()
        }
        if (!sourceTemp.renameTo(privateTarget)) {
            sourceTemp.copyTo(privateTarget, overwrite = true)
            sourceTemp.delete()
        }

        // Espejo público best-effort (no debe romper la descarga)
        try {
            val publicDir = publicGalleryDownloads()
            if (publicDir != null) {
                val publicTarget = File(publicDir, zipFileName)
                // Si no se puede borrar/sobrescribir (ZIP de otra instalación), se omite
                val canWritePublic = try {
                    if (publicTarget.exists()) {
                        publicTarget.delete() || publicTarget.canWrite()
                    } else {
                        publicDir.canWrite()
                    }
                } catch (_: Exception) {
                    false
                }
                if (canWritePublic) {
                    privateTarget.copyTo(publicTarget, overwrite = true)
                }
            }
        } catch (_: Exception) {
        }

        return privateTarget
    }
}
