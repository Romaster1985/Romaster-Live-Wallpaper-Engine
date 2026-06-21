package com.romaster.livewallengine.editor

import android.content.Context
import com.romaster.livewallengine.project.ProjectManager
import com.romaster.livewallengine.storage.StorageManager

class MainEditorController(
    private val context: Context
) {

    fun save() {

        StorageManager.saveProject(
            context,
            ProjectManager.getProject()
        )
    }

    fun load() {

        StorageManager.loadProject(
            context
        )?.let {

            ProjectManager.saveProject(it)
        }
    }
}