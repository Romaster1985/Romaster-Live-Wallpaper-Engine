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

package com.romaster.livewallengine.render

import android.content.Context
import android.os.SystemClock
import android.view.Surface
import com.romaster.livewallengine.model.OverlayAspectMode
import com.romaster.livewallengine.project.ProjectManager
import com.romaster.livewallengine.video.OverlayPlaybackDirection
import com.romaster.livewallengine.video.OverlayVideoPlayer
import com.romaster.livewallengine.video.ReverseClipKind

/**
 * Overlay de video con dos decoders.
 *
 * Player A y B se intercambian de rol tras cada crossfade de loop:
 * el que entró en la transición sigue como activo (sin seek) y el
 * saliente queda en espera para la próxima vuelta.
 */
class GLVideoOverlayRenderer(
    private val context: Context
) {

    private val textureA = ExternalTexture()
    private val textureB = ExternalTexture()
    private val quadRenderer = GLExternalQuadRenderer()
    private val playerA = OverlayVideoPlayer(context)
    private val playerB = OverlayVideoPlayer(context)

    /** 0 = A activo, 1 = B activo. */
    @Volatile
    private var activeIndex: Int = 0

    private val activePlayer: OverlayVideoPlayer
        get() = if (activeIndex == 0) playerA else playerB

    private val standbyPlayer: OverlayVideoPlayer
        get() = if (activeIndex == 0) playerB else playerA

    private val activeTexture: ExternalTexture
        get() = if (activeIndex == 0) textureA else textureB

    private val standbyTexture: ExternalTexture
        get() = if (activeIndex == 0) textureB else textureA

    private var fadeDurationOverrideMs: Long = -1L

    private val fadeDurationMs: Long
        get() = if (fadeDurationOverrideMs > 0L) {
            fadeDurationOverrideMs
        } else {
            ProjectManager.getProject().overlayFadeDurationMs
        }

    private var fadeDelayUntil = 0L
    private var fadeStartTime = 0L
    private var fadeAlpha = 0f

    @Volatile
    private var forceHidden: Boolean = false

    // ---- Crossfade de loop (intercambio de roles) ----
    @Volatile
    private var loopPhase: Int = LOOP_IDLE
    /** 0 = solo saliente (activo), 1 = solo entrante (standby). */
    @Volatile
    private var crossT: Float = 0f
    private var loopPhaseStartElapsed: Long = 0L
    private var loopTransMs: Long = LOOP_TRANS_DEFAULT_MS
    private var pendingLoopStartMs: Int = 0
    @Volatile
    private var crossReady: Boolean = false

    private var currentVolume: Float = 1f
    private var completionListener: (() -> Unit)? = null

    private var screenWidth = 1
    private var screenHeight = 1

    fun setSize(width: Int, height: Int) {
        this.screenWidth = if (width > 0) width else 1
        this.screenHeight = if (height > 0) height else 1
        quadRenderer.setSize(screenWidth, screenHeight)
    }

    fun initialize() {
        textureA.initialize()
        textureB.initialize()
        quadRenderer.initialize()
        updateTransform()

        bindSurfaceProvider(playerA, textureA)
        bindSurfaceProvider(playerB, textureB)

        playerA.initialize(textureA.getSurface())
        playerB.initialize(textureB.getSurface())

        playerB.setVolume(0f)
        // Completion solo del activo: el standby no debe disparar cues
        playerA.setOnCompletionListener {
            if (activeIndex == 0) completionListener?.invoke()
        }
        playerB.setOnCompletionListener {
            if (activeIndex == 1) completionListener?.invoke()
        }

        activeIndex = 0
        forceHidden = false
        fadeDurationOverrideMs = -1L
        fadeStartTime = -1L
        fadeAlpha = 0f
        resetLoopBlend()
    }

    private fun bindSurfaceProvider(player: OverlayVideoPlayer, texture: ExternalTexture) {
        player.surfaceProvider = {
            val current = try {
                texture.getSurface()
            } catch (_: Exception) {
                null
            }
            if (current != null && current.isValid) current
            else texture.recreateSurface()
        }
    }

    private fun updateFade() {
        val now = SystemClock.elapsedRealtime()
        if (fadeDelayUntil > 0L) {
            fadeAlpha = 0f
            if (now >= fadeDelayUntil) {
                fadeDelayUntil = 0L
                fadeStartTime = now
            }
            return
        }
        if (fadeStartTime < 0L) {
            fadeAlpha = 0f
            return
        }
        if (fadeStartTime == 0L) {
            fadeAlpha = 1f
            return
        }

        val elapsed = now - fadeStartTime
        val dur = fadeDurationMs.toFloat().coerceAtLeast(1f)
        fadeAlpha = (elapsed.toFloat() / dur).coerceIn(0f, 1f)

        if (fadeAlpha >= 1f) {
            fadeStartTime = 0L
            fadeDurationOverrideMs = -1L
        }
    }

    fun startSoftStart(durationMs: Long = -1L) {
        forceHidden = false
        fadeDurationOverrideMs =
            if (durationMs > 0L) durationMs else -1L
        fadeAlpha = 0f
        val delay = try {
            ProjectManager.getProject().overlayDelayStartMs.coerceAtLeast(0L)
        } catch (_: Exception) {
            0L
        }
        val now = SystemClock.elapsedRealtime()
        if (delay > 0L) {
            fadeDelayUntil = now + delay
            fadeStartTime = -1L
        } else {
            fadeDelayUntil = 0L
            fadeStartTime = now
        }
    }

    fun update() {
        try {
            activeTexture.update()
        } catch (_: Exception) {
        }
        if (loopPhase == LOOP_PREPARING || loopPhase == LOOP_CROSSFADING) {
            try {
                standbyTexture.update()
            } catch (_: Exception) {
            }
        }
    }

    fun draw() {
        if (forceHidden) return

        updateTransform()
        updateFade()

        val overlay = ProjectManager.getProject().overlay
        val base = (overlay.opacity * fadeAlpha).coerceIn(0f, 1f)

        if (loopPhase == LOOP_CROSSFADING) {
            val t = crossT.coerceIn(0f, 1f)
            val outA = base * (1f - t)
            if (outA > 0.01f) {
                quadRenderer.draw(
                    activeTexture.getTextureId(),
                    activeTexture.getTextureMatrix(),
                    outA,
                    overlay.chromaEnabled,
                    overlay.chromaColor,
                    overlay.threshold,
                    overlay.softness
                )
            }
            val inA = base * t
            if (inA > 0.01f) {
                quadRenderer.draw(
                    standbyTexture.getTextureId(),
                    standbyTexture.getTextureMatrix(),
                    inA,
                    overlay.chromaEnabled,
                    overlay.chromaColor,
                    overlay.threshold,
                    overlay.softness
                )
            }
        } else {
            quadRenderer.draw(
                activeTexture.getTextureId(),
                activeTexture.getTextureMatrix(),
                base,
                overlay.chromaEnabled,
                overlay.chromaColor,
                overlay.threshold,
                overlay.softness
            )
        }
    }

    fun setForceHidden(hidden: Boolean) {
        forceHidden = hidden
        if (hidden) {
            fadeAlpha = 0f
            fadeStartTime = -1L
            resetLoopBlend()
        }
    }

    fun resetLoopBlend() {
        if (loopPhase != LOOP_IDLE) {
            try {
                standbyPlayer.pause()
            } catch (_: Exception) {
            }
            try {
                standbyPlayer.setVolume(0f)
            } catch (_: Exception) {
            }
        }
        loopPhase = LOOP_IDLE
        crossT = 0f
        loopPhaseStartElapsed = 0L
        pendingLoopStartMs = 0
        crossReady = false
        try {
            activePlayer.setVolume(currentVolume)
        } catch (_: Exception) {
        }
    }

    fun beginLoopFadeIn() {
        // Con intercambio de roles no hace falta fade-in post-seek
        resetLoopBlend()
    }

    /**
     * Crossfade dual-decoder con **intercambio de roles**.
     *
     * El standby se prepara en [loopStartMs], se funde con el activo cerca
     * del fin del tramo y al terminar el standby pasa a ser el activo
     * (sigue reproduciendo sin seek). El saliente queda en espera.
     *
     * @return true si el seek duro externo NO debe aplicarse
     */
    fun tickSmoothLoop(
        enabled: Boolean,
        positionMs: Int,
        loopEndMs: Int,
        loopStartMs: Int = 0,
        crossfadeMs: Long = LOOP_TRANS_DEFAULT_MS,
        onSeekToStart: (() -> Unit)? = null
    ): Boolean {
        if (!enabled || loopEndMs <= 0) {
            if (loopPhase != LOOP_IDLE) resetLoopBlend()
            return false
        }

        val segment = (loopEndMs - loopStartMs).coerceAtLeast(1).toLong()
        // Acotado a la duración del tramo del loop (mín. 50 ms)
        val trans = crossfadeMs.coerceIn(50L, segment)
        val now = SystemClock.elapsedRealtime()
        val end = loopEndMs
        val zoneStart = (end - trans.toInt()).coerceAtLeast(loopStartMs)

        when (loopPhase) {
            LOOP_IDLE -> {
                if (positionMs >= zoneStart) {
                    pendingLoopStartMs = loopStartMs
                    crossReady = false
                    loopPhase = LOOP_PREPARING
                    loopPhaseStartElapsed = now
                    crossT = 0f
                    try {
                        standbyPlayer.setVolume(0f)
                        standbyPlayer.restoreAt(
                            positionMs = loopStartMs,
                            paused = false,
                            onReady = {
                                crossReady = true
                                try {
                                    standbyPlayer.play()
                                } catch (_: Exception) {
                                }
                            }
                        )
                    } catch (_: Exception) {
                        resetLoopBlend()
                        onSeekToStart?.invoke()
                        try {
                            activePlayer.seekTo(loopStartMs)
                            activePlayer.play()
                        } catch (_: Exception) {
                        }
                        return true
                    }
                    return true
                }
                return false
            }
            LOOP_PREPARING -> {
                if (crossReady || (now - loopPhaseStartElapsed) > 900L) {
                    crossReady = true
                    try {
                        standbyPlayer.play()
                    } catch (_: Exception) {
                    }
                    loopPhase = LOOP_CROSSFADING
                    loopPhaseStartElapsed = now
                    crossT = 0f
                }
                if (positionMs >= end && !crossReady) {
                    // Standby no listo a tiempo → seek duro en el activo
                    resetLoopBlend()
                    onSeekToStart?.invoke()
                    try {
                        activePlayer.seekTo(loopStartMs)
                        activePlayer.play()
                    } catch (_: Exception) {
                    }
                    return true
                }
                return true
            }
            LOOP_CROSSFADING -> {
                val elapsed = now - loopPhaseStartElapsed
                crossT = (elapsed.toFloat() / trans.toFloat()).coerceIn(0f, 1f)
                if (crossT >= 0.999f) {
                    // Intercambio de roles: el entrante sigue sin seek
                    try {
                        activePlayer.pause()
                    } catch (_: Exception) {
                    }
                    try {
                        activePlayer.setVolume(0f)
                    } catch (_: Exception) {
                    }

                    activeIndex = 1 - activeIndex

                    try {
                        activePlayer.setVolume(currentVolume)
                        activePlayer.play()
                    } catch (_: Exception) {
                    }

                    onSeekToStart?.invoke()

                    loopPhase = LOOP_IDLE
                    crossT = 0f
                    crossReady = false
                    pendingLoopStartMs = 0
                }
                return true
            }
            else -> {
                resetLoopBlend()
                return false
            }
        }
    }

    fun isForceHidden(): Boolean = forceHidden

    fun isLoopCrossfading(): Boolean = loopPhase != LOOP_IDLE

    fun isFadeComplete(): Boolean {
        if (forceHidden) return true
        if (fadeDelayUntil > 0L) return false
        if (fadeStartTime < 0L) return false
        if (fadeStartTime == 0L) return true
        return fadeAlpha >= 0.999f
    }

    fun startOverlayFadeIn(durationMs: Long = -1L) {
        startSoftStart(durationMs)
    }

    fun clearFadeOverride() {
        fadeDurationOverrideMs = -1L
    }

    fun getSurface(): Surface {
        return activeTexture.getSurface()
    }

    private fun updateTransform() {
        val overlay = ProjectManager.getProject().overlay
        val zoom = overlay.scale.toFloat()
        var targetWidth = zoom
        var targetHeight = zoom
        val safeWidth = if (screenWidth > 0) screenWidth else 1080
        val safeHeight = if (screenHeight > 0) screenHeight else 2400
        val screenRatio = safeWidth.toFloat() / safeHeight.toFloat()

        when (overlay.aspectMode) {
            OverlayAspectMode.SCREEN -> {
                targetWidth *= screenRatio
            }
            OverlayAspectMode.ORIGINAL -> {
                val videoWidth = activePlayer.getVideoWidth()
                val videoHeight = activePlayer.getVideoHeight()
                if (videoWidth > 0 && videoHeight > 0) {
                    val videoRatio = videoWidth.toFloat() / videoHeight.toFloat()
                    targetWidth *= videoRatio
                } else {
                    targetWidth *= screenRatio
                }
            }
        }

        quadRenderer.setRect(
            (overlay.x / 100f) * screenRatio,
            overlay.y / 100f,
            targetWidth,
            targetHeight,
            overlay.rotation
        )
    }

    fun isPlaying() = activePlayer.isPlaying()
    fun play() = activePlayer.play()
    fun pause() = activePlayer.pause()
    fun getDuration() = activePlayer.getDuration()
    fun getCurrentPosition() = activePlayer.getCurrentPosition()

    fun seekTo(position: Int) {
        // Seek externo (cues, lock, etc.): cancela crossfade y usa el activo
        resetLoopBlend()
        activePlayer.seekTo(position)
    }

    fun isIntentionallyPaused(): Boolean =
        activePlayer.intentionallyPaused

    fun restoreAt(
        positionMs: Int,
        paused: Boolean,
        onReady: (() -> Unit)? = null
    ) {
        resetLoopBlend()
        activePlayer.restoreAt(positionMs, paused, onReady)
    }

    fun ensurePlaying(preferredSeekMs: Int = -1) =
        activePlayer.ensurePlaying(preferredSeekMs)

    fun recoverPlayback(seekMs: Int = 0) {
        resetLoopBlend()
        activePlayer.recoverPlayback(seekMs)
    }

    fun setDirection(
        direction: OverlayPlaybackDirection,
        reverseFileName: String? = null,
        startPositionMs: Int = 0,
        reverseKind: ReverseClipKind = ReverseClipKind.NONE,
        onReady: (() -> Unit)? = null
    ) {
        resetLoopBlend()
        activePlayer.setDirection(
            direction,
            reverseFileName,
            startPositionMs,
            reverseKind,
            onReady
        )
    }

    fun getDirection(): OverlayPlaybackDirection =
        activePlayer.direction

    fun isPlayingReverseClip(): Boolean =
        activePlayer.isPlayingReverseClip

    fun getReverseClipKind(): ReverseClipKind =
        activePlayer.reverseClipKind

    fun setOnCompletionListener(listener: () -> Unit) {
        completionListener = listener
    }

    fun setLooping(looping: Boolean) {
        playerA.setLooping(looping)
        playerB.setLooping(looping)
    }

    fun setVolume(volume: Float) {
        currentVolume = volume
        try {
            activePlayer.setVolume(volume)
        } catch (_: Exception) {
        }
        try {
            standbyPlayer.setVolume(0f)
        } catch (_: Exception) {
        }
    }

    fun stop() {
        resetLoopBlend()
        activePlayer.pause()
        activePlayer.seekTo(0)
    }

    fun release() {
        try {
            playerA.release()
        } catch (_: Exception) {
        }
        try {
            playerB.release()
        } catch (_: Exception) {
        }
        try {
            textureA.release()
        } catch (_: Exception) {
        }
        try {
            textureB.release()
        } catch (_: Exception) {
        }
        try {
            quadRenderer.release()
        } catch (_: Exception) {
        }
    }

    companion object {
        private const val LOOP_IDLE = 0
        private const val LOOP_PREPARING = 1
        private const val LOOP_CROSSFADING = 2
        /** Duración del crossfade entre fin e inicio del loop (ms). */
        private const val LOOP_TRANS_DEFAULT_MS = 500L
    }
}
