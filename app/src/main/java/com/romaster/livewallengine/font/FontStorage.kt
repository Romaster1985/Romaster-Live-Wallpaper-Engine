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
}