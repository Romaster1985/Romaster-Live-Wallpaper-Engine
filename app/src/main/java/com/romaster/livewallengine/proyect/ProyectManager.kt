package com.romaster.livewallengine.project

import com.romaster.livewallengine.model.WallpaperProject

object ProjectManager {

    private var currentProject =
        WallpaperProject()

    fun getProject(): WallpaperProject {
        return currentProject
    }

    fun saveProject(
        project: WallpaperProject
    ) {
        currentProject = project
    }

    fun resetProject() {
        currentProject =
            WallpaperProject()
    }
}