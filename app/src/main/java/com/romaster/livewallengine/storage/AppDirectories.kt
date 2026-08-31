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
import android.provider.MediaStore
import java.io.File
import java.io.FileInputStream
import java.io.InputStream

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

    fun privateGalleryZip(context: Context, zipFileName: String): File =
        File(galleryDownloads(context), zipFileName)

    fun publicGalleryZip(zipFileName: String): File =
        File(publicDocumentsRoot(), "downloads${File.separator}$zipFileName")

    fun isUsableZip(file: File): Boolean {
        return try {
            file.exists() && file.isFile && file.length() > 32L && file.canRead()
        } catch (_: Exception) {
            false
        }
    }

    /**
     * True si hay un ZIP con ese nombre en privado o en Documentos
     * (aunque el path público no sea "canRead" por ownership: puede abrirse por MediaStore).
     */
    fun galleryZipPresent(context: Context, zipFileName: String): Boolean {
        if (isUsableZip(privateGalleryZip(context, zipFileName))) return true
        val pub = publicGalleryZip(zipFileName)
        return try {
            pub.exists() && pub.isFile && pub.length() > 32L
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Abre el ZIP como InputStream, igual de espíritu que "Importar":
     * 1) archivo privado de la app
     * 2) path público si se puede leer
     * 3) MediaStore / content URI (ZIP viejo en Documentos tras reinstalar)
     */
    fun openGalleryZipStream(context: Context, zipFileName: String): InputStream? {
        val private = privateGalleryZip(context, zipFileName)
        if (isUsableZip(private)) {
            return try { FileInputStream(private) } catch (_: Exception) { null }
        }

        val public = publicGalleryZip(zipFileName)
        if (isUsableZip(public)) {
            return try { FileInputStream(public) } catch (_: Exception) { null }
        }

        // Path público existe pero FileInputStream falla → MediaStore
        if (public.exists() && public.length() > 32L) {
            findPublicZipUri(context, zipFileName)?.let { uri ->
                try {
                    return context.contentResolver.openInputStream(uri)
                } catch (_: Exception) {
                }
            }
        }
        return null
    }

    /**
     * Busca el ZIP en MediaStore por nombre bajo Documents/Romaster_LiveWall_Engine/downloads.
     */
    fun findPublicZipUri(context: Context, zipFileName: String): Uri? {
        return try {
            val collection = MediaStore.Files.getContentUri("external")
            val projection = arrayOf(MediaStore.Files.FileColumns._ID)
            val selection =
                "${MediaStore.Files.FileColumns.DISPLAY_NAME}=? AND (" +
                    "${MediaStore.Files.FileColumns.RELATIVE_PATH} LIKE ? OR " +
                    "${MediaStore.Files.FileColumns.RELATIVE_PATH} LIKE ?)"
            val args = arrayOf(
                zipFileName,
                "%Romaster_LiveWall_Engine/downloads%",
                "%Romaster_LiveWall_Engine%/downloads%"
            )
            context.contentResolver.query(
                collection, projection, selection, args, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val id = cursor.getLong(0)
                    return android.content.ContentUris.withAppendedId(collection, id)
                }
            }
            // Fallback: solo por nombre
            val selection2 = "${MediaStore.Files.FileColumns.DISPLAY_NAME}=?"
            context.contentResolver.query(
                collection,
                projection,
                selection2,
                arrayOf(zipFileName),
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val id = cursor.getLong(0)
                    return android.content.ContentUris.withAppendedId(collection, id)
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    /** @deprecated Preferir openGalleryZipStream / galleryZipPresent */
    fun resolveGalleryZip(context: Context, zipFileName: String): File? {
        val private = privateGalleryZip(context, zipFileName)
        if (isUsableZip(private)) return private
        val public = publicGalleryZip(zipFileName)
        if (isUsableZip(public)) return public
        return null
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
