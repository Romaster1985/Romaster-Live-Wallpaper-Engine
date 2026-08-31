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

object ImageStorage {

    fun imagesDir(context: Context): File {
        val dir = File(context.filesDir, "images")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getImageFile(context: Context, fileName: String): File =
        File(imagesDir(context), fileName)

    fun importImage(
        context: Context,
        uri: Uri,
        layerId: String
    ): String {
        val mime = context.contentResolver.getType(uri) ?: "image/png"
        val ext = when {
            mime.contains("gif", ignoreCase = true) -> "gif"
            mime.contains("webp", ignoreCase = true) -> "webp"
            mime.contains("jpeg", ignoreCase = true) ||
                mime.contains("jpg", ignoreCase = true) -> "jpg"
            mime.contains("png", ignoreCase = true) -> "png"
            mime.contains("bmp", ignoreCase = true) -> "bmp"
            else -> "img"
        }
        val fileName = "${layerId}.$ext"
        imagesDir(context).listFiles()?.forEach { f ->
            if (f.nameWithoutExtension == layerId) f.delete()
        }
        val dest = getImageFile(context, fileName)
        context.contentResolver.openInputStream(uri)?.use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        } ?: throw IllegalStateException("No se pudo leer la imagen")
        return fileName
    }

    fun deleteImage(context: Context, fileName: String?) {
        if (fileName.isNullOrBlank()) return
        try {
            getImageFile(context, fileName).delete()
        } catch (_: Exception) {
        }
    }

    fun clearAll(context: Context) {
        try {
            imagesDir(context).deleteRecursively()
        } catch (_: Exception) {
        }
    }
}
