package com.romaster.livewallengine.wallpaper

import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import android.app.KeyguardManager
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

class GLWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine {
    
        FileLogger.clear(this)
    
        FileLogger.writeDeviceInfo(this)
    
        FileLogger.log(
            this,  
            "GLWallpaperService.onCreateEngine()"
        )
    
        return GLEngine()
    }
    
    inner class GLEngine : Engine() {
    
        private var holder: SurfaceHolder? = null
    
        private var renderer: GLRenderer? = null
    
        private var videoPlayer: VideoPlayer? = null
        
        private var bgSoundPlayer: WallpaperSoundPlayer? = null

        private var overlaySoundPlayer: WallpaperSoundPlayer? = null
    
        private var renderThread: Thread? = null
    
        @Volatile
        private var running = false
    
        @Volatile
        private var visible = false
    
        // ============================================  
        // POSICIÓN GUARDADA DEL VIDEO  
        // ============================================  
        private var savedPosition: Int = 0
        
        private var savedOverlayPosition: Int = 0
        
        private var playback =
            PlaybackSettings()
        
        private var lastRevision = -1
        
        private var lastLockState = false
        
        private var deviceLocked = false
        
        private fun updateCueState() {

            val keyguard =
                getSystemService(KEYGUARD_SERVICE)
                    as KeyguardManager
        
            val locked =
                keyguard.isKeyguardLocked
        
            if (locked != lastLockState) {
        
                lastLockState = locked
        
                if (locked) {
                    
                    deviceLocked = true
                    
                    renderer
                        ?.getVideoOverlayRenderer()
                        ?.seekTo(0)
        
                    FileLogger.log(
                        this@GLWallpaperService,
                        "LOCKED"
                    )
        
                } else {
                    
                    deviceLocked = false
        
                    FileLogger.log(
                        this@GLWallpaperService,
                        "UNLOCKED"
                    )
        
                }
        
            }
        
        }
    
        override fun onSurfaceCreated(
            holder: SurfaceHolder
        ) {
    
            super.onSurfaceCreated(holder)
    
            this.holder = holder
    
            FileLogger.log(
                this@GLWallpaperService,
                "onSurfaceCreated()"
            )
    
            if (visible) {
                startRendering()
            }
        }
    
        override fun onSurfaceChanged(
            holder: SurfaceHolder,
            format: Int,
            width: Int,
            height: Int
        ) {
            super.onSurfaceChanged(holder, format, width, height)
            FileLogger.log(
                this@GLWallpaperService,
                "onSurfaceChanged: ${width}x${height}"
            )
        }
    
        override fun onVisibilityChanged(
            visible: Boolean
        ) {
    
            super.onVisibilityChanged(visible)
    
            this.visible = visible
    
            FileLogger.log(
                this@GLWallpaperService,
                "onVisibilityChanged($visible)"
            )
    
            if (visible) {
                startRendering()
            } else {
                stopRendering()
            }
        }
    
        override fun onSurfaceDestroyed(
            holder: SurfaceHolder
        ) {
    
            FileLogger.log(
                this@GLWallpaperService,
                "onSurfaceDestroyed()"
            )
            
            stopRendering()
            
            this.holder = null
            
            super.onSurfaceDestroyed(holder)
        }
        
        override fun onDestroy() {
            
            FileLogger.log(
                this@GLWallpaperService,
                "onDestroy()"
            )
            
            stopRendering()
            
            super.onDestroy()
        }
        
        private fun startRendering() {
    
            if (renderThread?.isAlive == true) {
                return
            }
    
            running = true
    
            renderThread = Thread {
    
                FileLogger.log(
                    this@GLWallpaperService,
                    "RenderThread iniciado"
                )
    
                try {
    
                    val surfaceHolder = holder
                        ?: return@Thread
    
                    // ============================================  
                    // CREAR RENDERER (Incluye Video OL)
                    // ============================================  
                    renderer = GLRenderer(this@GLWallpaperService, surfaceHolder)
                    renderer!!.initialize()
                    
                    playback =
                        ProjectManager
                            .getProject()
                            .playback
                            .copy()
                    
                    // -------------------------------------
                    // Primer video (Video de fondo)
                    // -------------------------------------
                    videoPlayer = VideoPlayer(this@GLWallpaperService)
                    videoPlayer!!.initialize(renderer!!.getVideoSurface())
                    
                    videoPlayer!!.setOnVideoSizeChangedListener {

                        width,
                        height ->
                    
                        renderer?.setVideoSize(
                            width,
                            height
                        )
                    
                    }
                    
                    bgSoundPlayer =
                        WallpaperSoundPlayer(
                            this@GLWallpaperService
                        )
                    
                    overlaySoundPlayer =
                        WallpaperSoundPlayer(
                            this@GLWallpaperService
                        )
    
                    // Restaurar posición del Video BG si hay una guardada  
                    if (savedPosition > 0) {
                        videoPlayer!!.seekTo(savedPosition)
                        FileLogger.log(
                            this@GLWallpaperService,
                            "Video restaurado en posición: ${savedPosition}ms"
                        )
                    }
    
                    videoPlayer!!.play()
                    
                    // Restaurar posición del Video OL si hay una guardada
                    renderer!!
                        .getVideoOverlayRenderer()
                        ?.let { overlay ->
                    
                            if (savedOverlayPosition > 0) {
                    
                                overlay.seekTo(savedOverlayPosition)
                    
                                FileLogger.log(
                                    this@GLWallpaperService,
                                    "Overlay restaurado en posición: ${savedOverlayPosition}ms"
                                )
                            }
                    
                            overlay.play()
                            
                        }
                    
                    initializeAudioConfiguration()
    
                    var frameCount = 0
    
                    while (running) {
                        
                        updateCueState()
                        
                        applyAudioConfiguration()

                        val revision =
                            ProjectManager.getRevision()
                        
                        if (revision != lastRevision) {
                        
                            lastRevision =
                                revision
                        
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
                        
                                val position = overlay.getCurrentPosition()
                                val duration = overlay.getDuration()
                        
                                if (
                                    playback.enabled &&
                                    duration > 0
                                ) {
                                
                                    if (deviceLocked) {
                                
                                        if (
                                            position >=
                                            playback.lockedCueMs
                                        ) {
                                
                                            FileLogger.log(
                                                this@GLWallpaperService,
                                                "CueLocked -> seekTo(0)"
                                            )
                                
                                            overlay.seekTo(0)
                                        }
                                
                                    } else {
                                
                                        if (
                                            position >=
                                            duration - 50
                                        ) {
                                
                                            FileLogger.log(
                                                this@GLWallpaperService,
                                                "CueUnlocked -> seekTo(" +
                                                    "${playback.unlockedCueMs}"
                                            )
                                
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
    
                        renderer!!.drawFrame()
    
                        frameCount++
                        if (frameCount % 120 == 0) {
                            // Log cada 120 frames (≈8 segundos) - solo para saber que está vivo  
                            FileLogger.log(
                                this@GLWallpaperService,
                                "Frames: $frameCount"
                            )
                        }
    
                        Thread.sleep(16)
                    }
    
                } catch (e: InterruptedException) {
    
                    FileLogger.log(
                        this@GLWallpaperService,
                        "RenderThread detenido"
                    )
    
                } catch (e: Exception) {
    
                    FileLogger.logException(
                        this@GLWallpaperService,
                        "RenderThread",
                        e
                    )
    
                } finally {
    
                    // ============================================  
                    // GUARDAR POSICIÓN ANTES DE DESTRUIR  
                    // ============================================  
                    videoPlayer?.let {
                        savedPosition = it.getCurrentPosition()
                        FileLogger.log(
                            this@GLWallpaperService,
                            "Video guardado en posición: ${savedPosition}ms"
                        )
                    }
                    
                    renderer
                        ?.getVideoOverlayRenderer()
                        ?.let { overlay ->
                    
                            savedOverlayPosition =
                                overlay.getCurrentPosition()
                    
                            FileLogger.log(
                                this@GLWallpaperService,
                                "Overlay guardado en posición: ${savedOverlayPosition}ms"
                            )
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
                        this@GLWallpaperService,
                        "RenderThread finalizado"
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
    
            FileLogger.log(
                this@GLWallpaperService,
                "RenderThread detenido"
            )
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
                            this@GLWallpaperService,
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
                        this@GLWallpaperService,
                        overlay.soundPath!!
                    ),
                    overlay.soundVolume,
                    false
                )
            }
        }
    }
}