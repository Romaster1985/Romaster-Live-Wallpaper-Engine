package com.romaster.livewallengine.video

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.view.Surface

import com.romaster.livewallengine.R
import com.romaster.livewallengine.debug.FileLogger
import com.romaster.livewallengine.storage.StorageManager

class OverlayVideoPlayer(

    private val context: Context

) {

    private var mediaPlayer: MediaPlayer? = null

    private var prepared = false

    fun initialize(
        surface: Surface
    ) {

        if (prepared) {
            return
        }

        val project =
            StorageManager.loadProject(
                context
            )

        val fileName =
            project?.overlayVideo

        mediaPlayer =
            if (fileName != null) {

                val file =
                    VideoStorage.getVideoFile(
                        context,
                        fileName
                    )

                if (file.exists()) {

                    FileLogger.log(
                        context,
                        "VideoPlayer: usando video del usuario -> ${file.absolutePath}"
                    )

                    MediaPlayer().apply {

                        setDataSource(
                            context,
                            Uri.fromFile(file)
                        )

                        setSurface(surface)

                        isLooping = true

                        installListeners()

                        prepare()
                    }

                } else {

                    FileLogger.log(
                        context,
                        "VideoPlayer: no existe el video del usuario, usando test.mp4"
                    )

                    MediaPlayer.create(
                        context,
                        R.raw.test
                    )?.apply {

                        setSurface(surface)

                        isLooping = true

                        installListeners()
                    }
                }

            } else {

                FileLogger.log(
                    context,
                    "VideoPlayer: usando test.mp4"
                )

                MediaPlayer.create(
                    context,
                    R.raw.test
                )?.apply {

                    setSurface(surface)

                    isLooping = true

                    installListeners()
                }
            }

        prepared = true
    }

    private fun MediaPlayer.installListeners() {

        setOnPreparedListener {

            FileLogger.log(
                context,
                "MediaPlayer -> onPrepared()"
            )
        }

        setOnCompletionListener {

            FileLogger.log(
                context,
                "MediaPlayer -> onCompletion()"
            )
        }

        setOnSeekCompleteListener {

            FileLogger.log(
                context,
                "MediaPlayer -> onSeekComplete()"
            )
        }

        setOnVideoSizeChangedListener { _, width, height ->

            FileLogger.log(
                context,
                "MediaPlayer -> VideoSize ${width}x${height}"
            )
        }

        setOnInfoListener { _, what, extra ->

            FileLogger.log(
                context,
                "MediaPlayer -> onInfo what=$what extra=$extra"
            )

            false
        }

        setOnErrorListener { _, what, extra ->

            FileLogger.log(
                context,
                "MediaPlayer -> ERROR what=$what extra=$extra"
            )

            false
        }
    }

    fun play() {

        mediaPlayer?.let {

            if (!it.isPlaying) {

                FileLogger.log(
                    context,
                    "MediaPlayer.start()"
                )

                it.start()
            }
        }
    }

    fun pause() {

        mediaPlayer?.let {

            if (it.isPlaying) {

                FileLogger.log(
                    context,
                    "MediaPlayer.pause()"
                )

                it.pause()
            }
        }
    }

    fun seekTo(
        position: Int
    ) {

        FileLogger.log(
            context,
            "MediaPlayer.seekTo($position)"
        )

        mediaPlayer?.seekTo(position)
    }

    fun getCurrentPosition(): Int {

        return mediaPlayer?.currentPosition ?: 0
    }

    fun getDuration(): Int {

        return mediaPlayer?.duration ?: 0
    }

    fun getVideoWidth(): Int {

        return mediaPlayer?.videoWidth ?: 0
    }

    fun getVideoHeight(): Int {

        return mediaPlayer?.videoHeight ?: 0
    }

    fun isPlaying(): Boolean {

        return mediaPlayer?.isPlaying ?: false
    }
    
    fun setVolume(volume: Float) {
    
        mediaPlayer?.setVolume(
            volume.coerceIn(0f, 1f),
            volume.coerceIn(0f, 1f)
        )
    
    }

    fun release() {

        FileLogger.log(
            context,
            "MediaPlayer.release()"
        )

        mediaPlayer?.release()

        mediaPlayer = null

        prepared = false
    }
    
    fun reload(
        surface: Surface
    ) {
    
        release()
    
        initialize(surface)
    
        play()
    
    }
}
