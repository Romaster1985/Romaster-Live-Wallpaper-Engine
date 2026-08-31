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
import com.romaster.livewallengine.video.CueLoopController
import com.romaster.livewallengine.project.ProjectManager
import com.romaster.livewallengine.audio.WallpaperSoundPlayer
import com.romaster.livewallengine.audio.AudioStorage
import com.romaster.livewallengine.audio.AudioPicker
import com.romaster.livewallengine.model.CueMode
import com.romaster.livewallengine.video.OverlayPlaybackDirection
import com.romaster.livewallengine.video.ReverseClipKind
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
    @Volatile private var pendingReloadImageLayers = false

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

    private var savedOverlayPaused: Boolean = false
    
    private val overlayCueController =
        CueLoopController()
    
    private var lastRevision = -1
    
    private var lastOverlayLoopEnabled: Boolean? = null

    /**
     * Último estado de bloqueo simulado.
     * null = aún no inicializado (no disparar transición al primer frame).
     * Misma idea que lastLockState en GLWallpaperService.
     */
    private var lastPreviewLocked: Boolean? = null
    
    init {

        holder.addCallback(this)
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
        lastPreviewLocked = null

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
                
                // Restaurar posición del Video OL
                renderer!!
                    .getVideoOverlayRenderer()
                    ?.let { overlay ->
                
                        val project =
                            ProjectManager.getProject()
                
                        overlayCueController.cueLockedMs =
                            project.cueLockedMs
                
                        overlayCueController.cueUnlockedMs =
                            project.cueUnlockedMs
                
                        // Cuando usamos nuestro sistema de cues,
                        // el MediaPlayer NO debe hacer loop.
                        //
                        // Cuando el sistema de cues está desactivado,
                        // dejamos que MediaPlayer haga su propio loop.
                        // Además inicializamos el eatado de nuestro sistema en la primera carga.
                        lastOverlayLoopEnabled =
                            project.overlayLoopEnabled
                        
                        overlay.setLooping(
                            !project.overlayLoopEnabled
                        )
                
                        // -----------------------------------------
                        // FIN REAL DEL VIDEO
                        // -----------------------------------------
                
                        overlay.setOnCompletionListener {
                
                            val currentProject =
                                ProjectManager.getProject()
                
                            FileLogger.log(
                                context,
                                "WallpaperPreview -> Overlay onCompletion()"
                            )
                
                            if (currentProject.overlayLoopEnabled) {

                                // Primero: fin del clip invertido (locked o unlocked)
                                if (overlay.isPlayingReverseClip()) {

                                    val kind = overlay.getReverseClipKind()
                                    FileLogger.log(
                                        context,
                                        "Ping-pong reverse terminó (preview) kind=$kind locked=${currentProject.previewLocked}"
                                    )

                                    when (kind) {
                                        ReverseClipKind.UNLOCKED -> {
                                            overlay.setDirection(
                                                OverlayPlaybackDirection.FORWARD,
                                                startPositionMs = currentProject.cueUnlockedMs
                                            )
                                        }
                                        else -> {
                                            // LOCKED: siempre desde 0 (también si desbloquearon a mitad)
                                            overlay.setDirection(
                                                OverlayPlaybackDirection.FORWARD,
                                                startPositionMs = 0
                                            )
                                        }
                                    }

                                } else if (
                                    !currentProject.previewLocked &&
                                    currentProject.cueUnlockedPingPong &&
                                    !currentProject.cueUnlockedReverseFile.isNullOrBlank()
                                ) {

                                    FileLogger.log(
                                        context,
                                        "CueUnlocked -> completion -> play reverse clip"
                                    )

                                    overlay.setDirection(
                                        OverlayPlaybackDirection.REVERSE,
                                        reverseFileName = currentProject.cueUnlockedReverseFile,
                                        reverseKind = ReverseClipKind.UNLOCKED
                                    )

                                } else if (
                                    !currentProject.previewLocked &&
                                    currentProject.cueUnlockedMode ==
                                        CueMode.LOOP
                                ) {
                
                                    FileLogger.log(
                                        context,
                                        "CueUnlocked -> completion -> seekTo(${currentProject.cueUnlockedMs})"
                                    )
                
                                    overlay.seekTo(
                                        currentProject.cueUnlockedMs
                                    )
                
                                    overlay.play()
                
                                } else if (!currentProject.previewLocked) {
                
                                    FileLogger.log(
                                        context,
                                        "CueUnlocked -> completion -> pause(end)"
                                    )
                
                                    overlay.pause()
                
                                }
                
                            }
                
                        }

                        try {
                            Thread.sleep(40)
                        } catch (_: InterruptedException) {
                        }

                        val softStart = {
                            overlay.startSoftStart()
                        }
                        val lockedNow =
                            ProjectManager.getProject().previewLocked
                        if (lockedNow) {
                            val hideOnLock =
                                ProjectManager.getProject()
                                    .overlay.disableOnLockScreen
                            if (hideOnLock) {
                                FileLogger.log(
                                    context,
                                    "Preview LOCKED resume -> oculto"
                                )
                                overlay.setForceHidden(true)
                                overlay.restoreAt(0, paused = false)
                            } else {
                                FileLogger.log(
                                    context,
                                    "Preview LOCKED resume -> desde 0 + Soft Start"
                                )
                                overlay.restoreAt(
                                    0,
                                    paused = false,
                                    onReady = softStart
                                )
                            }
                        } else if (savedOverlayPaused) {
                            FileLogger.log(
                                context,
                                "Preview overlay restoreAt paused pos=$savedOverlayPosition"
                            )
                            overlay.restoreAt(
                                savedOverlayPosition,
                                paused = true,
                                onReady = softStart
                            )
                        } else if (savedOverlayPosition > 0) {
                            overlay.restoreAt(
                                savedOverlayPosition,
                                paused = false,
                                onReady = softStart
                            )
                        } else {
                            overlay.play()
                            softStart()
                        }
                
                    }
                
                initializeAudioConfiguration()

                var frameCount = 0
                var overlayStalledFrames = 0

                while (running) {

                    applyAudioConfiguration()
                
                    val revision =
                        ProjectManager.getRevision()
                
                    if (revision != lastRevision) {

                        lastRevision = revision

                        // Re-aplicar visibilidad de capas; no cancelar Soft Start del reloj
                        val projRev = ProjectManager.getProject()
                        if (projRev.previewLocked) {
                            renderer?.setImageLayersLockState(true)
                            if (!projRev.clock.enabledOnLockScreen) {
                                renderer?.setClockLockScreenState(
                                    visible = false,
                                    fadeIn = false
                                )
                            }
                        } else {
                            renderer?.setImageLayersLockState(false)
                        }
                    
                        initializeAudioConfiguration()
                    
                        renderer
                            ?.getVideoOverlayRenderer()
                            ?.let { overlay ->
                    
                                val project =
                                    ProjectManager.getProject()
                    
                                val loopEnabled =
                                    project.overlayLoopEnabled
                    
                                // ============================================
                                // CAMBIO DE ESTADO DEL SMART LOOP
                                // ============================================
                    
                                val loopStateChanged =
                                    lastOverlayLoopEnabled != null &&
                                    lastOverlayLoopEnabled != loopEnabled
                    
                                // Configurar el modo normal de reproducción
                                //
                                // Smart Loop ACTIVADO  -> nuestro CueLoopController
                                // Smart Loop DESACTIVADO -> loop nativo del player
                                //
                                overlay.setLooping(
                                    !loopEnabled
                                )
                    
                                if (loopStateChanged) {
                    
                                    FileLogger.log(
                                        context,
                                        "WallpaperPreview -> overlayLoopEnabled cambió: " +
                                        "${lastOverlayLoopEnabled} -> $loopEnabled"
                                    )
                    
                                    FileLogger.log(
                                        context,
                                        "WallpaperPreview -> reiniciando Overlay desde 0"
                                    )
                    
                                    // ========================================
                                    // MUY IMPORTANTE:
                                    // cada cambio de modo reinicia el video
                                    // ========================================
                    
                                    overlay.seekTo(0)
                    
                                    overlay.play()
                                }
                    
                                lastOverlayLoopEnabled =
                                    loopEnabled
                            }
                    }
                
                    renderer
                        ?.getVideoOverlayRenderer()
                        ?.let { overlay ->
                
                            val position =
                                overlay.getCurrentPosition()
                
                            // Duración del MediaPlayer actual (puede ser el clip de reversa).
                            // Para cues / UI SIEMPRE usamos overlayDurationMs del proyecto
                            // (duración del original), nunca la del reverse.
                            val playerDuration =
                                overlay.getDuration()

                            val duration =
                                ProjectManager
                                    .getProject()
                                    .overlayDurationMs
                                    .toInt()
                                    .takeIf { it > 0 }
                                    ?: playerDuration
                
                            // -----------------------------------------
                            // Guardar duración SOLO del video original
                            // -----------------------------------------
                
                            if (
                                !overlay.isPlayingReverseClip() &&
                                playerDuration > 0 &&
                                ProjectManager
                                    .getProject()
                                    .overlayDurationMs !=
                                        playerDuration.toLong()
                            ) {
                            
                                ProjectManager
                                    .getProject()
                                    .overlayDurationMs =
                                    playerDuration.toLong()
                            
                                ProjectManager.saveProject(
                                    ProjectManager.getProject()
                                )
                            
                            }
                
                            // -----------------------------------------
                            // Leer Cues desde ProjectManager
                            // -----------------------------------------
                
                            val project =
                                ProjectManager.getProject()
                
                            overlayCueController.cueLockedMs =
                                project.cueLockedMs
                
                            overlayCueController.cueUnlockedMs =
                                project.cueUnlockedMs
                
                            // -----------------------------------------
                            // Transición bloqueo simulado (borde)
                            // Igual que updateCueState() del wallpaper service
                            // -----------------------------------------

                            val lockedNow = project.previewLocked
                            val prevLocked = lastPreviewLocked

                            if (prevLocked != null && lockedNow != prevLocked) {

                                if (lockedNow) {
                                    FileLogger.log(
                                        context,
                                        "Preview LOCKED (sim) -> original desde 0"
                                    )
                                    overlay.setForceHidden(true)
                                    val hideOnLock =
                                        project.overlay.disableOnLockScreen
                                    if (hideOnLock) {
                                        overlay.setDirection(
                                            OverlayPlaybackDirection.FORWARD,
                                            startPositionMs = 0
                                        )
                                    } else {
                                        val softMs =
                                            project.overlayFadeDurationMs
                                                .coerceAtLeast(1L)
                                        overlay.setDirection(
                                            OverlayPlaybackDirection.FORWARD,
                                            startPositionMs = 0,
                                            onReady = {
                                                overlay.setForceHidden(false)
                                                overlay.startOverlayFadeIn(softMs)
                                            }
                                        )
                                    }

                                    // Reloj e imágenes: misma lógica que el wallpaper real
                                    if (project.clock.enabledOnLockScreen) {
                                        renderer?.setClockLockScreenState(
                                            visible = true,
                                            fadeIn = true
                                        )
                                    } else {
                                        renderer?.setClockLockScreenState(
                                            visible = false,
                                            fadeIn = false
                                        )
                                    }
                                    renderer?.startImageLayersSoftStartOnLock()
                                } else {
                                    FileLogger.log(
                                        context,
                                        "Preview UNLOCKED (sim) reverse=" +
                                            overlay.isPlayingReverseClip()
                                    )
                                    if (project.overlay.disableOnLockScreen) {
                                        val softMs =
                                            project.overlayFadeDurationMs
                                                .coerceAtLeast(1L)
                                        overlay.setForceHidden(false)
                                        overlay.startSoftStart(softMs)
                                    }
                                    if (overlay.isPlayingReverseClip()) {
                                        // Dejar terminar la reversa
                                    } else {
                                        overlay.play()
                                    }

                                    renderer?.setClockLockScreenState(
                                        visible = true,
                                        fadeIn = !project.clock.enabledOnLockScreen
                                    )
                                    renderer?.revealImageLayersAfterUnlock()
                                }
                            }

                            lastPreviewLocked = lockedNow

                            // -----------------------------------------
                            // Lógica de Loop / Ping-pong (estado estable)
                            // -----------------------------------------
                
                            if (
                                project.overlayLoopEnabled &&
                                duration > 0
                            ) {
                
                                if (lockedNow) {

                                    if (
                                        project.cueLockedPingPong &&
                                        !project.cueLockedReverseFile.isNullOrBlank()
                                    ) {

                                        if (
                                            !overlay.isPlayingReverseClip() &&
                                            position >=
                                            (overlayCueController.cueLockedMs - 30).coerceAtLeast(0)
                                        ) {
                                            overlay.setDirection(
                                                OverlayPlaybackDirection.REVERSE,
                                                reverseFileName = project.cueLockedReverseFile,
                                                reverseKind = ReverseClipKind.LOCKED
                                            )
                                        }

                                    } else if (
                                        position >= overlayCueController.cueLockedMs
                                    ) {
                                    
                                        if (project.cueLockedMode == CueMode.LOOP) {
                                            overlay.seekTo(0)
                                        } else {
                                            overlay.pause()
                                        }
                                    }
                
                                } else {

                                    // Unlocked: el fin de video / reverse lo maneja onCompletion
                                    // (igual que el service)
                                }
                
                            }
                
                        }
                
                    // Watchdog overlay (no tocar pausas intencionales)
                    renderer
                        ?.getVideoOverlayRenderer()
                        ?.let { overlay ->
                            val proj = ProjectManager.getProject()
                            val pos = overlay.getCurrentPosition()
                            val dur = proj.overlayDurationMs
                                .toInt()
                                .coerceAtLeast(overlay.getDuration())
                            val locked = proj.previewLocked

                            val intentionalPause =
                                when {
                                    overlay.isPlaying() -> false

                                    locked &&
                                        !proj.cueLockedPingPong &&
                                        proj.cueLockedMode == CueMode.PAUSE &&
                                        pos >= proj.cueLockedMs -> true

                                    !locked &&
                                        !proj.cueUnlockedPingPong &&
                                        proj.cueUnlockedMode == CueMode.PAUSE &&
                                        dur > 0 &&
                                        pos >= (dur - 250).coerceAtLeast(
                                            proj.cueUnlockedMs
                                        ) -> true

                                    else -> false
                                }

                            if (intentionalPause ||
                                overlay.isIntentionallyPaused() ||
                                proj.overlayVideo == null ||
                                overlay.isPlayingReverseClip()
                            ) {
                                overlayStalledFrames = 0
                            } else if (!overlay.isPlaying()) {
                                overlayStalledFrames++
                                if (overlayStalledFrames >= 45) {
                                    FileLogger.log(
                                        context,
                                        "Preview overlay watchdog: recoverPlayback pos=$pos"
                                    )
                                    overlay.recoverPlayback(
                                        pos.coerceAtLeast(0)
                                    )
                                    overlayStalledFrames = 0
                                } else if (overlayStalledFrames == 20) {
                                    overlay.ensurePlaying(pos)
                                }
                            } else {
                                overlayStalledFrames = 0
                            }
                        }

                    try {
                        if (pendingReloadImageLayers) {
                            try {
                                renderer?.reloadImageLayers()
                            } catch (e: Exception) {
                                FileLogger.logException(
                                    context,
                                    "Preview reloadImageLayers",
                                    e
                                )
                            }
                            pendingReloadImageLayers = false
                        }
                        renderer?.drawFrame()

                        pendingCapture?.let {
                            try {
                                GLES20.glFinish()
                                val bmp = renderer?.captureBitmap()
                                if (bmp != null) {
                                    post { it(bmp) }
                                }
                            } catch (e: Exception) {
                                FileLogger.logException(
                                    context,
                                    "Preview capture",
                                    e
                                )
                            }
                            pendingCapture = null
                        }
                    } catch (e: Exception) {
                        // No matar el RenderThread por un frame fallido
                        FileLogger.logException(
                            context,
                            "Preview frame",
                            e
                        )
                        try {
                            Thread.sleep(32)
                        } catch (_: InterruptedException) {
                            throw InterruptedException()
                        }
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
                        val project = ProjectManager.getProject()
                        if (project.previewLocked) {
                            // Simulación de lock: al volver, siempre desde 0
                            savedOverlayPaused = false
                            savedOverlayPosition = 0
                        } else {
                            val unlockedPause =
                                !project.cueUnlockedPingPong &&
                                    project.cueUnlockedMode == CueMode.PAUSE &&
                                    !overlay.isPlaying()

                            savedOverlayPaused =
                                overlay.isIntentionallyPaused() || unlockedPause

                            savedOverlayPosition =
                                when {
                                    // PAUSE al final: fijar SIEMPRE duration
                                    // (MediaPlayer a veces reporta 0 tras EOF)
                                    savedOverlayPaused && unlockedPause ->
                                        project.overlayDurationMs
                                            .toInt()
                                            .coerceAtLeast(
                                                overlay.getDuration()
                                            )

                                    overlay.isPlayingReverseClip() -> {
                                        when (overlay.getReverseClipKind()) {
                                            ReverseClipKind.UNLOCKED ->
                                                project.cueUnlockedMs
                                            else -> 0
                                        }
                                    }

                                    else ->
                                        overlay.getCurrentPosition()
                                }
                        }
                        FileLogger.log(
                            context,
                            "Preview overlay guardado pos=$savedOverlayPosition paused=$savedOverlayPaused"
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
                    context,
                    "WallpaperPreview.RenderThread finalizado"
                )
            }
        }

        renderThread!!.start()
    }

    private fun stopRendering() {

        FileLogger.log(
            context,
            "WallpaperPreview.stopRendering() -> iniciando"
        )
    
        running = false
    
        val thread =
            renderThread
    
        thread?.interrupt()
    
        try {
    
            thread?.join()
    
        } catch (_: InterruptedException) {
    
            Thread.currentThread().interrupt()
        }
    
        if (thread != null && thread.isAlive) {
    
            FileLogger.log(
                context,
                "WallpaperPreview.stopRendering() -> ERROR: RenderThread sigue vivo"
            )
    
        } else {
    
            FileLogger.log(
                context,
                "WallpaperPreview.stopRendering() -> RenderThread terminado"
            )
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
        // Proyecto nuevo / import: no restaurar pausa ni posición vieja
        savedPosition = 0
        savedOverlayPosition = 0
        savedOverlayPaused = false
        lastPreviewLocked = null
        stopRendering()
        startRendering()
    }

    /** Marca recarga de texturas Pics-OL en el hilo de render. */
    fun reloadImageLayers() {
        pendingReloadImageLayers = true
    }

}