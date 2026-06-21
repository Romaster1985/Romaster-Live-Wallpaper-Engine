package com.romaster.livewallengine.wallpaper

import android.app.KeyguardManager
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder

import com.romaster.livewallengine.audio.AudioStorage
import com.romaster.livewallengine.audio.WallpaperSoundPlayer
import com.romaster.livewallengine.debug.FileLogger
import com.romaster.livewallengine.model.CueMode
import com.romaster.livewallengine.project.ProjectManager
import com.romaster.livewallengine.render.GLRenderer
import com.romaster.livewallengine.video.CueLoopController
import com.romaster.livewallengine.video.VideoPlayer

class GLWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine {

        FileLogger.startNewSession(this)

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
        // POSICIONES GUARDADAS
        // ============================================

        private var savedPosition: Int = 0

        private var savedOverlayPosition: Int = 0

        // ============================================
        // CUE CONTROLLER
        // ============================================

        private val overlayCueController =
            CueLoopController()

        private var lastRevision = -1
        
        private var lastOverlayLoopEnabled = false

        private var lastLockState = false

        private var deviceLocked = false

        // ============================================
        // ESTADO DE BLOQUEO
        // ============================================

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
                        ?.let { overlay ->

                            val project =
                                ProjectManager.getProject()

                            FileLogger.log(
                                this@GLWallpaperService,
                                "LOCKED"
                            )

                            if (
                                project.cueLockedMode ==
                                CueMode.LOOP
                            ) {

                                FileLogger.log(
                                    this@GLWallpaperService,
                                    "CueLocked -> seekTo(0)"
                                )

                                overlay.seekTo(0)

                            } else {

                                FileLogger.log(
                                    this@GLWallpaperService,
                                    "CueLocked -> seekTo(0) + play()"
                                )

                                overlay.seekTo(0)

                                overlay.play()
                            }
                        }

                } else {

                    deviceLocked = false

                    FileLogger.log(
                        this@GLWallpaperService,
                        "UNLOCKED"
                    )

                    renderer
                        ?.getVideoOverlayRenderer()
                        ?.play()
                }
            }
        }

        // ============================================
        // SURFACE CREATED
        // ============================================

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

        // ============================================
        // SURFACE CHANGED
        // ============================================

        override fun onSurfaceChanged(
            holder: SurfaceHolder,
            format: Int,
            width: Int,
            height: Int
        ) {

            super.onSurfaceChanged(
                holder,
                format,
                width,
                height
            )

            FileLogger.log(
                this@GLWallpaperService,
                "onSurfaceChanged: ${width}x${height}"
            )
        }

        // ============================================
        // VISIBILITY
        // ============================================

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

        // ============================================
        // SURFACE DESTROYED
        // ============================================

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

        // ============================================
        // DESTROY
        // ============================================

        override fun onDestroy() {

            FileLogger.log(
                this@GLWallpaperService,
                "onDestroy()"
            )

            stopRendering()

            super.onDestroy()
        }

        // ============================================
        // START RENDERING
        // ============================================

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

                    val surfaceHolder =
                        holder ?: return@Thread

                    // ====================================
                    // CREAR RENDERER
                    // ====================================

                    renderer =
                        GLRenderer(
                            this@GLWallpaperService,
                            surfaceHolder
                        )

                    renderer!!.initialize()

                    // ====================================
                    // VIDEO DE FONDO
                    // ====================================

                    videoPlayer =
                        VideoPlayer(
                            this@GLWallpaperService
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
                        WallpaperSoundPlayer(
                            this@GLWallpaperService
                        )

                    overlaySoundPlayer =
                        WallpaperSoundPlayer(
                            this@GLWallpaperService
                        )

                    // ====================================
                    // RESTAURAR VIDEO BG
                    // ====================================

                    if (savedPosition > 0) {

                        videoPlayer!!.seekTo(
                            savedPosition
                        )

                        FileLogger.log(
                            this@GLWallpaperService,
                            "Video restaurado en posición: ${savedPosition}ms"
                        )
                    }

                    videoPlayer!!.play()

                    // ====================================
                    // VIDEO OVERLAY
                    // ====================================

                    renderer!!
                        .getVideoOverlayRenderer()
                        ?.let { overlay ->

                            val project =
                                ProjectManager.getProject()
                            
                            lastOverlayLoopEnabled =
                                project.overlayLoopEnabled

                            // --------------------------------
                            // Restaurar posición
                            // --------------------------------

                            if (
                                savedOverlayPosition > 0
                            ) {

                                overlay.seekTo(
                                    savedOverlayPosition
                                )

                                FileLogger.log(
                                    this@GLWallpaperService,
                                    "Overlay restaurado en posición: ${savedOverlayPosition}ms"
                                )
                            }

                            // --------------------------------
                            // Cargar cues
                            // --------------------------------

                            overlayCueController.cueLockedMs =
                                project.cueLockedMs

                            overlayCueController.cueUnlockedMs =
                                project.cueUnlockedMs

                            // --------------------------------
                            // Configurar looping
                            //
                            // Cues OFF:
                            // MediaPlayer controla el loop.
                            //
                            // Cues ON:
                            // nuestro sistema controla
                            // los loops.
                            // --------------------------------

                            overlay.setLooping(
                                !project.overlayLoopEnabled
                            )

                            // --------------------------------
                            // Completion
                            // --------------------------------

                            overlay.setOnCompletionListener {

                                val currentProject =
                                    ProjectManager.getProject()

                                FileLogger.log(
                                    this@GLWallpaperService,
                                    "Overlay -> onCompletion()"
                                )

                                // --------------------------------
                                // Si los cues están desactivados,
                                // MediaPlayer tiene looping=true,
                                // por lo que normalmente nunca
                                // llegaremos aquí.
                                // --------------------------------

                                if (
                                    !currentProject.overlayLoopEnabled
                                ) {

                                    FileLogger.log(
                                        this@GLWallpaperService,
                                        "Overlay -> completion ignorado: cues desactivados"
                                    )

                                    return@setOnCompletionListener
                                }

                                // --------------------------------
                                // Si el dispositivo está bloqueado
                                // el final natural no controla
                                // el estado.
                                //
                                // El cueLocked se controla desde
                                // el RenderThread.
                                // --------------------------------

                                if (
                                    currentProject.previewLocked
                                ) {

                                    FileLogger.log(
                                        this@GLWallpaperService,
                                        "Overlay -> completion ignorado: dispositivo bloqueado"
                                    )

                                    return@setOnCompletionListener
                                }

                                // --------------------------------
                                // DISPOSITIVO DESBLOQUEADO
                                // --------------------------------

                                if (
                                    currentProject.cueUnlockedMode ==
                                    CueMode.LOOP
                                ) {

                                    FileLogger.log(
                                        this@GLWallpaperService,
                                        "CueUnlocked -> completion -> seekTo(${currentProject.cueUnlockedMs})"
                                    )

                                    overlay.seekTo(
                                        currentProject.cueUnlockedMs
                                    )

                                    overlay.play()

                                } else {

                                    FileLogger.log(
                                        this@GLWallpaperService,
                                        "CueUnlocked -> completion -> pause(end)"
                                    )

                                    overlay.pause()
                                }
                            }

                            // --------------------------------
                            // Iniciar reproducción
                            // --------------------------------

                            overlay.play()
                        }

                    // ====================================
                    // AUDIO
                    // ====================================

                    initializeAudioConfiguration()

                    var frameCount = 0

                    // ====================================
                    // RENDER LOOP
                    // ====================================

                    while (running) {

                        // --------------------------------
                        // Estado real del dispositivo
                        // --------------------------------

                        updateCueState()

                        // --------------------------------
                        // Audio
                        // --------------------------------

                        applyAudioConfiguration()

                        // --------------------------------
                        // Detectar cambios de proyecto
                        // --------------------------------

                        val revision =
                            ProjectManager.getRevision()

                        if (
                            revision != lastRevision
                        ) {
                        
                            lastRevision =
                                revision
                        
                            initializeAudioConfiguration()
                        
                            renderer
                                ?.getVideoOverlayRenderer()
                                ?.let { overlay ->
                        
                                    val project =
                                        ProjectManager.getProject()
                        
                                    // ============================================
                                    // CAMBIO DEL OVERLAY LOOP INTELIGENTE
                                    // ============================================
                        
                                    if (
                                        project.overlayLoopEnabled !=
                                        lastOverlayLoopEnabled
                                    ) {
                        
                                        lastOverlayLoopEnabled =
                                            project.overlayLoopEnabled
                        
                                        FileLogger.log(
                                            this@GLWallpaperService,
                                            "Overlay Loop Inteligente cambió a ${project.overlayLoopEnabled} -> seekTo(0) + play()"
                                        )
                        
                                        overlay.seekTo(0)
                        
                                        overlay.play()
                                    }
                        
                                    // ============================================
                                    // CONFIGURAR LOOP NATIVO DEL MEDIAPLAYER
                                    // ============================================
                        
                                    overlay.setLooping(
                                        !project.overlayLoopEnabled
                                    )
                                }
                        }

                        // --------------------------------
                        // OVERLAY
                        // --------------------------------

                        renderer
                            ?.getVideoOverlayRenderer()
                            ?.let { overlay ->

                                val position =
                                    overlay.getCurrentPosition()

                                val duration =
                                    overlay.getDuration()

                                // --------------------------------
                                // Guardar duración real
                                // --------------------------------

                                if (
                                    duration > 0 &&
                                    ProjectManager
                                        .getProject()
                                        .overlayDurationMs !=
                                            duration.toLong()
                                ) {

                                    ProjectManager
                                        .getProject()
                                        .overlayDurationMs =
                                        duration.toLong()

                                    ProjectManager.saveProject(
                                        ProjectManager.getProject()
                                    )
                                }

                                // --------------------------------
                                // Leer cues
                                // --------------------------------

                                val project =
                                    ProjectManager.getProject()

                                overlayCueController.cueLockedMs =
                                    project.cueLockedMs

                                overlayCueController.cueUnlockedMs =
                                    project.cueUnlockedMs

                                // --------------------------------
                                // LÓGICA DEL OVERLAY
                                // --------------------------------

                                if (
                                    project.overlayLoopEnabled &&
                                    duration > 0
                                ) {

                                    // ============================
                                    // BLOQUEADO
                                    // ============================

                                    if (deviceLocked) {

                                        if (
                                            position >=
                                            overlayCueController.cueLockedMs
                                        ) {

                                            if (
                                                project.cueLockedMode ==
                                                CueMode.LOOP
                                            ) {

                                                FileLogger.log(
                                                    this@GLWallpaperService,
                                                    "CueLocked -> seekTo(0)"
                                                )

                                                overlay.seekTo(0)

                                            } else {

                                                FileLogger.log(
                                                    this@GLWallpaperService,
                                                    "CueLocked -> pause()"
                                                )

                                                overlay.pause()
                                            }
                                        }

                                    }

                                    // ============================
                                    // DESBLOQUEADO
                                    // ============================

                                    else {

                                        // --------------------------------
                                        // IMPORTANTE:
                                        //
                                        // NO comprobamos:
                                        //
                                        // position >= duration - 50
                                        //
                                        // El final real ahora lo informa
                                        // MediaPlayer mediante
                                        // onCompletion().
                                        // --------------------------------
                                    }
                                }
                            }

                        // --------------------------------
                        // Dibujar
                        // --------------------------------

                        renderer!!.drawFrame()

                        frameCount++

                        if (
                            frameCount % 120 == 0
                        ) {

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

                    // ========================================
                    // GUARDAR POSICIONES
                    // ========================================

                    videoPlayer?.let {

                        savedPosition =
                            it.getCurrentPosition()

                        FileLogger.log(
                            this@GLWallpaperService,
                            "Video guardado en posición: ${savedPosition}ms"
                        )
                    }

                    renderer
                        ?.getVideoOverlayRenderer()
                        ?.let { overlay ->

                            val project =
                                ProjectManager.getProject()

                            savedOverlayPosition =
                                if (
                                    deviceLocked &&
                                    project.cueLockedMode ==
                                    CueMode.PAUSE
                                ) {

                                    0

                                } else {

                                    overlay.getCurrentPosition()
                                }

                            FileLogger.log(
                                this@GLWallpaperService,
                                "Overlay guardado en posición: ${savedOverlayPosition}ms"
                            )
                        }

                    // ========================================
                    // RELEASE
                    // ========================================

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

        // ============================================
        // STOP RENDERING
        // ============================================

        private fun stopRendering() {

            running = false

            renderThread?.interrupt()

            try {

                renderThread?.join(
                    1000
                )

            } catch (_: Exception) {
            }

            renderThread = null

            FileLogger.log(
                this@GLWallpaperService,
                "RenderThread detenido"
            )
        }

        // ============================================
        // AUDIO - APPLY
        // ============================================

        private fun applyAudioConfiguration() {

            val project =
                ProjectManager.getProject()

            val bgLayer =
                project.layers.firstOrNull()

            if (bgLayer != null) {

                if (
                    bgLayer.soundPath.isNullOrEmpty()
                ) {

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

            if (
                overlay.soundPath.isNullOrEmpty()
            ) {

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

        // ============================================
        // AUDIO - INITIALIZE
        // ============================================

        private fun initializeAudioConfiguration() {

            val project =
                ProjectManager.getProject()

            // ========================================
            // VIDEO BG
            // ========================================

            val bgLayer =
                project.layers.firstOrNull()

            if (bgLayer != null) {

                if (
                    bgLayer.soundPath.isNullOrEmpty()
                ) {

                    videoPlayer?.setVolume(
                        bgLayer.soundVolume
                    )

                    bgSoundPlayer?.stop()

                } else {

                    videoPlayer?.setVolume(
                        0f
                    )

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

            // ========================================
            // OVERLAY
            // ========================================

            val overlay =
                project.overlay

            if (
                overlay.soundPath.isNullOrEmpty()
            ) {

                renderer
                    ?.getVideoOverlayRenderer()
                    ?.setVolume(
                        overlay.soundVolume
                    )

                overlaySoundPlayer?.stop()

            } else {

                renderer
                    ?.getVideoOverlayRenderer()
                    ?.setVolume(
                        0f
                    )

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