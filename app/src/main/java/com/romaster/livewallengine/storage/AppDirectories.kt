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

    /**
     * Clips invertidos para ping-pong del overlay.
     */
    fun pingpong(context: Context): File {
        return File(
            context.filesDir,
            "pingpong"
        ).apply {
            mkdirs()
        }
    }

    /**
     * Carpeta pública de la app en Documentos
     * (misma base que usa FileLogger).
     */
    fun publicDocumentsRoot(): File {
        return File(
            "/storage/emulated/0/Documents/Romaster_LiveWall_Engine"
        ).apply {
            mkdirs()
        }
    }

    /**
     * Destino de los ZIP descargados desde la galería de GitHub.
     */
    fun galleryDownloads(context: Context): File {
        return try {
            File(publicDocumentsRoot(), "downloads").apply {
                if (!exists()) {
                    mkdirs()
                }
                if (!canWrite()) {
                    throw SecurityException("No write access")
                }
            }
        } catch (_: Exception) {
            File(
                context.getExternalFilesDir(null) ?: context.filesDir,
                "gallery_downloads"
            ).apply {
                mkdirs()
            }
        }
    }
}
