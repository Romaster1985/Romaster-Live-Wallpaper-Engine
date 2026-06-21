package com.romaster.livewallengine.video

import android.content.Context
import android.net.Uri
import com.romaster.livewallengine.storage.AppDirectories
import java.io.File

object VideoStorage {

    const val WALLPAPER_VIDEO =
        "wallpaper_video.mp4"

    const val OVERLAY_VIDEO =
        "overlay_video.mp4"

    fun importWallpaperVideo(
        context: Context,
        uri: Uri
    ): String {

        return importVideo(
            context,
            uri,
            WALLPAPER_VIDEO
        )
    }

    fun importOverlayVideo(
        context: Context,
        uri: Uri
    ): String {

        return importVideo(
            context,
            uri,
            OVERLAY_VIDEO
        )
    }

    private fun importVideo(
        context: Context,
        uri: Uri,
        fileName: String
    ): String {

        val destination = File(
            AppDirectories.videos(context),
            fileName
        )

        context.contentResolver
            .openInputStream(uri)
            ?.use { input ->

                destination.outputStream()
                    .use { output ->

                        input.copyTo(output)
                    }
            }

        return fileName
    }

    fun getVideoFile(
        context: Context,
        fileName: String
    ): File {

        return File(
            AppDirectories.videos(context),
            fileName
        )
    }
    
    fun clear(context: Context) {

        File(
            context.filesDir,
            "videos"
        ).deleteRecursively()
    
    }
}