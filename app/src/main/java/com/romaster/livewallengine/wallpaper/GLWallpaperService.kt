package com.romaster.livewallengine.wallpaper

import android.app.KeyguardManager
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder

import com.romaster.livewallengine.editor.MainEditorController
import com.romaster.livewallengine.audio.AudioStorage
import com.romaster.livewallengine.audio.WallpaperSoundPlayer
import com.romaster.livewallengine.debug.FileLogger
import com.romaster.livewallengine.model.CueMode
import com.romaster.livewallengine.project.ProjectManager
import com.romaster.livewallengine.render.GLRenderer
import com.romaster.livewallengine.video.CueLoopController
import com.romaster.livewallengine.video.OverlayPlaybackDirection
import com.romaster.livewallengine.video.ReverseClipKind
import com.romaster.livewallengine.video.VideoPlayer

class GLWallpaperService : WallpaperService() {

    private lateinit var editor:
        MainEditorController

    override fun onCreateEngine(): Engine {

        FileLogger.startNewSession(this)

        FileLogger.writeDeviceInfo(this)

        FileLogger.log(
            this,
            "GLWallpaperService.onCreateEngine()"
        )

        editor = MainEditorController(this)

        editor.load()

        return GLEngine()
    }

    inner class GLEngine : Engine() {

        private var holder: SurfaceHolder? = null

        private var renderer: GLRenderer? = null

        private var lastSurfaceWidth: Int = 0

        private var lastSurfaceHeight: Int = 0

        private var videoPlayer: VideoPlayer? = null

        private var bgSoundPlayer: WallpaperSoundPlayer? = null

        private var overlaySoundPlayer: WallpaperSoundPlayer? = null

        private var renderThread: Thread? = null

        /*
         * CAMBIO:
         *
         * Ya no usamos un único "running" global para controlar
         * todas las generaciones del RenderThread.
         *
         * Cada RenderThread tendrá su propio estado de ejecución.
         *
         * Esto evita que un thread nuevo pueda quedar afectado
         * por el estado de un thread anterior.
         */

        @Volatile
        private var visible = false

        /*
         * CAMBIO:
         *
         * Identificador de generación del RenderThread.
         *
         * Cada vez que creamos un RenderThread nuevo incrementamos
         * este valor.
         *
         * Un thread antiguo podrá comprobar que ya no es la
         * generación válida y finalizar sin tocar recursos
         * pertenecientes al thread nuevo.
         */

        @Volatile
        private var renderGeneration = 0L

        // ============================================
        // POSICIONES GUARDADAS
        // ============================================

        private var savedPosition: Int = 0

        private var savedOverlayPosition: Int = 0

        /** true si el overlay estaba en pausa al ocultarse (modo PAUSE). */
        private var savedOverlayPaused: Boolean = false

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

                val project =
                    ProjectManager.getProject()

                val clock =
                    project.clock

                if (locked) {

                    deviceLocked = true

                    FileLogger.log(
                        this@GLWallpaperService,
                        "LOCKED -> siempre original desde 0"
                    )

                    val overlay =
                        renderer?.getVideoOverlayRenderer()
                    // Anti-flash del frame anterior
                    overlay?.setForceHidden(true)

                    val hideOnLock =
                        project.overlay.disableOnLockScreen

                    if (hideOnLock) {
                        // No mostrar Video-OL en pantalla de bloqueo
                        FileLogger.log(
                            this@GLWallpaperService,
                            "LOCKED -> overlay deshabilitado en lock screen"
                        )
                        // Sigue en original desde 0 por debajo (sin dibujar)
                        overlay?.setDirection(
                            OverlayPlaybackDirection.FORWARD,
                            startPositionMs = 0
                        )
                    } else {
                        // Soft Start al llegar el frame 0
                        val softMs =
                            project.overlayFadeDurationMs
                                .coerceAtLeast(1L)
                        overlay?.setDirection(
                            OverlayPlaybackDirection.FORWARD,
                            startPositionMs = 0,
                            onReady = {
                                overlay.setForceHidden(false)
                                overlay.startOverlayFadeIn(softMs)
                            }
                        )
                    }

                    renderer?.setClockLockScreenState(
                        visible =
                            clock.enabledOnLockScreen,
                        fadeIn = false
                    )

                } else {

                    deviceLocked = false

                    FileLogger.log(
                        this@GLWallpaperService,
                        "UNLOCKED reversePlaying=" +
                            (renderer
                                ?.getVideoOverlayRenderer()
                                ?.isPlayingReverseClip() == true)
                    )

                    renderer?.setClockLockScreenState(
                        visible = true,
                        fadeIn = !clock.enabledOnLockScreen
                    )

                    val overlay =
                        renderer?.getVideoOverlayRenderer()

                    if (overlay != null) {
                        val wasHiddenOnLock =
                            project.overlay.disableOnLockScreen
                        if (wasHiddenOnLock) {
                            // Revelar con Soft Start al desbloquear
                            val softMs =
                                project.overlayFadeDurationMs
                                    .coerceAtLeast(1L)
                            overlay.setForceHidden(false)
                            overlay.startSoftStart(softMs)
                        }

                        if (overlay.isPlayingReverseClip()) {
                            FileLogger.log(
                                this@GLWallpaperService,
                                "UNLOCKED durante reversa -> se deja terminar el clip"
                            )
                        } else {
                            overlay.play()
                        }
                    }
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

            /*
             * El Surface puede ser creado antes o después de que
             * Android marque el Engine como visible.
             *
             * Por eso solamente iniciamos si realmente estamos
             * visibles.
             */

            if (visible) {
                startRendering()
            }
        }

        // ============================================
        // SURFACE CHANGED
        // ============================================

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            FileLogger.log(this@GLWallpaperService, "onSurfaceChanged: ${width}x${height}")

            // AGREGADO: Registrar los valores recibidos de Android
            lastSurfaceWidth = width
            lastSurfaceHeight = height

            renderer?.onSurfaceChanged(width, height)
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

                /*
                 * CAMBIO:
                 *
                 * startRendering() será responsable de comprobar
                 * si existe otro thread activo.
                 */

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

            /*
             * CAMBIO:
             *
             * Primero detenemos completamente el RenderThread.
             * Recién después eliminamos la referencia al Surface.
             */

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

            /*
             * CAMBIO:
             *
             * Invalidamos inmediatamente cualquier generación
             * anterior antes de detener el thread.
             */

            renderGeneration++

            stopRendering()

            this.holder = null
            this.visible = false

            super.onDestroy()
        }

        // ============================================
        // START RENDERING
        // ============================================

        private fun startRendering() {

            /*
             * CAMBIO:
             *
             * Si ya existe un RenderThread vivo, no creamos otro.
             *
             * Esto es especialmente importante porque Android puede
             * enviar varios onVisibilityChanged(true) muy cerca entre sí.
             */

            if (renderThread?.isAlive == true) {

                FileLogger.log(
                    this@GLWallpaperService,
                    "startRendering(): RenderThread ya activo"
                )

                return
            }

            /*
             * CAMBIO:
             *
             * Nueva generación.
             *
             * El valor queda capturado por este RenderThread y no cambia
             * para él aunque posteriormente se cree otro.
             */

            renderGeneration++

            val myGeneration =
                renderGeneration

            /*
             * CAMBIO:
             *
             * El estado running pertenece conceptualmente a ESTA
             * generación.
             *
             * No usamos un running global.
             */

            val threadRunning =
                java.util.concurrent.atomic.AtomicBoolean(true)

            /*
             * CAMBIO:
             *
             * Validamos todas las condiciones antes de crear recursos
             * gráficos y reproductores.
             */

            val surfaceHolder =
                holder

            if (
                surfaceHolder == null ||
                !surfaceHolder.surface.isValid ||
                !visible
            ) {

                FileLogger.log(
                    this@GLWallpaperService,
                    "startRendering(): inicio abortado -> " +
                        "holder=${surfaceHolder != null}, " +
                        "surfaceValid=${surfaceHolder?.surface?.isValid == true}, " +
                        "visible=$visible"
                )

                return
            }

            /*
             * CAMBIO:
             *
             * El thread recibe su propia referencia al SurfaceHolder.
             *
             * No dependemos de que "holder" siga apuntando al mismo
             * Surface mientras el thread está arrancando.
             */

            renderThread =
                Thread {

                    FileLogger.log(
                        this@GLWallpaperService,
                        "RenderThread iniciado " +
                            "(generation=$myGeneration)"
                    )

                    try {

                        // ====================================
                        // VALIDACIÓN INICIAL
                        // ====================================

                        if (
                            !isGenerationActive(
                                myGeneration,
                                threadRunning
                            )
                        ) {

                            FileLogger.log(
                                this@GLWallpaperService,
                                "RenderThread $myGeneration abortado antes de inicializar"
                            )

                            return@Thread
                        }

                        if (
                            !surfaceHolder.surface.isValid
                        ) {

                            FileLogger.log(
                                this@GLWallpaperService,
                                "RenderThread $myGeneration: Surface no válida"
                            )

                            return@Thread
                        }

                        // ====================================
                        // CREAR RENDERER
                        // ====================================

                        renderer =
                            GLRenderer(
                                this@GLWallpaperService,
                                surfaceHolder
                            )

                        renderer!!.initialize()

                        // AGREGADO: Si ya conocemos dimensiones válidas de la pantalla, se las pasamos de inmediato
                        if (lastSurfaceWidth > 0 && lastSurfaceHeight > 0) {
                            renderer!!.onSurfaceChanged(lastSurfaceWidth, lastSurfaceHeight)
                        }

                        /*
                         * CAMBIO:
                         *
                         * Después de inicializar OpenGL volvemos a
                         * comprobar que esta generación sigue siendo válida.
                         *
                         * Si Android destruyó el Surface durante
                         * initialize(), liberamos inmediatamente.
                         */

                        if (
                            !isGenerationActive(
                                myGeneration,
                                threadRunning
                            )
                        ) {

                            FileLogger.log(
                                this@GLWallpaperService,
                                "RenderThread $myGeneration invalidado después de GLRenderer.initialize()"
                            )

                            return@Thread
                        }

                        // ====================================
                        // VIDEO DE FONDO
                        // ====================================

                        videoPlayer =
                            VideoPlayer(
                                this@GLWallpaperService,
                                "BG"
                            )

                        videoPlayer!!.initialize(
                            renderer!!.getVideoSurface()
                        )

                        videoPlayer!!.setOnVideoSizeChangedListener {

                            width,
                            height ->

                            /*
                             * CAMBIO:
                             *
                             * Los callbacks del VideoPlayer también
                             * pueden llegar después de que la generación
                             * haya sido invalidada.
                             */

                            if (
                                myGeneration !=
                                renderGeneration
                            ) {

                                return@setOnVideoSizeChangedListener
                            }

                            renderer?.setVideoSize(
                                width,
                                height
                            )
                        }

                        // ====================================
                        // AUDIO PLAYERS
                        // ====================================

                        bgSoundPlayer =
                            WallpaperSoundPlayer(
                                this@GLWallpaperService
                            )

                        overlaySoundPlayer =
                            WallpaperSoundPlayer(
                                this@GLWallpaperService
                            )

                        /*
                         * CAMBIO:
                         *
                         * Comprobación antes de continuar con la
                         * reproducción.
                         */

                        if (
                            !isGenerationActive(
                                myGeneration,
                                threadRunning
                            )
                        ) {

                            FileLogger.log(
                                this@GLWallpaperService,
                                "RenderThread $myGeneration invalidado después de crear players"
                            )

                            return@Thread
                        }

                        // ====================================
                        // RESTAURAR VIDEO BG
                        // ====================================

                        if (
                            savedPosition > 0
                        ) {

                            videoPlayer!!.seekTo(
                                savedPosition
                            )

                            FileLogger.log(
                                this@GLWallpaperService,
                                "Video restaurado en posición: ${savedPosition}ms"
                            )
                        }

                        renderer!!.startFadeIn()

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
                                // Restaurar posición / pausa
                                // --------------------------------
                                // Si estaba pausado, restoreAt hace
                                // seek → start (frame) → pause
                                // para no quedar con Surface negra.

                                // --------------------------------
                                // Cargar cues
                                // --------------------------------

                                overlayCueController.cueLockedMs =
                                    project.cueLockedMs

                                overlayCueController.cueUnlockedMs =
                                    project.cueUnlockedMs

                                // --------------------------------
                                // Configurar looping
                                // --------------------------------

                                overlay.setLooping(
                                    !project.overlayLoopEnabled
                                )

                                // --------------------------------
                                // Completion
                                // --------------------------------

                                overlay.setOnCompletionListener {

                                    /*
                                     * CAMBIO:
                                     *
                                     * Un callback viejo nunca debe
                                     * controlar el overlay de una
                                     * generación posterior.
                                     */

                                    if (
                                        myGeneration !=
                                        renderGeneration
                                    ) {

                                        FileLogger.log(
                                            this@GLWallpaperService,
                                            "Overlay completion ignorado: generation=$myGeneration ya no activa"
                                        )

                                        return@setOnCompletionListener
                                    }

                                    val currentProject =
                                        ProjectManager.getProject()

                                    FileLogger.log(
                                        this@GLWallpaperService,
                                        "Overlay -> onCompletion()"
                                    )

                                    // --------------------------------
                                    // Cues desactivados
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

                                    // Fin del clip invertido → volver al original
                                    // (tanto en locked como unlocked)
                                    if (overlay.isPlayingReverseClip()) {
                                        val kind = overlay.getReverseClipKind()
                                        FileLogger.log(
                                            this@GLWallpaperService,
                                            "Ping-pong reverse terminó kind=$kind locked=$deviceLocked"
                                        )

                                        when (kind) {
                                            ReverseClipKind.UNLOCKED -> {
                                                // Fin del tramo unlocked: volver al cue unlocked
                                                overlay.setDirection(
                                                    OverlayPlaybackDirection.FORWARD,
                                                    startPositionMs = currentProject.cueUnlockedMs
                                                )
                                            }
                                            else -> {
                                                // LOCKED (o desconocido): volver al inicio del original.
                                                // Si ya desbloquearon a mitad de la reversa,
                                                // sigue 0 → final y luego actúan cues unlocked.
                                                overlay.setDirection(
                                                    OverlayPlaybackDirection.FORWARD,
                                                    startPositionMs = 0
                                                )
                                            }
                                        }
                                        return@setOnCompletionListener
                                    }

                                    // --------------------------------
                                    // Dispositivo bloqueado
                                    // --------------------------------

                                    if (deviceLocked) {

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
                                        currentProject.cueUnlockedPingPong &&
                                        !currentProject.cueUnlockedReverseFile.isNullOrBlank()
                                    ) {

                                        FileLogger.log(
                                            this@GLWallpaperService,
                                            "CueUnlocked -> completion -> play reverse clip"
                                        )

                                        overlay.setDirection(
                                            OverlayPlaybackDirection.REVERSE,
                                            reverseFileName = currentProject.cueUnlockedReverseFile,
                                            reverseKind = ReverseClipKind.UNLOCKED
                                        )

                                    } else if (
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
                                // Iniciar reproducción / restaurar pausa
                                // --------------------------------
                                if (isGenerationActive(myGeneration, threadRunning)) {
                                    try {
                                        Thread.sleep(40)
                                    } catch (_: InterruptedException) {}

                                    // ¿Dispositivo bloqueado ahora?
                                    // En lock SIEMPRE desde 0 + play (los cues
                                    // pausan al llegar al cue). Nunca restaurar
                                    // "ya pausado en el cue".
                                    val keyguard =
                                        getSystemService(KEYGUARD_SERVICE)
                                            as KeyguardManager
                                    val lockedNow =
                                        keyguard.isKeyguardLocked

                                    val softStart = {
                                        overlay.startSoftStart()
                                    }

                                    if (lockedNow) {
                                        deviceLocked = true
                                        lastLockState = true
                                        val hideOnLock =
                                            ProjectManager.getProject()
                                                .overlay.disableOnLockScreen
                                        if (hideOnLock) {
                                            FileLogger.log(
                                                this@GLWallpaperService,
                                                "Overlay LOCKED resume -> oculto (disableOnLockScreen)"
                                            )
                                            overlay.setForceHidden(true)
                                            overlay.restoreAt(
                                                0,
                                                paused = false
                                            )
                                        } else {
                                            FileLogger.log(
                                                this@GLWallpaperService,
                                                "Overlay LOCKED resume -> desde 0 + Soft Start"
                                            )
                                            overlay.restoreAt(
                                                0,
                                                paused = false,
                                                onReady = softStart
                                            )
                                        }
                                    } else if (savedOverlayPaused) {
                                        FileLogger.log(
                                            this@GLWallpaperService,
                                            "Overlay restoreAt paused pos=$savedOverlayPosition"
                                        )
                                        overlay.restoreAt(
                                            savedOverlayPosition,
                                            paused = true,
                                            onReady = softStart
                                        )
                                    } else if (savedOverlayPosition > 0) {
                                        FileLogger.log(
                                            this@GLWallpaperService,
                                            "Overlay restoreAt playing pos=$savedOverlayPosition"
                                        )
                                        overlay.restoreAt(
                                            savedOverlayPosition,
                                            paused = false,
                                            onReady = softStart
                                        )
                                    } else {
                                        overlay.play()
                                        // Sin seek: arrancar Soft Start ya
                                        softStart()
                                    }
                                }

                            }

                        // ====================================
                        // AUDIO
                        // ====================================

                        initializeAudioConfiguration()

                        var frameCount = 0
                        var overlayStalledFrames = 0

                        // ====================================
                        // RENDER LOOP
                        // ====================================

                        while (
                            isGenerationActive(
                                myGeneration,
                                threadRunning
                            )
                        ) {

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
                                        // LOOP NATIVO DEL MEDIAPLAYER
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

                                    val playerDuration =
                                        overlay.getDuration()

                                    // Cues usan siempre la duración del original
                                    val duration =
                                        ProjectManager
                                            .getProject()
                                            .overlayDurationMs
                                            .toInt()
                                            .takeIf { it > 0 }
                                            ?: playerDuration

                                    // --------------------------------
                                    // Guardar duración SOLO del original
                                    // (nunca la del clip de reversa)
                                    // --------------------------------

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
                                                project.cueLockedPingPong &&
                                                !project.cueLockedReverseFile.isNullOrBlank()
                                            ) {

                                                if (
                                                    !overlay.isPlayingReverseClip() &&
                                                    // Un poco antes del cue evita pasarse del
                                                    // frame exacto (MediaPlayer reporta posición
                                                    // con latencia de un par de frames).
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

                                            // Ping-pong unlocked: el final del original
                                            // y del clip invertido se manejan en onCompletion.
                                            /*
                                             * Sin ping-pong, el final natural
                                             * sigue siendo onCompletion().
                                             */
                                        }
                                    }
                                }

                            // --------------------------------
                            // Watchdog overlay: solo freeze
                            // accidental (NUNCA tocar PAUSE)
                            // --------------------------------

                            renderer
                                ?.getVideoOverlayRenderer()
                                ?.let { overlay ->
                                    val proj =
                                        ProjectManager.getProject()
                                    val pos =
                                        overlay.getCurrentPosition()
                                    val dur =
                                        proj.overlayDurationMs
                                            .toInt()
                                            .coerceAtLeast(
                                                overlay.getDuration()
                                            )

                                    val intentionalPause =
                                        when {
                                            overlay.isPlaying() ->
                                                false

                                            deviceLocked &&
                                                !proj.cueLockedPingPong &&
                                                proj.cueLockedMode ==
                                                CueMode.PAUSE &&
                                                pos >=
                                                proj.cueLockedMs ->
                                                true

                                            !deviceLocked &&
                                                !proj.cueUnlockedPingPong &&
                                                proj.cueUnlockedMode ==
                                                CueMode.PAUSE &&
                                                dur > 0 &&
                                                pos >=
                                                (dur - 250)
                                                    .coerceAtLeast(
                                                        proj.cueUnlockedMs
                                                    ) ->
                                                true

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
                                                this@GLWallpaperService,
                                                "Overlay watchdog: recoverPlayback pos=$pos"
                                            )
                                            overlay.recoverPlayback(
                                                pos.coerceAtLeast(0)
                                            )
                                            overlayStalledFrames = 0
                                        } else if (
                                            overlayStalledFrames == 20
                                        ) {
                                            overlay.ensurePlaying(pos)
                                        }
                                    } else {
                                        overlayStalledFrames = 0
                                    }
                                }

                            // --------------------------------
                            // Dibujar
                            // --------------------------------

                            renderer?.drawFrame()

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
                            "RenderThread detenido " +
                                "(generation=$myGeneration)"
                        )

                    } catch (e: Exception) {

                        FileLogger.logException(
                            this@GLWallpaperService,
                            "RenderThread generation=$myGeneration",
                            e
                        )

                    } finally {

                        // ========================================
                        // INVALIDAR ESTE THREAD
                        // ========================================

                        threadRunning.set(false)

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

                                // Con dispositivo bloqueado: al volver a
                                // visible siempre se reinicia desde 0.
                                // No persistir pausa en el cue locked.
                                if (deviceLocked) {
                                    savedOverlayPaused = false
                                    savedOverlayPosition = 0
                                } else {
                                    val unlockedPause =
                                        !project.cueUnlockedPingPong &&
                                            project.cueUnlockedMode ==
                                            CueMode.PAUSE &&
                                            !overlay.isPlaying()

                                    savedOverlayPaused =
                                        overlay.isIntentionallyPaused() ||
                                            unlockedPause

                                    savedOverlayPosition =
                                        when {
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
                                    this@GLWallpaperService,
                                    "Overlay guardado pos=${savedOverlayPosition}ms paused=$savedOverlayPaused locked=$deviceLocked"
                                )
                            }

                        // ========================================
                        // LIMPIAR SURFACE
                        // ========================================

                        if (
                            myGeneration != renderGeneration
                        ) {

                            FileLogger.log(
                                this@GLWallpaperService,
                                "RenderThread $myGeneration -> limpiando Surface antes de release"
                            )

                            renderer?.clearSurface()
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
                            "RenderThread finalizado " +
                                "(generation=$myGeneration)"
                        )
                    }

                }

            renderThread!!.start()
        }

        // ============================================
        // FUNCIÓN AUXILIAR - GENERACIÓN ACTIVA
        // ============================================

        private fun isGenerationActive(
            generation: Long,
            threadRunning:
                java.util.concurrent.atomic.AtomicBoolean
        ): Boolean {

            return (
                threadRunning.get() &&
                generation == renderGeneration &&
                visible
            )
        }

        // ============================================
        // STOP RENDERING
        // ============================================

        private fun stopRendering() {

            FileLogger.log(
                this@GLWallpaperService,
                "RenderThread deteniendo"
            )

            /*
             * ============================================
             * INVALIDAR GENERACIÓN
             * ============================================
             *
             * Incrementamos la generación antes de detener
             * el thread.
             *
             * De esta manera cualquier callback perteneciente
             * al RenderThread anterior queda automáticamente
             * invalidado.
             */

            renderGeneration++

            val thread =
                renderThread

            /*
             * Si no existe un thread activo, no hay nada
             * que detener.
             */

            if (thread == null) {

                FileLogger.log(
                    this@GLWallpaperService,
                    "stopRendering(): no hay RenderThread activo"
                )

                return
            }

            /*
             * El RenderThread comprueba su propia generación
             * mediante isGenerationActive().
             *
             * Además lo interrumpimos para que salga
             * inmediatamente de Thread.sleep().
             */

            thread.interrupt()

            /*
             * Esperamos a que el thread termine completamente.
             *
             * Esto es importante:
             *
             * onSurfaceDestroyed()
             * onDestroy()
             * y onVisibilityChanged(false)
             *
             * no deben dejar un RenderThread antiguo vivo
             * mientras otro intenta comenzar.
             */

            try {

                thread.join()

            } catch (
                e: InterruptedException
            ) {

                FileLogger.log(
                    this@GLWallpaperService,
                    "stopRendering(): join() interrumpido"
                )

                Thread.currentThread().interrupt()
            }

            /*
             * ============================================
             * VERIFICACIÓN FINAL
             * ============================================
             */

            if (thread.isAlive) {

                FileLogger.log(
                    this@GLWallpaperService,
                    "ERROR: RenderThread sigue vivo después de join()"
                )

            } else {

                FileLogger.log(
                    this@GLWallpaperService,
                    "RenderThread terminado correctamente"
                )
            }

            /*
             * Solamente eliminamos la referencia si sigue
             * apuntando al mismo thread que acabamos de detener.
             *
             * Esto evita borrar accidentalmente la referencia
             * de una generación nueva.
             */

            if (
                renderThread === thread
            ) {

                renderThread = null
            }
        }

        // ============================================
        // AUDIO - APPLY
        // ============================================

        private fun applyAudioConfiguration() {

            val project =
                ProjectManager.getProject()

            // ========================================
            // AUDIO VIDEO DE FONDO
            // ========================================

            val bgLayer =
                project.layers.firstOrNull()

            if (bgLayer != null) {

                if (
                    bgLayer.soundPath.isNullOrEmpty()
                ) {

                    /*
                     * El propio video reproduce su audio.
                     */

                    videoPlayer?.setVolume(
                        bgLayer.soundVolume
                    )

                } else {

                    /*
                     * El audio externo tiene su propio
                     * reproductor.
                     *
                     * El volumen del video queda controlado
                     * por initializeAudioConfiguration().
                     */

                    bgSoundPlayer?.setVolume(
                        bgLayer.soundVolume
                    )
                }
            }

            // ========================================
            // AUDIO OVERLAY
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
            // VIDEO DE FONDO
            // ========================================

            val bgLayer =
                project.layers.firstOrNull()

            if (bgLayer != null) {

                if (
                    bgLayer.soundPath.isNullOrEmpty()
                ) {

                    /*
                     * No existe audio externo.
                     *
                     * El MediaPlayer del video conserva
                     * su volumen configurado.
                     */

                    videoPlayer?.setVolume(
                        bgLayer.soundVolume
                    )

                    bgSoundPlayer?.stop()

                } else {

                    /*
                     * Existe audio externo.
                     *
                     * Silenciamos el audio interno del video
                     * y utilizamos WallpaperSoundPlayer.
                     */

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

                /*
                 * El overlay utiliza el audio contenido
                 * dentro de su propio MediaPlayer.
                 */

                renderer
                    ?.getVideoOverlayRenderer()
                    ?.setVolume(
                        overlay.soundVolume
                    )

                overlaySoundPlayer?.stop()

            } else {

                /*
                 * Audio externo para el overlay.
                 *
                 * Silenciamos el audio interno del video.
                 */

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
