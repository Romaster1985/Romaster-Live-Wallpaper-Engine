/*
 * Copyright 2026 Román Ignacio Romero (Romaster)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Nota: Este proyecto incluye ColorPickerView (skydoves) licenciado bajo Apache 2.0.
 */

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

    /** Clip invertido del overlay para cue locked (ping-pong). */
    const val OVERLAY_REVERSE_LOCKED =
        "overlay_video_reverse_locked.mp4"

    /** Clip invertido del overlay para cue unlocked (ping-pong). */
    const val OVERLAY_REVERSE_UNLOCKED =
        "overlay_video_reverse_unlocked.mp4"

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

    fun getReverseLockedFile(context: Context): File =
        getVideoFile(context, OVERLAY_REVERSE_LOCKED)

    fun getReverseUnlockedFile(context: Context): File =
        getVideoFile(context, OVERLAY_REVERSE_UNLOCKED)

    fun reverseFileName(locked: Boolean): String =
        if (locked) OVERLAY_REVERSE_LOCKED else OVERLAY_REVERSE_UNLOCKED

    fun getReverseFile(context: Context, locked: Boolean): File =
        if (locked) getReverseLockedFile(context) else getReverseUnlockedFile(context)

    /**
     * Borra solo los clips de reversa del overlay (nombres fijos).
     * También limpia residuos viejos de la carpeta pingpong/.
     */
    fun clearReverseClips(context: Context) {
        try {
            getReverseLockedFile(context).delete()
        } catch (_: Exception) {
        }
        try {
            getReverseUnlockedFile(context).delete()
        } catch (_: Exception) {
        }
        // Residuos de versiones anteriores
        try {
            val oldDir = File(context.filesDir, "pingpong")
            if (oldDir.exists()) {
                oldDir.deleteRecursively()
            }
        } catch (_: Exception) {
        }
    }
    
    fun clear(context: Context) {

        File(
            context.filesDir,
            "videos"
        ).deleteRecursively()

        try {
            File(context.filesDir, "pingpong").deleteRecursively()
        } catch (_: Exception) {
        }
    
    }
}
