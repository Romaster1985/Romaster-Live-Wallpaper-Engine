package com.romaster.livewallengine.video

import android.content.Context
import android.net.Uri
import com.romaster.livewallengine.storage.AppDirectories
import java.io.File

object VideoStorage {

    private const val VIDEO_NAME =
        "wallpaper_video.mp4"

    fun importVideo(
        context: Context,
        uri: Uri
    ): String {

        val destination = File(
            AppDirectories.videos(context),
            VIDEO_NAME
        )

        context.contentResolver
            .openInputStream(uri)
            ?.use { input ->

                destination.outputStream()
                    .use { output ->

                        input.copyTo(output)
                    }
            }

        return VIDEO_NAME
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
}