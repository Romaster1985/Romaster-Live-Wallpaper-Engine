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

package com.romaster.livewallengine.font

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns

import java.io.File

object FontStorage {

    fun getFontsDir(
        context: Context
    ): File {

        val dir =
            File(
                context.filesDir,
                "fonts"
            )

        if (!dir.exists()) {
            dir.mkdirs()
        }

        return dir
    }

    fun getFontFile(
        context: Context,
        name: String
    ): File {

        return File(
            getFontsDir(context),
            name
        )
    }

    fun importFont(
        context: Context,
        uri: Uri
    ): String {
    
        val resolver =
            context.contentResolver
    
        var fileName =
            "font.ttf"
    
        resolver.query(
            uri,
            null,
            null,
            null,
            null
        )?.use { cursor ->
    
            val nameIndex =
                cursor.getColumnIndex(
                    android.provider.OpenableColumns.DISPLAY_NAME
                )
    
            if (
                nameIndex >= 0 &&
                cursor.moveToFirst()
            ) {
    
                fileName =
                    cursor.getString(
                        nameIndex
                    )
            }
        }
    
        val target =
            getFontFile(
                context,
                fileName
            )
    
        resolver.openInputStream(uri)
            ?.use { input ->
    
                target.outputStream()
                    .use { output ->
    
                        input.copyTo(
                            output
                        )
                    }
            }
    
        return fileName
    }
    
    fun getInstalledFonts(
        context: Context
    ): List<String> {
    
        return getFontsDir(
            context
        )
            .listFiles()
            ?.map {
                it.name
            }
            ?.sorted()
            ?: emptyList()
    }

    /**
     * Copia un archivo de fuente ya descargado a la carpeta fonts/
     * (misma lógica que importFont desde URI).
     * @return nombre del archivo instalado
     */
    fun importFontFile(
        context: Context,
        source: File
    ): String {
        val fileName = source.name
        val target = getFontFile(context, fileName)
        source.inputStream().use { input ->
            target.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        return fileName
    }
}
