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
import android.media.MediaPlayer
import android.net.Uri
import android.view.Surface

import com.romaster.livewallengine.R
import com.romaster.livewallengine.debug.FileLogger
import com.romaster.livewallengine.storage.StorageManager

class VideoPlayer(

private val context: Context,
private val playerName: String = "VIDEO"

) {

private var mediaPlayer: MediaPlayer? = null

private var prepared = false

private var onVideoSizeChanged:
    ((Int, Int) -> Unit)? = null

// ============================================
// IDENTIFICACIÓN
// ============================================

private fun log(
    message: String
) {

    FileLogger.log(
        context,
        "VideoPlayer[$playerName] $message"
    )
}

private fun logException(
    operation: String,
    exception: Exception
) {

    FileLogger.logException(
        context,
        "VideoPlayer[$playerName] $operation",
        exception
    )
}

private fun playerHash(): String {

    return mediaPlayer
        ?.hashCode()
        ?.toString()
        ?: "null"
}

// ============================================
// INITIALIZE
// ============================================

fun initialize(
    surface: Surface
) {

    if (prepared) {

        log(
            "initialize() ignorado: ya preparado " +
                "hash=${playerHash()}"
        )

        return
    }

    log(
        "initialize() iniciado"
    )

    val project =
        StorageManager.loadProject(
            context
        )

    val fileName =
        project?.wallpaperVideo

    mediaPlayer =
        try {

            if (fileName != null) {

                val file =
                    VideoStorage.getVideoFile(
                        context,
                        fileName
                    )

                if (file.exists()) {

                    log(
                        "usando video del usuario -> " +
                            file.absolutePath
                    )

                    MediaPlayer().apply {

                        log(
                            "MediaPlayer creado " +
                                "hash=${hashCode()}"
                        )

                        setDataSource(
                            context,
                            Uri.fromFile(file)
                        )

                        log(
                            "setDataSource() completado " +
                                "hash=${hashCode()}"
                        )

                        setSurface(surface)

                        log(
                            "setSurface() completado " +
                                "hash=${hashCode()}"
                        )

                        isLooping = true

                        log(
                            "setLooping(true) " +
                                "hash=${hashCode()}"
                        )

                        installListeners()

                        prepare()

                        log(
                            "prepare() completado " +
                                "hash=${hashCode()}"
                        )
                    }

                } else {

                    log(
                        "no existe el video del usuario, " +
                            "usando test.mp4"
                    )

                    MediaPlayer.create(
                        context,
                        R.raw.test
                    )?.apply {

                        log(
                            "MediaPlayer.create() -> " +
                                "hash=${hashCode()}"
                        )

                        setSurface(surface)

                        log(
                            "setSurface() completado " +
                                "hash=${hashCode()}"
                        )

                        isLooping = true

                        log(
                            "setLooping(true) " +
                                "hash=${hashCode()}"
                        )

                        installListeners()
                    }
                }

            } else {

                log(
                    "usando test.mp4"
                )

                MediaPlayer.create(
                    context,
                    R.raw.test
                )?.apply {

                    log(
                        "MediaPlayer.create() -> " +
                            "hash=${hashCode()}"
                    )

                    setSurface(surface)

                    log(
                        "setSurface() completado " +
                            "hash=${hashCode()}"
                    )

                    isLooping = true

                    log(
                        "setLooping(true) " +
                            "hash=${hashCode()}"
                    )

                    installListeners()
                }
            }

        } catch (e: Exception) {

            logException(
                "initialize()",
                e
            )

            null
        }

    prepared =
        mediaPlayer != null

    log(
        "initialize() finalizado -> " +
            "prepared=$prepared " +
            "hash=${playerHash()}"
    )
}

// ============================================
// VIDEO SIZE LISTENER
// ============================================

fun setOnVideoSizeChangedListener(
    listener: (Int, Int) -> Unit
) {

    onVideoSizeChanged = listener

    log(
        "setOnVideoSizeChangedListener()"
    )
}

// ============================================
// MEDIA PLAYER LISTENERS
// ============================================

private fun MediaPlayer.installListeners() {

    val instanceHash =
        hashCode()

    setOnPreparedListener {

        log(
            "onPrepared() hash=$instanceHash"
        )
    }

    setOnCompletionListener {

        log(
            "onCompletion() hash=$instanceHash"
        )
    }

    setOnSeekCompleteListener {

        log(
            "onSeekComplete() hash=$instanceHash"
        )
    }

    setOnVideoSizeChangedListener {
            _,
            width,
            height ->

        log(
            "VideoSize ${width}x${height} " +
                "hash=$instanceHash"
        )

        onVideoSizeChanged?.invoke(
            width,
            height
        )
    }

    setOnInfoListener {
            _,
            what,
            extra ->

        log(
            "onInfo what=$what " +
                "extra=$extra " +
                "hash=$instanceHash"
        )

        false
    }

    setOnErrorListener {
            _,
            what,
            extra ->

        log(
            "ERROR what=$what " +
                "extra=$extra " +
                "hash=$instanceHash"
        )

        false
    }
}

// ============================================
// PLAY
// ============================================

fun play() {

    mediaPlayer?.let { player ->

        val hash =
            player.hashCode()

        try {

            val playing =
                player.isPlaying

            log(
                "play() hash=$hash " +
                    "isPlaying=$playing " +
                    "prepared=$prepared"
            )

            if (!playing) {

                log(
                    "MediaPlayer.start() " +
                        "hash=$hash"
                )

                player.start()

            } else {

                log(
                    "start() omitido: " +
                        "MediaPlayer ya reproduciendo " +
                        "hash=$hash"
                )
            }

        } catch (e: IllegalStateException) {

            logException(
                "play() IllegalStateException " +
                    "hash=$hash",
                e
            )

        } catch (e: Exception) {

            logException(
                "play() hash=$hash",
                e
            )
        }

    } ?: run {

        log(
            "play() ignorado: mediaPlayer=null"
        )
    }
}

// ============================================
// PAUSE
// ============================================

fun pause() {

    mediaPlayer?.let { player ->

        val hash =
            player.hashCode()

        try {

            if (player.isPlaying) {

                log(
                    "MediaPlayer.pause() " +
                        "hash=$hash"
                )

                player.pause()

            } else {

                log(
                    "pause() omitido: " +
                        "MediaPlayer no estaba reproduciendo " +
                        "hash=$hash"
                )
            }

        } catch (e: IllegalStateException) {

            logException(
                "pause() IllegalStateException " +
                    "hash=$hash",
                e
            )

        } catch (e: Exception) {

            logException(
                "pause() hash=$hash",
                e
            )
        }
    }
}

// ============================================
// SEEK
// ============================================

fun seekTo(
    position: Int
) {

    mediaPlayer?.let { player ->

        val hash =
            player.hashCode()

        try {

            log(
                "MediaPlayer.seekTo($position) " +
                    "hash=$hash"
            )

            player.seekTo(
                position
            )

        } catch (e: IllegalStateException) {

            logException(
                "seekTo($position) " +
                    "IllegalStateException " +
                    "hash=$hash",
                e
            )

        } catch (e: Exception) {

            logException(
                "seekTo($position) " +
                    "hash=$hash",
                e
            )
        }

    } ?: run {

        log(
            "seekTo($position) ignorado: " +
                "mediaPlayer=null"
        )
    }
}

// ============================================
// CURRENT POSITION
// ============================================

fun getCurrentPosition(): Int {

    return try {

        mediaPlayer?.currentPosition ?: 0

    } catch (e: Exception) {

        logException(
            "getCurrentPosition()",
            e
        )

        0
    }
}

// ============================================
// DURATION
// ============================================

fun getDuration(): Int {

    return try {

        mediaPlayer?.duration ?: 0

    } catch (e: Exception) {

        logException(
            "getDuration()",
            e
        )

        0
    }
}

// ============================================
// VIDEO WIDTH
// ============================================

fun getVideoWidth(): Int {

    return try {

        mediaPlayer?.videoWidth ?: 0

    } catch (e: Exception) {

        logException(
            "getVideoWidth()",
            e
        )

        0
    }
}

// ============================================
// VIDEO HEIGHT
// ============================================

fun getVideoHeight(): Int {

    return try {

        mediaPlayer?.videoHeight ?: 0

    } catch (e: Exception) {

        logException(
            "getVideoHeight()",
            e
        )

        0
    }
}

// ============================================
// IS PLAYING
// ============================================

fun isPlaying(): Boolean {

    return try {

        mediaPlayer?.isPlaying ?: false

    } catch (e: Exception) {

        logException(
            "isPlaying()",
            e
        )

        false
    }
}

// ============================================
// VOLUME
// ============================================

fun setVolume(
    volume: Float
) {

    mediaPlayer?.let { player ->

        try {

            player.setVolume(
                volume,
                volume
            )

        } catch (e: Exception) {

            logException(
                "setVolume($volume) " +
                    "hash=${player.hashCode()}",
                e
            )
        }
    }
}

// ============================================
// RELEASE
// ============================================

fun release() {

    val player =
        mediaPlayer

    if (player == null) {

        log(
            "release() ignorado: " +
                "mediaPlayer=null"
        )

        prepared = false

        return
    }

    val hash =
        player.hashCode()

    log(
        "MediaPlayer.release() " +
            "hash=$hash"
    )

    try {

        player.release()

    } catch (e: Exception) {

        logException(
            "release() hash=$hash",
            e
        )

    } finally {

        mediaPlayer = null

        prepared = false

        log(
            "release() finalizado " +
                "hash=$hash"
        )
    }
}

// ============================================
// RELOAD
// ============================================

fun reload(
    surface: Surface
) {

    log(
        "reload()"
    )

    release()

    initialize(
        surface
    )

    play()
}

}