package com.romaster.livewallengine.ui

import android.content.Context
import android.util.AttributeSet
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.romaster.livewallengine.debug.FileLogger
import com.romaster.livewallengine.render.GLRenderer
import com.romaster.livewallengine.render.GLVideoOverlayRenderer
import com.romaster.livewallengine.video.VideoPlayer
import com.romaster.livewallengine.video.OverlayVideoPlayer
import com.romaster.livewallengine.model.PlaybackSettings
import com.romaster.livewallengine.project.ProjectManager
import com.romaster.livewallengine.audio.WallpaperSoundPlayer
import com.romaster.livewallengine.audio.AudioStorage
import com.romaster.livewallengine.audio.AudioPicker
import android.graphics.Bitmap
import android.opengl.GLES20
import java.nio.ByteBuffer
import java.nio.ByteOrder

class WallpaperPreviewView @JvmOverloads constructor(

    context: Context,

    attrs: AttributeSet? = null

) : SurfaceView(
    context,
    attrs
), SurfaceHolder.Callback {

    private var holderRef: SurfaceHolder? = null

    private var renderer: GLRenderer? = null

    private var videoPlayer: VideoPlayer? = null
    
    private var bgSoundPlayer: WallpaperSoundPlayer? = null

    private var overlaySoundPlayer: WallpaperSoundPlayer? = null

    private var renderThread: Thread? = null
    
    private var pendingCapture: ((Bitmap) -> Unit)? = null

    @Volatile
    private var running = false
    
    @Volatile
    private var visible = false
    
    private var savedPosition: Int = 0
        
    private var savedOverlayPosition: Int = 0
    
    private var playback =
            PlaybackSettings()
    
    private var lastRevision = -1
        
    private var deviceLocked = false

    init {

        holder.addCallback(this)
    }
    
    fun setSimulatedLockState(
        locked: Boolean
    ) {
    
        if (
            deviceLocked ==
            locked
        ) {
            return
        }
    
        deviceLocked =
            locked
    
        if (locked) {
    
            renderer
                ?.getVideoOverlayRenderer()
                ?.seekTo(0)
    
            FileLogger.log(
                context,
                "Preview -> LOCKED"
            )
    
        } else {
    
            FileLogger.log(
                context,
                "Preview -> UNLOCKED"
            )
    
        }
    
    }

    override fun surfaceCreated(
        holder: SurfaceHolder
    ) {

        FileLogger.log(
            context,
            "WallpaperPreview.surfaceCreated()"
        )

        holderRef = holder

        startRendering()
    }

    override fun surfaceChanged(

        holder: SurfaceHolder,

        format: Int,

        width: Int,

        height: Int

    ) {

        FileLogger.log(
            context,
            "WallpaperPreview.surfaceChanged(${width}x${height})"
        )

        renderer?.onSurfaceChanged(
            width,
            height
        )
    }

    override fun surfaceDestroyed(
        holder: SurfaceHolder
    ) {

        FileLogger.log(
            context,
            "WallpaperPreview.surfaceDestroyed()"
        )

        stopRendering()

        holderRef = null
    }

    private fun startRendering() {

        if (renderThread?.isAlive == true) {
            return
        }

        running = true

        renderThread = Thread {

            FileLogger.log(
                context,
                "WallpaperPreview.RenderThread iniciado"
            )

            try {

                val holder =
                    holderRef ?: return@Thread

                renderer =
                    GLRenderer(
                        context,
                        holder
                    )

                renderer!!.initialize()
                
                playback =
                    ProjectManager
                        .getProject()
                        .playback
                        .copy()
                
                val metrics =
                    resources.displayMetrics
                
                renderer!!.setVirtualScreenSize(
                    metrics.widthPixels,
                    metrics.heightPixels
                )

                renderer!!.onSurfaceChanged(
                    holder.surfaceFrame.width(),
                    holder.surfaceFrame.height()
                )

                videoPlayer =
                    VideoPlayer(
                        context
                    )

                videoPlayer!!.initialize(
                    renderer!!.getVideoSurface()
                )
                
                videoPlayer!!.setOnVideoSizeChangedListener {

                    width,
                    height ->
                
                    renderer?.setVideoSize(
                        width,
                        height
                    )
                
                }
                
                bgSoundPlayer =
                    WallpaperSoundPlayer(context)
                
                overlaySoundPlayer =
                    WallpaperSoundPlayer(context)
                
                // Restaurar posición del Video BG si hay una guardada  
                if (savedPosition > 0) {
                    videoPlayer!!.seekTo(savedPosition)
                }

                videoPlayer!!.play()
                
                // Restaurar posición del Video OL si hay una guardada
                renderer!!
                    .getVideoOverlayRenderer()
                    ?.let { overlay ->
                
                        if (savedOverlayPosition > 0) {
                
                            overlay.seekTo(savedOverlayPosition)
                            
                        }
                
                        overlay.play()
                        
                    }
                
                initializeAudioConfiguration()
    
                var frameCount = 0

                while (running) {
                    
                    //setSimulatedLockState()

                    applyAudioConfiguration()
                
                    val revision =
                        ProjectManager.getRevision()
                
                    if (revision != lastRevision) {
                
                        lastRevision = revision
                        
                        playback =
                            ProjectManager
                                .getProject()
                                .playback
                                .copy()
                
                        initializeAudioConfiguration()
                
                    }
                    
                    renderer
                        ?.getVideoOverlayRenderer()
                        ?.let { overlay ->
                    
                            val position =
                                overlay.getCurrentPosition()
                            
                            val duration =
                                overlay.getDuration()
                            
                            if (
                                duration > 0
                            ) {
                            
                                val project =
                                    ProjectManager.getProject()
                            
                                val playbackSettings =
                                    project.playback
                            
                                val durationMs =
                                    duration.toLong()
                            
                                if (
                                    playbackSettings.overlayDurationMs !=
                                    durationMs
                                ) {
                            
                                    playbackSettings.overlayDurationMs =
                                        durationMs
                            
                                    ProjectManager.saveProject(
                                        project
                                    )
                            
                                    FileLogger.log(
                                        context,
                                        "Overlay duration saved: ${durationMs}ms"
                                    )
                                }
                            
                                if (
                                    playback.enabled
                                ) {
                            
                                    if (
                                        deviceLocked
                                    ) {
                            
                                        if (
                                            position >=
                                            playback.lockedCueMs
                                        ) {
                            
                                            overlay.seekTo(0)
                                        }
                            
                                    } else {
                            
                                        if (
                                            position >=
                                            duration -
                                            50
                                        ) {
                            
                                            overlay.seekTo(
                                                playback.unlockedCueMs
                                                    .coerceIn(
                                                        0L,
                                                        Int.MAX_VALUE.toLong()
                                                    )
                                                    .toInt()
                                            )
                                        }
                                    }
                                }
                            }
                    
                        }
                
                    renderer!!.drawFrame()
                
                    pendingCapture?.let {
                
                        GLES20.glFinish()
                
                        val bmp =
                            renderer!!.captureBitmap()
                
                        post {
                
                            it(bmp)
                
                        }
                
                        pendingCapture = null
                
                    }
                
                    Thread.sleep(16)
                
                }

            } catch (e: InterruptedException) {

                FileLogger.log(
                    context,
                    "WallpaperPreview.RenderThread detenido"
                )

            } catch (e: Exception) {

                FileLogger.logException(
                    context,
                    "WallpaperPreview.RenderThread",
                    e
                )

            } finally {
                
                // ============================================  
                // GUARDAR POSICIÓN ANTES DE DESTRUIR  
                // ============================================  
                videoPlayer?.let {
                    savedPosition = it.getCurrentPosition()
                }
                
                renderer
                    ?.getVideoOverlayRenderer()
                    ?.let { overlay ->
                
                        savedOverlayPosition =
                            overlay.getCurrentPosition()
                    }

                videoPlayer?.release()
                videoPlayer = null
                
                bgSoundPlayer?.release()
                bgSoundPlayer = null
                
                overlaySoundPlayer?.release()
                overlaySoundPlayer = null

                renderer?.release()
                renderer = null

                FileLogger.log(
                    context,
                    "WallpaperPreview.RenderThread finalizado"
                )
            }
        }

        renderThread!!.start()
    }

    private fun stopRendering() {

        running = false

        renderThread?.interrupt()

        try {

            renderThread?.join(1000)

        } catch (_: Exception) {
        }

        renderThread = null
    }
    
    private fun applyAudioConfiguration() {

        val project =
            ProjectManager.getProject()
    
        val bgLayer =
            project.layers.firstOrNull()
    
        if (bgLayer != null) {
    
            if (bgLayer.soundPath.isNullOrEmpty()) {
    
                videoPlayer?.setVolume(
                    bgLayer.soundVolume
                )
    
            } else {
    
                bgSoundPlayer?.setVolume(
                    bgLayer.soundVolume
                )
    
            }
        }
    
        val overlay =
            project.overlay
    
        if (overlay.soundPath.isNullOrEmpty()) {
    
            renderer
                ?.getVideoOverlayRenderer()
                ?.setVolume(
                    overlay.soundVolume
                )
    
        } else {
    
            overlaySoundPlayer?.setVolume(
                overlay.soundVolume
            )
    
        }
    
    }
    
    private fun initializeAudioConfiguration() {

        val project =
            ProjectManager.getProject()
    
        // ============================================
        // VIDEO BG
        // ============================================
    
        val bgLayer =
            project.layers.firstOrNull()
    
        if (bgLayer != null) {
    
            if (bgLayer.soundPath.isNullOrEmpty()) {
    
                videoPlayer?.setVolume(
                    bgLayer.soundVolume
                )
    
                bgSoundPlayer?.stop()
    
            } else {
    
                videoPlayer?.setVolume(0f)
    
                bgSoundPlayer?.play(
                    AudioStorage.getAudioFile(
                        context,
                        bgLayer.soundPath!!
                    ),
                    bgLayer.soundVolume,
                    false
                )
            }
        }
    
        // ============================================
        // OVERLAY
        // ============================================
    
        val overlay =
            project.overlay
    
        if (overlay.soundPath.isNullOrEmpty()) {
    
            renderer
                ?.getVideoOverlayRenderer()
                ?.setVolume(
                    overlay.soundVolume
                )
    
            overlaySoundPlayer?.stop()
    
        } else {
    
            renderer
                ?.getVideoOverlayRenderer()
                ?.setVolume(0f)
    
            overlaySoundPlayer?.play(
                AudioStorage.getAudioFile(
                    context,
                    overlay.soundPath!!
                ),
                overlay.soundVolume,
                false
            )
        }
    }
    
    fun refresh() {

        // Más adelante regeneraremos el bitmap del reloj aquí.
    }
    
    fun capturePreview(

        callback: (Bitmap) -> Unit
    
    ) {
    
        pendingCapture = callback
    
    }
    
    fun reloadPlayers() {
    
        stopRendering()
        startRendering()
        
    }
}