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

package com.romaster.livewallengine.image

import android.content.Context
import android.net.Uri
import java.io.File

/** Almacenamiento de máscaras de recorte (PNG) para la tab Efectos. */
object MaskStorage {

    fun masksDir(context: Context): File {
        val dir = File(context.filesDir, "masks")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getMaskFile(context: Context, fileName: String): File =
        File(masksDir(context), fileName)

    /**
     * Importa una imagen (idealmente PNG con alpha) como máscara del efecto [effectId].
     * @return nombre de archivo relativo en masks/
     */
    fun importMask(context: Context, uri: Uri, effectId: String): String {
        val mime = context.contentResolver.getType(uri) ?: "image/png"
        val pathHint = uri.lastPathSegment?.lowercase() ?: ""
        val ext = when {
            mime.contains("png", ignoreCase = true) || pathHint.endsWith(".png") -> "png"
            mime.contains("webp", ignoreCase = true) || pathHint.endsWith(".webp") -> "webp"
            mime.contains("jpeg", ignoreCase = true) ||
                mime.contains("jpg", ignoreCase = true) ||
                pathHint.endsWith(".jpg") || pathHint.endsWith(".jpeg") -> "jpg"
            else -> "png"
        }
        // ID seguro para nombre de archivo
        val safeId = effectId.replace(Regex("[^A-Za-z0-9_-]"), "_")
        val fileName = "mask_${safeId}.$ext"
        masksDir(context).listFiles()?.forEach { f ->
            if (f.name.startsWith("mask_${safeId}.")) f.delete()
        }
        val dest = getMaskFile(context, fileName)
        val input = context.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("No se pudo abrir la URI")
        input.use { inp ->
            dest.outputStream().use { out ->
                val n = inp.copyTo(out)
                if (n <= 0L) throw IllegalStateException("Archivo vacío")
            }
        }
        if (!dest.exists() || dest.length() <= 0L) {
            throw IllegalStateException("No se pudo guardar la máscara")
        }
        return fileName
    }

    fun deleteMask(context: Context, fileName: String?) {
        if (fileName.isNullOrBlank()) return
        try {
            getMaskFile(context, fileName).delete()
        } catch (_: Exception) {
        }
    }

    fun clearUnused(context: Context, usedNames: Set<String>) {
        try {
            masksDir(context).listFiles()?.forEach { f ->
                if (f.name !in usedNames) f.delete()
            }
        } catch (_: Exception) {
        }
    }
}
