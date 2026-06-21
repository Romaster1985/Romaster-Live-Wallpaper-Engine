package com.romaster.livewallengine.project

import com.romaster.livewallengine.model.WallpaperProject

object ProjectManager {

    private var currentProject =
        DefaultProject.create()

    private var revision = 0

    fun getProject(): WallpaperProject {
        return currentProject
    }

    fun saveProject(
        project: WallpaperProject
    ) {

        currentProject = project

        revision++

    }

    fun setProject(
        project: WallpaperProject
    ) {

        currentProject = project

        revision++

    }

    fun resetProject() {

        currentProject =
            DefaultProject.create()

        revision++

    }

    fun setWallpaperVideo(
        path: String
    ) {

        currentProject.wallpaperVideo = path

        revision++

    }

    fun setOverlayVideo(
        path: String
    ) {

        currentProject.overlayVideo = path

        revision++

    }

    fun getRevision(): Int {
        return revision
    }

}