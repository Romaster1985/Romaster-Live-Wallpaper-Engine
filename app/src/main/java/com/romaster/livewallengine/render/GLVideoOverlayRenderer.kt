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
import android.view.Surface
import android.os.SystemClock
import com.romaster.livewallengine.video.OverlayVideoPlayer
import com.romaster.livewallengine.video.OverlayPlaybackDirection
import com.romaster.livewallengine.model.OverlaySettings
import com.romaster.livewallengine.model.OverlayAspectMode
import com.romaster.livewallengine.project.ProjectManager

class GLVideoOverlayRenderer(
    private val context: Context
) {

    private val externalTexture = ExternalTexture()
    private val quadRenderer = GLExternalQuadRenderer()
    private val overlayPlayer = OverlayVideoPlayer(context)
    
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

    /**
     * true = no dibujar el overlay (evita flash del frame viejo
     * al bloquear y hacer seek a 0).
     */
    @Volatile
    private var forceHidden: Boolean = false

    private var screenWidth = 1
    private var screenHeight = 1

    fun setSize(width: Int, height: Int) {
        this.screenWidth = if (width > 0) width else 1
        this.screenHeight = if (height > 0) height else 1
        quadRenderer.setSize(screenWidth, screenHeight)
    }

    fun initialize() {
        externalTexture.initialize()
        quadRenderer.initialize()
        updateTransform()

        // Cada vez que se abre un archivo (original o reverse),
        // pedimos un Surface nuevo desde el SurfaceTexture.
        // Reusar el Surface viejo tras release() del MediaPlayer
        // es la causa típica del crash al cambiar de clip.
        // Solo recrear Surface si la actual quedó inválida
        overlayPlayer.surfaceProvider = {
            val current = try {
                externalTexture.getSurface()
            } catch (_: Exception) {
                null
            }
            if (current != null && current.isValid) {
                current
            } else {
                externalTexture.recreateSurface()
            }
        }

        overlayPlayer.initialize(externalTexture.getSurface())
        // Soft Start NO arranca acá: el tiempo de seek/prepare
        // consumiría el fade y al final se veía un “salto”.
        // Se inicia con startSoftStart() cuando hay frame listo.
        forceHidden = false
        fadeDurationOverrideMs = -1L
        fadeStartTime = -1L // -1 = esperando (alpha 0)
        fadeAlpha = 0f
    }
    
    private fun updateFade() {
        val now = SystemClock.elapsedRealtime()
        // Delay Start pendiente
        if (fadeDelayUntil > 0L) {
            fadeAlpha = 0f
            if (now >= fadeDelayUntil) {
                fadeDelayUntil = 0L
                fadeStartTime = now
            }
            return
        }
        // Esperando primer frame / soft start
        if (fadeStartTime < 0L) {
            fadeAlpha = 0f
            return
        }
        // Fade terminado
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

    /**
     * Soft Start suave con la duración de la card (o [durationMs]).
     * Llamar cuando el decoder ya empujó frame (onReady de restore/setDirection).
     */
    fun startSoftStart(durationMs: Long = -1L) {
        forceHidden = false
        fadeDurationOverrideMs =
            if (durationMs > 0L) durationMs else -1L
        fadeAlpha = 0f
        val delay = try {
            ProjectManager.getProject().overlayDelayStartMs.coerceAtLeast(0L)
        } catch (_: Exception) { 0L }
        val now = SystemClock.elapsedRealtime()
        if (delay > 0L) {
            fadeDelayUntil = now + delay
            fadeStartTime = -1L // sigue esperando (alpha 0) hasta post-delay
        } else {
            fadeDelayUntil = 0L
            fadeStartTime = now
        }
    }

    fun update() {
        externalTexture.update()
    }

    fun draw() {
        if (forceHidden) {
            // No dibujar textura vieja (p. ej. durante seek a 0 al bloquear)
            return
        }

        updateTransform()
        updateFade()
    
        val overlay = ProjectManager.getProject().overlay
    
        quadRenderer.draw(
            externalTexture.getTextureId(),
            externalTexture.getTextureMatrix(),
            overlay.opacity * fadeAlpha,
            overlay.chromaEnabled,
            overlay.chromaColor,
            overlay.threshold,
            overlay.softness
        )
    }

    /** Oculta el overlay hasta [reveal] (anti-parpadeo al lock). */
    fun setForceHidden(hidden: Boolean) {
        forceHidden = hidden
        if (hidden) {
            fadeAlpha = 0f
            fadeStartTime = -1L // hold invisible until Soft Start
        }
    }

    fun isForceHidden(): Boolean = forceHidden

    /** true si no hay soft start en curso (oculto o fade terminado). */
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
        return externalTexture.getSurface()
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
                // getVideoWidth/Height usan caché durante el swap de ping-pong
                // para no caer al screenRatio (eso provocaba el “salto” de ancho).
                val videoWidth = overlayPlayer.getVideoWidth()
                val videoHeight = overlayPlayer.getVideoHeight()
    
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

    fun isPlaying() = overlayPlayer.isPlaying()
    fun play() = overlayPlayer.play()
    fun pause() = overlayPlayer.pause()
    fun getDuration() = overlayPlayer.getDuration()
    fun getCurrentPosition() = overlayPlayer.getCurrentPosition()
    fun seekTo(position: Int) = overlayPlayer.seekTo(position)

    fun isIntentionallyPaused(): Boolean =
        overlayPlayer.intentionallyPaused

    fun restoreAt(
        positionMs: Int,
        paused: Boolean,
        onReady: (() -> Unit)? = null
    ) = overlayPlayer.restoreAt(positionMs, paused, onReady)

    fun ensurePlaying(preferredSeekMs: Int = -1) =
        overlayPlayer.ensurePlaying(preferredSeekMs)

    fun recoverPlayback(seekMs: Int = 0) =
        overlayPlayer.recoverPlayback(seekMs)

    /**
     * Cambia entre video original y clip invertido preprocesado.
     * Firma alineada con OverlayVideoPlayer.setDirection(...).
     */
    fun setDirection(
        direction: OverlayPlaybackDirection,
        reverseFileName: String? = null,
        startPositionMs: Int = 0,
        reverseKind: com.romaster.livewallengine.video.ReverseClipKind =
            com.romaster.livewallengine.video.ReverseClipKind.NONE,
        onReady: (() -> Unit)? = null
    ) = overlayPlayer.setDirection(
        direction,
        reverseFileName,
        startPositionMs,
        reverseKind,
        onReady
    )

    fun getDirection(): OverlayPlaybackDirection =
        overlayPlayer.direction

    fun isPlayingReverseClip(): Boolean =
        overlayPlayer.isPlayingReverseClip

    fun getReverseClipKind(): com.romaster.livewallengine.video.ReverseClipKind =
        overlayPlayer.reverseClipKind
    
    fun setOnCompletionListener(listener: () -> Unit) =
        overlayPlayer.setOnCompletionListener(listener)
    
    fun setLooping(looping: Boolean) = overlayPlayer.setLooping(looping)
    fun setVolume(volume: Float) { overlayPlayer.setVolume(volume) }
    
    fun stop() {
        overlayPlayer.pause()
        overlayPlayer.seekTo(0)
    }

    fun release() {
        overlayPlayer.release()
        quadRenderer.release()
        externalTexture.release()
    }
}
