package com.romaster.livewallengine.audio

import android.content.Context
import android.media.MediaPlayer
import java.io.File

class WallpaperSoundPlayer(

    private val context: Context

) {

    private var mediaPlayer: MediaPlayer? = null

    private var currentFile: File? = null

    private var volume = 1f

    fun play(

        file: File,

        volume: Float,

        restart: Boolean = true

    ) {

        // Si ya está reproduciendo exactamente este archivo,
        // solamente actualizamos el volumen.

        if (
            mediaPlayer != null &&
            mediaPlayer!!.isPlaying &&
            currentFile?.absolutePath == file.absolutePath
        ) {

            setVolume(volume)

            return
        }

        release()

        mediaPlayer = MediaPlayer().apply {

            setDataSource(file.absolutePath)

            isLooping = false

            prepare()

            val v = volume.coerceIn(0f, 1f)

            setVolume(v, v)

            if (restart) {

                seekTo(0)

            }

            currentFile = file

            start()
        }
    }

    fun loadAndPlay(

        fileName: String?,

        volume: Float

    ) {

        if (fileName.isNullOrEmpty())
            return

        val file = File(

            context.filesDir,

            "audio/$fileName"

        )

        if (!file.exists())
            return

        play(

            file,

            volume

        )
    }

    fun pause() {

        mediaPlayer?.pause()

    }

    fun stop() {

        mediaPlayer?.let {

            try {

                it.pause()

                it.seekTo(0)

            } catch (_: Exception) {
            }

        }

        currentFile = null

    }

    fun setVolume(

        value: Float

    ) {

        volume = value.coerceIn(

            0f,

            1f

        )

        mediaPlayer?.setVolume(

            volume,

            volume

        )
    }

    fun isPlaying(): Boolean =

        mediaPlayer?.isPlaying ?: false

    fun release() {

        mediaPlayer?.release()

        mediaPlayer = null

        currentFile = null

    }
}