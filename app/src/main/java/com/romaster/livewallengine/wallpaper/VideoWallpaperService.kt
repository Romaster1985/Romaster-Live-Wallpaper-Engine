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

package com.romaster.livewallengine.wallpaper

import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

import com.romaster.livewallengine.storage.StorageManager
import com.romaster.livewallengine.video.VideoStorage

class VideoWallpaperService :
    WallpaperService() {

    override fun onCreateEngine(): Engine {
        return VideoEngine()
    }

    inner class VideoEngine :
        Engine() {

        private var player:
            ExoPlayer? = null

        override fun onSurfaceCreated(
            holder: SurfaceHolder
        ) {

            super.onSurfaceCreated(holder)

            val project =
                StorageManager.loadProject(
                    applicationContext
                ) ?: return

            val fileName =
                project.wallpaperVideo
                    ?: return

            val file =
                VideoStorage.getVideoFile(
                    applicationContext,
                    fileName
                )

            if (!file.exists()) {
                return
            }

            player =
                ExoPlayer.Builder(
                    applicationContext
                ).build()

            player?.setVideoSurface(
                holder.surface
            )

            player?.setMediaItem(
                MediaItem.fromUri(
                    file.toURI().toString()
                )
            )

            player?.repeatMode =
                Player.REPEAT_MODE_ONE

            player?.prepare()

            player?.play()
        }

        override fun onVisibilityChanged(
            visible: Boolean
        ) {

            player?.let {

                if (visible) {
                    it.play()
                } else {
                    it.pause()
                }
            }
        }

        override fun onDestroy() {

            player?.release()

            player = null

            super.onDestroy()
        }
    }
}