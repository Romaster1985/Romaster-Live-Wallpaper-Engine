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
    
    private val overlayCueController =
        CueLoopController()
    
    private var lastRevision = -1
    
    private var lastOverlayLoopEnabled: Boolean? = null
    
    private var continue_play = true
    
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
                
                // Restaurar posición del Video OL si hay una guardada
                renderer!!
                    .getVideoOverlayRenderer()
                    ?.let { overlay ->
                
                        if (savedOverlayPosition > 0) {
                
                            overlay.seekTo(
                                savedOverlayPosition
                            )
                
                        }
                
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
                
                            if (
                                currentProject.overlayLoopEnabled &&
                                !currentProject.previewLocked
                            ) {
                
                                if (
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
                
                                } else {
                
                                    FileLogger.log(
                                        context,
                                        "CueUnlocked -> completion -> pause(end)"
                                    )
                
                                    overlay.pause()
                
                                }
                
                            }
                
                        }
                
                        overlay.play()
                
                    }
                
                initializeAudioConfiguration()

                var frameCount = 0

                while (running) {

                    applyAudioConfiguration()
                
                    val revision =
                        ProjectManager.getRevision()
                
                    if (revision != lastRevision) {

                        lastRevision = revision
                    
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
                
                            val duration =
                                overlay.getDuration()
                
                            // -----------------------------------------
                            // Guardar duración real del Overlay
                            // -----------------------------------------
                
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
                            // Lógica de Loop
                            // -----------------------------------------
                
                            if (
                                project.overlayLoopEnabled &&
                                duration > 0
                            ) {
                
                                if (
                                    project.previewLocked
                                ) {
                
                                    if (
                                        (position >= overlayCueController.cueLockedMs)
                                        && project.previewLocked
                                    ) {
                                    
                                        if (
                                            (project.cueLockedMode == CueMode.LOOP)
                                            && project.previewLocked
                                        ) {
                                    
                                            overlay.seekTo(0)
                                    
                                        } else {
                                            
                                            if (project.previewLocked) {
                                                overlay.pause()
                                            }
                                    
                                        }
                                    
                                    }
                                    
                                    if ((continue_play == false)
                                        && project.previewLocked)
                                    {
                                        overlay.seekTo(0)
                                        overlay.play()
                                        continue_play = true
                                    }
                
                                } else {
                                    
                                    if ((continue_play == true)
                                        && !(project.previewLocked))
                                    {
                                        overlay.play()
                                        continue_play = false
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
                
                            it(
                                bmp
                            )
                
                        }
                
                        pendingCapture =
                            null
                
                    }
                
                    Thread.sleep(
                        16
                    )
                
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
    
        stopRendering()
        startRendering()
        
    }
}