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