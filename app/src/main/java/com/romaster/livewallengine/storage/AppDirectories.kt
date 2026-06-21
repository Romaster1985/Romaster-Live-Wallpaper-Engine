package com.romaster.livewallengine.storage

import android.content.Context
import java.io.File

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
}