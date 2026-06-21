package com.romaster.livewallengine.storage

import android.content.Context
import com.romaster.livewallengine.model.WallpaperProject
import java.io.File

object StorageManager {

    private const val FILE_NAME =
        "current_project.json"

    fun saveProject(
        context: Context,
        project: WallpaperProject
    ) {

        val file = File(
            AppDirectories.projects(context),
            FILE_NAME
        )

        file.writeText(
            ProjectSerializer.encode(project)
        )
    }

    fun loadProject(
        context: Context
    ): WallpaperProject? {

        val file = File(
            AppDirectories.projects(context),
            FILE_NAME
        )

        if (!file.exists()) {
            return null
        }

        return ProjectSerializer.decode(
            file.readText()
        )
    }
}