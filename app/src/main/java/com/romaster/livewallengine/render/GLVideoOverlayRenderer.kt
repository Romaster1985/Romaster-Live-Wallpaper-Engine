package com.romaster.livewallengine.render

import android.content.Context
import android.view.Surface
import android.os.SystemClock
import com.romaster.livewallengine.video.OverlayVideoPlayer
import com.romaster.livewallengine.model.OverlaySettings
import com.romaster.livewallengine.model.OverlayAspectMode
import com.romaster.livewallengine.project.ProjectManager

class GLVideoOverlayRenderer(

    private val context: Context

) {

    private val externalTexture =
        ExternalTexture()

    private val quadRenderer =
        GLExternalQuadRenderer()
    
    private val overlayPlayer =
    OverlayVideoPlayer(context)
    
    private val fadeDurationMs = 3000L

    private var fadeStartTime = 0L
    
    private var fadeAlpha = 0f

    fun initialize() {

        externalTexture.initialize()
    
        quadRenderer.initialize()
    
        updateTransform()
    
        overlayPlayer.initialize(
            externalTexture.getSurface()
        )
    
        overlayPlayer.play()
    
        fadeStartTime =
            SystemClock.elapsedRealtime()
    
        fadeAlpha = 0f
    }
    
    private fun updateFade() {

        if (fadeStartTime <= 0L) {
    
            fadeAlpha = 1f
    
            return
        }
    
        val elapsed =
            SystemClock.elapsedRealtime() -
            fadeStartTime
    
        fadeAlpha =
            (
                elapsed.toFloat() /
                fadeDurationMs.toFloat()
            ).coerceIn(
                0f,
                1f
            )
    
        if (fadeAlpha >= 1f) {
    
            fadeStartTime = 0L
        }
    }

    fun update() {

        externalTexture.update()
    }

    fun draw() {

        updateTransform()
    
        updateFade()
    
        val overlay =
            ProjectManager
                .getProject()
                .overlay
    
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

    fun getSurface(): Surface {

        return externalTexture.getSurface()
    }
    
    private fun updateTransform() {

        val overlay =
            ProjectManager
                .getProject()
                .overlay
    
    
        val zoom =
            overlay.scale
    
    
        var width = zoom
        var height = zoom
    
    
        when (overlay.aspectMode) {
    
    
            OverlayAspectMode.SCREEN -> {
    
                // Mantiene relación de pantalla
                // No modifica nada
    
            }
    
    
            OverlayAspectMode.ORIGINAL -> {
    
    
                val videoWidth =
                    overlayPlayer.getVideoWidth()
    
                val videoHeight =
                    overlayPlayer.getVideoHeight()
    
    
                if (
                    videoWidth > 0 &&
                    videoHeight > 0
                ) {
    
    
                    val videoRatio =
                        videoWidth.toFloat() /
                        videoHeight.toFloat()
    
    
                    val screenRatio =
                        context.resources
                            .displayMetrics
                            .widthPixels
                            .toFloat() /
                        context.resources
                            .displayMetrics
                            .heightPixels
                            .toFloat()
    
    
    
                    if (videoRatio > screenRatio) {
    
    
                        height *=
                            screenRatio / videoRatio
    
    
                    } else {
    
    
                        width *=
                            videoRatio / screenRatio
    
                    }
    
                }
    
            }
    
        }
    
    
        quadRenderer.setRect(
    
            overlay.x / 100f,
    
            overlay.y / 100f,
    
            width,
    
            height
    
        )
    
    }
    
    fun isPlaying() =
    overlayPlayer.isPlaying()

    fun play() =
        overlayPlayer.play()
    
    fun pause() =
        overlayPlayer.pause()
    
    fun getDuration() =
        overlayPlayer.getDuration()
    
    fun getCurrentPosition() =
        overlayPlayer.getCurrentPosition()
    
    fun seekTo(position: Int) =
        overlayPlayer.seekTo(position)
    
    fun setOnCompletionListener(
        listener: () -> Unit
    ) =
        overlayPlayer.setOnCompletionListener(
            listener
        )
    
    fun setLooping(
        looping: Boolean
    ) =
        overlayPlayer.setLooping(looping)
    
    fun setVolume(volume: Float) {
        overlayPlayer.setVolume(volume)
    }
    
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