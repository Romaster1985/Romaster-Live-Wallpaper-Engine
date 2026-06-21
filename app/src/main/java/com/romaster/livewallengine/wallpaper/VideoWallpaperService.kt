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