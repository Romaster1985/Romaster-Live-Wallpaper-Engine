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

    private val externalTexture = ExternalTexture()
    private val quadRenderer = GLExternalQuadRenderer()
    private val overlayPlayer = OverlayVideoPlayer(context)
    
    private val fadeDurationMs: Long
        get() = ProjectManager.getProject().overlayFadeDurationMs

    private var fadeStartTime = 0L
    private var fadeAlpha = 0f

    // AGREGADO: Almacenar las dimensiones reales del viewport de la pantalla
    private var screenWidth = 1
    private var screenHeight = 1

    /**
     * AGREGADO: Recibe las dimensiones físicas de la pantalla desde GLRenderer 
     * e inicializa el pipeline de proyección del Quad.
     */
    fun setSize(width: Int, height: Int) {
        this.screenWidth = if (width > 0) width else 1
        this.screenHeight = if (height > 0) height else 1
        
        // Notificar al quad renderer para que arme su matriz ortográfica
        quadRenderer.setSize(screenWidth, screenHeight)
    }

    fun initialize() {
        externalTexture.initialize()
        quadRenderer.initialize()
        updateTransform()
    
        overlayPlayer.initialize(externalTexture.getSurface())
        overlayPlayer.play()
    
        fadeStartTime = SystemClock.elapsedRealtime()
        fadeAlpha = 0f
    }
    
    private fun updateFade() {
        if (fadeStartTime <= 0L) {
            fadeAlpha = 1f
            return
        }
    
        val elapsed = SystemClock.elapsedRealtime() - fadeStartTime
        fadeAlpha = (elapsed.toFloat() / fadeDurationMs.toFloat()).coerceIn(0f, 1f)
    
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

    fun getSurface(): Surface {
        return externalTexture.getSurface()
    }
    
    private fun updateTransform() {
        val overlay = ProjectManager.getProject().overlay
    
        // CORREGIDO: Usamos el valor real del slider (ej. 200f o 500f) sin achicarlo.
        // Esto le devuelve toda la fuerza geométrica al zoom del video overlay.
        val zoom = overlay.scale.toFloat()
    
        var targetWidth = zoom
        var targetHeight = zoom
    
        // Mantenemos el blindaje contra ceros para evitar colapsos de pantalla
        val safeWidth = if (screenWidth > 0) screenWidth else 1080
        val safeHeight = if (screenHeight > 0) screenHeight else 2400
        val screenRatio = safeWidth.toFloat() / safeHeight.toFloat()

        when (overlay.aspectMode) {
            OverlayAspectMode.SCREEN -> {
                // Adaptarse exactamente a la proporción de la pantalla.
                targetWidth *= screenRatio
            }
    
            OverlayAspectMode.ORIGINAL -> {
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
    
        // Enviamos las coordenadas reales al Quad Renderer.
        // Dividimos la posición X e Y por 100f solo para que el desplazamiento en pantalla
        // mantenga la sensibilidad original de tus sliders de posición.
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