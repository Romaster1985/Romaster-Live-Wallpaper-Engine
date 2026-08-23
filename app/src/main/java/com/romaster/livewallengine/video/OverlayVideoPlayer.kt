package com.romaster.livewallengine.video

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.view.Surface

import com.romaster.livewallengine.R
import com.romaster.livewallengine.debug.FileLogger
import com.romaster.livewallengine.storage.AppDirectories
import com.romaster.livewallengine.storage.StorageManager
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

enum class OverlayPlaybackDirection {
    FORWARD,
    REVERSE
}

/** Origen del clip de reversa activo (define a dónde volver al terminar). */
enum class ReverseClipKind {
    NONE,
    LOCKED,
    UNLOCKED
}

/**
 * MediaPlayer del overlay.
 *
 * Importante:
 * - Todas las llamadas al MediaPlayer van por [playerThread] (no es thread-safe).
 * - El hilo GL solo lee valores cacheados (posición, tamaño, flags).
 * - seekTo usa SEEK_CLOSEST (API 26+) para acercarse al frame real del cue.
 */
class OverlayVideoPlayer(

    private val context: Context

) {

    private var mediaPlayer: MediaPlayer? = null

    private var prepared = false

    private var onCompletion: (() -> Unit)? = null

    private var surface: Surface? = null

    var surfaceProvider: (() -> Surface)? = null

    private var currentVolume = 1f

    private val mainHandler = Handler(Looper.getMainLooper())

    private val playerThread = HandlerThread("OverlayVideoPlayer").also { it.start() }
    private val playerHandler = Handler(playerThread.looper)

    private val switching = AtomicBoolean(false)

    @Volatile
    private var pendingSeekMs: Int = -1

    /** true si debemos esperar onSeekComplete antes de start() */
    @Volatile
    private var waitSeekBeforeStart: Boolean = false

    @Volatile
    private var cachedVideoWidth: Int = 0

    @Volatile
    private var cachedVideoHeight: Int = 0

    @Volatile
    private var cachedPositionMs: Int = 0

    @Volatile
    private var cachedDurationMs: Int = 0

    @Volatile
    private var cachedIsPlaying: Boolean = false

    @Volatile
    var direction: OverlayPlaybackDirection =
        OverlayPlaybackDirection.FORWARD
        private set

    @Volatile
    var isPlayingReverseClip: Boolean = false
        private set

    @Volatile
    var reverseClipKind: ReverseClipKind = ReverseClipKind.NONE
        private set

    /** true si pause() fue a propósito (modo PAUSE de cues). */
    @Volatile
    var intentionallyPaused: Boolean = false
        private set

    /**
     * Tras seek en mitad/inicio: start() breve → pause()
     * (empuja un frame; un frame de avance cerca del inicio no molesta).
     */
    @Volatile
    private var pauseAfterFrame: Boolean = false

    /**
     * Restaurar pausa al final del video: seek un poco antes del fin,
     * play hasta onCompletion y quedarse en el último frame
     * (sin invocar el listener de cues/loop).
     */
    @Volatile
    private var pauseAtEnd: Boolean = false

    /** Ignorar el próximo onCompletion de la app (solo refresco de frame final). */
    @Volatile
    private var suppressCompletionOnce: Boolean = false

    /** Callback en main tras seek+start (anti-flash al lock). */
    @Volatile
    private var onReadyAfterSeek: (() -> Unit)? = null

    private val positionTicker = object : Runnable {
        override fun run() {
            try {
                val mp = mediaPlayer
                if (mp != null) {
                    try {
                        cachedPositionMs = mp.currentPosition
                        val d = mp.duration
                        if (d > 0) cachedDurationMs = d
                        cachedIsPlaying = mp.isPlaying
                    } catch (_: Exception) {
                    }
                }
            } finally {
                if (prepared) {
                    playerHandler.postDelayed(this, 16L)
                }
            }
        }
    }

    fun initialize(
        surface: Surface
    ) {
        this.surface = surface

        if (prepared) {
            return
        }

        prepared = true
        runOnPlayerThread {
            openOriginal(looping = true, autoPlay = false)
            playerHandler.removeCallbacks(positionTicker)
            playerHandler.post(positionTicker)
        }
    }

    private fun runOnPlayerThread(block: () -> Unit) {
        if (Looper.myLooper() == playerThread.looper) {
            block()
        } else {
            playerHandler.post(block)
        }
    }

    private fun installListeners(player: MediaPlayer) {
        player.setOnPreparedListener { mp ->
            FileLogger.log(
                context,
                "MediaPlayer -> onPrepared() reverseClip=$isPlayingReverseClip pendingSeek=$pendingSeekMs"
            )
            try {
                val w = mp.videoWidth
                val h = mp.videoHeight
                if (w > 0 && h > 0) {
                    cachedVideoWidth = w
                    cachedVideoHeight = h
                }
                val d = mp.duration
                if (d > 0) cachedDurationMs = d

                mp.setVolume(currentVolume, currentVolume)

                val seek = pendingSeekMs
                val hasSeek = seek >= 0
                pendingSeekMs = -1

                if (hasSeek) {
                    waitSeekBeforeStart = true
                    seekInternal(mp, seek)
                    // start() se dispara en onSeekComplete
                } else {
                    waitSeekBeforeStart = false
                    if (!mp.isPlaying) {
                        mp.start()
                        cachedIsPlaying = true
                    }
                    switching.set(false)
                }
            } catch (e: Exception) {
                FileLogger.log(
                    context,
                    "MediaPlayer onPrepared error: ${e.message}"
                )
                waitSeekBeforeStart = false
                switching.set(false)
            }
        }

        player.setOnSeekCompleteListener { mp ->
            FileLogger.log(
                context,
                "MediaPlayer -> onSeekComplete() pos=${try { mp.currentPosition } catch (_: Exception) { -1 }} pauseAfter=$pauseAfterFrame pauseAtEnd=$pauseAtEnd"
            )
            try {
                cachedPositionMs = mp.currentPosition
            } catch (_: Exception) {
            }

            if (waitSeekBeforeStart) {
                waitSeekBeforeStart = false
                try {
                    if (!mp.isPlaying) {
                        mp.start()
                    }
                    when {
                        pauseAtEnd -> {
                            intentionallyPaused = true
                            cachedIsPlaying = true
                            FileLogger.log(
                                context,
                                "onSeekComplete: play hasta EOF para frame final"
                            )
                        }
                        pauseAfterFrame -> {
                            // Un solo frame al Surface; pause inmediato
                            // (sin sleep: evitaba el corrimiento progresivo)
                            try {
                                mp.pause()
                            } catch (_: Exception) {
                            }
                            intentionallyPaused = true
                            cachedIsPlaying = false
                            pauseAfterFrame = false
                            try {
                                cachedPositionMs = mp.currentPosition
                            } catch (_: Exception) {
                            }
                            FileLogger.log(
                                context,
                                "onSeekComplete: frame mid + pause() pos=$cachedPositionMs"
                            )
                        }
                        else -> {
                            intentionallyPaused = false
                            cachedIsPlaying = true
                        }
                    }
                    // Aviso a la UI/render (p. ej. revelar overlay tras lock)
                    val ready = onReadyAfterSeek
                    onReadyAfterSeek = null
                    if (ready != null) {
                        mainHandler.post {
                            try {
                                ready.invoke()
                            } catch (_: Exception) {
                            }
                        }
                    }
                } catch (e: Exception) {
                    FileLogger.log(
                        context,
                        "start after seek error: ${e.message}"
                    )
                }
                switching.set(false)
            }
        }

        player.setOnCompletionListener {
            FileLogger.log(
                context,
                "MediaPlayer -> onCompletion() reverseClip=$isPlayingReverseClip suppress=$suppressCompletionOnce pauseAtEnd=$pauseAtEnd"
            )
            cachedIsPlaying = false

            // Refresco del frame final al volver a visible:
            // no disparar cues / loop / ping-pong.
            if (suppressCompletionOnce || pauseAtEnd) {
                suppressCompletionOnce = false
                pauseAtEnd = false
                intentionallyPaused = true
                cachedIsPlaying = false
                try {
                    // Asegurar último frame en la Surface (best-effort)
                    val d = mediaPlayer?.duration ?: cachedDurationMs
                    if (d > 0) {
                        cachedPositionMs = d
                    }
                } catch (_: Exception) {
                }
                FileLogger.log(
                    context,
                    "onCompletion: frame final en pausa (sin listener de cues)"
                )
                return@setOnCompletionListener
            }

            val listener = onCompletion
            mainHandler.post {
                try {
                    listener?.invoke()
                } catch (e: Exception) {
                    FileLogger.log(
                        context,
                        "onCompletion listener error: ${e.message}"
                    )
                }
            }
        }

        player.setOnVideoSizeChangedListener { _, width, height ->
            if (width > 0 && height > 0) {
                cachedVideoWidth = width
                cachedVideoHeight = height
            }
            FileLogger.log(
                context,
                "MediaPlayer -> VideoSize ${width}x${height}"
            )
        }

        player.setOnInfoListener { _, what, extra ->
            FileLogger.log(
                context,
                "MediaPlayer -> onInfo what=$what extra=$extra"
            )
            false
        }

        player.setOnErrorListener { _, what, extra ->
            FileLogger.log(
                context,
                "MediaPlayer -> ERROR what=$what extra=$extra"
            )
            waitSeekBeforeStart = false
            switching.set(false)
            cachedIsPlaying = false
            true
        }
    }

    /**
     * Seek lo más cercano posible al tiempo pedido.
     * SEEK_CLOSEST (API 26+) evita quedarse en el keyframe anterior.
     */
    private fun seekInternal(mp: MediaPlayer, positionMs: Int) {
        val pos = positionMs.coerceAtLeast(0)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                mp.seekTo(pos.toLong(), MediaPlayer.SEEK_CLOSEST)
            } else {
                @Suppress("DEPRECATION")
                mp.seekTo(pos)
            }
            FileLogger.log(
                context,
                "seekInternal($pos) SEEK_CLOSEST=${Build.VERSION.SDK_INT >= 26}"
            )
        } catch (e: Exception) {
            FileLogger.log(
                context,
                "seekInternal error: ${e.message}"
            )
            try {
                @Suppress("DEPRECATION")
                mp.seekTo(pos)
            } catch (_: Exception) {
            }
        }
    }

    private fun resolveSurface(forceRecreate: Boolean = false): Surface? {
        return try {
            if (forceRecreate) {
                surfaceProvider?.invoke()?.also { surface = it }
                    ?: surface
            } else {
                val current = surface
                if (current != null && current.isValid) {
                    current
                } else {
                    surfaceProvider?.invoke()?.also { surface = it }
                        ?: current
                }
            }
        } catch (e: Exception) {
            FileLogger.log(
                context,
                "resolveSurface error: ${e.message}"
            )
            surface
        }
    }

    private fun openFile(
        file: File?,
        looping: Boolean,
        asReverseClip: Boolean,
        autoPlay: Boolean
    ) {
        // Debe ejecutarse en playerThread
        isPlayingReverseClip = asReverseClip

        var player = mediaPlayer
        var usedReset = false

        if (player != null) {
            try {
                player.setOnCompletionListener(null)
                player.setOnPreparedListener(null)
                player.setOnErrorListener(null)
                player.setOnSeekCompleteListener(null)
                try {
                    if (player.isPlaying) player.stop()
                } catch (_: Exception) {
                }
                player.reset()
                usedReset = true
            } catch (e: Exception) {
                FileLogger.log(
                    context,
                    "reset() falló, recreando player: ${e.message}"
                )
                try {
                    player.release()
                } catch (_: Exception) {
                }
                mediaPlayer = null
                player = null
                usedReset = false
            }
        }

        if (player == null) {
            player = MediaPlayer()
            mediaPlayer = player
        }

        // Solo recrear Surface si no hay una válida (evitar abandonar
        // la SurfaceTexture recién creada en initialize()).
        val needNewSurface = !usedReset && (surface == null || surface?.isValid != true)
        val s = resolveSurface(forceRecreate = needNewSurface)
        if (s == null || !s.isValid) {
            FileLogger.log(
                context,
                "OverlayVideoPlayer openFile: Surface inválida"
            )
            switching.set(false)
            return
        }
        surface = s

        try {
            if (file != null && file.exists()) {
                FileLogger.log(
                    context,
                    "OverlayVideoPlayer open -> ${file.absolutePath} reverse=$asReverseClip reset=$usedReset size=${file.length()}"
                )
                player.setDataSource(context, Uri.fromFile(file))
            } else {
                FileLogger.log(
                    context,
                    "OverlayVideoPlayer open -> test.mp4 (fallback)"
                )
                val afd = context.resources.openRawResourceFd(R.raw.test)
                player.setDataSource(
                    afd.fileDescriptor,
                    afd.startOffset,
                    afd.length
                )
                afd.close()
            }

            player.setSurface(s)
            player.isLooping = looping
            player.setVolume(currentVolume, currentVolume)
            installListeners(player)

            if (autoPlay) {
                player.prepareAsync()
            } else {
                player.prepare()
                val w = player.videoWidth
                val h = player.videoHeight
                if (w > 0 && h > 0) {
                    cachedVideoWidth = w
                    cachedVideoHeight = h
                }
                val d = player.duration
                if (d > 0) cachedDurationMs = d
                cachedPositionMs = player.currentPosition
            }
        } catch (e: Exception) {
            FileLogger.log(
                context,
                "OverlayVideoPlayer openFile ERROR: ${e.message}"
            )
            try {
                player.setOnCompletionListener(null)
                player.setOnPreparedListener(null)
                player.release()
            } catch (_: Exception) {
            }
            mediaPlayer = null
            isPlayingReverseClip = false
            switching.set(false)
        }
    }

    private fun openOriginal(looping: Boolean, autoPlay: Boolean) {
        val project = StorageManager.loadProject(context)
        val fileName = project?.overlayVideo
        val file = if (fileName != null) {
            VideoStorage.getVideoFile(context, fileName)
        } else {
            null
        }
        openFile(file, looping, asReverseClip = false, autoPlay = autoPlay)
        direction = OverlayPlaybackDirection.FORWARD
        reverseClipKind = ReverseClipKind.NONE
    }

    private fun openReverseFile(
        fileName: String,
        autoPlay: Boolean,
        kind: ReverseClipKind
    ) {
        // Nombres fijos en videos/ (misma carpeta que overlay_video.mp4)
        val file = VideoStorage.getVideoFile(context, fileName)
        if (!file.exists() || file.length() == 0L) {
            FileLogger.log(
                context,
                "openReverseFile: no existe o vacío -> ${file.absolutePath}"
            )
            switching.set(false)
            return
        }
        openFile(file, looping = false, asReverseClip = true, autoPlay = autoPlay)
        direction = OverlayPlaybackDirection.REVERSE
        reverseClipKind = kind
    }

    fun play() {
        intentionallyPaused = false
        runOnPlayerThread {
            mediaPlayer?.let {
                try {
                    if (!it.isPlaying) {
                        FileLogger.log(context, "MediaPlayer.start()")
                        it.start()
                        cachedIsPlaying = true
                    }
                } catch (e: Exception) {
                    FileLogger.log(
                        context,
                        "MediaPlayer.start() error: ${e.message}"
                    )
                }
            }
        }
    }

    /**
     * Si el player debería estar reproduciendo pero no lo está,
     * intenta start(); si no hay player o falla, reabre el original.
     */
    fun ensurePlaying(preferredSeekMs: Int = -1) {
        if (intentionallyPaused) {
            return
        }
        runOnPlayerThread {
            if (intentionallyPaused) return@runOnPlayerThread
            try {
                val mp = mediaPlayer
                if (mp != null) {
                    try {
                        if (!mp.isPlaying) {
                            FileLogger.log(context, "ensurePlaying: start()")
                            mp.start()
                            cachedIsPlaying = true
                        }
                        return@runOnPlayerThread
                    } catch (e: Exception) {
                        FileLogger.log(
                            context,
                            "ensurePlaying start falló: ${e.message}"
                        )
                    }
                }

                FileLogger.log(context, "ensurePlaying: recoverPlayback")
                recoverPlaybackInternal(preferredSeekMs)
            } catch (e: Exception) {
                FileLogger.log(
                    context,
                    "ensurePlaying ERROR: ${e.message}"
                )
            }
        }
    }

    /**
     * Reabre el video original (no reverse) y busca [seekMs].
     * Usar tras error o freeze al volver a visible.
     */
    fun recoverPlayback(seekMs: Int = 0) {
        runOnPlayerThread {
            recoverPlaybackInternal(seekMs)
        }
    }

    private fun recoverPlaybackInternal(seekMs: Int) {
        if (intentionallyPaused) {
            FileLogger.log(context, "recoverPlayback ignorado: intentionallyPaused")
            return
        }
        try {
            switching.set(false)
            waitSeekBeforeStart = false
            isPlayingReverseClip = false
            reverseClipKind = ReverseClipKind.NONE
            direction = OverlayPlaybackDirection.FORWARD
            pendingSeekMs = seekMs.coerceAtLeast(0)
            openOriginal(looping = false, autoPlay = true)
            FileLogger.log(
                context,
                "recoverPlayback seek=$seekMs"
            )
        } catch (e: Exception) {
            FileLogger.log(
                context,
                "recoverPlayback ERROR: ${e.message}"
            )
            switching.set(false)
        }
    }

    fun pause() {
        intentionallyPaused = true
        runOnPlayerThread {
            mediaPlayer?.let {
                try {
                    if (it.isPlaying) {
                        FileLogger.log(context, "MediaPlayer.pause()")
                        it.pause()
                    }
                    cachedIsPlaying = false
                    try {
                        val p = it.currentPosition
                        val d =
                            it.duration.takeIf { x -> x > 0 } ?: cachedDurationMs
                        // Cerca del final: fijar duration (evita 0 tras EOF)
                        cachedPositionMs =
                            if (d > 0 && p >= (d - 500).coerceAtLeast(0)) d
                            else p.coerceAtLeast(0)
                        if (d > 0) cachedDurationMs = d
                    } catch (_: Exception) {
                    }
                } catch (e: Exception) {
                    FileLogger.log(
                        context,
                        "MediaPlayer.pause() error: ${e.message}"
                    )
                }
            }
        }
    }

    fun seekTo(position: Int) {
        runOnPlayerThread {
            FileLogger.log(context, "MediaPlayer.seekTo($position)")
            mediaPlayer?.let { seekInternal(it, position) }
        }
    }

    /**
     * Restaura el overlay en [positionMs].
     *
     * - paused=false: seek + play normal
     * - paused=true cerca del **final**: seek un poco antes del EOF → play
     *   hasta onCompletion (último frame real, sin saltar al inicio)
     * - paused=true en **inicio/medio**: seek → start breve → pause
     *   (un frame de avance al inicio no se nota)
     */
    fun restoreAt(
        positionMs: Int,
        paused: Boolean,
        onReady: (() -> Unit)? = null
    ) {
        if (onReady != null) {
            onReadyAfterSeek = onReady
        }
        runOnPlayerThread {
            val dur = try {
                mediaPlayer?.duration?.takeIf { it > 0 } ?: cachedDurationMs
            } catch (_: Exception) {
                cachedDurationMs
            }
            val pos = positionMs.coerceAtLeast(0)

            pauseAfterFrame = false
            pauseAtEnd = false
            suppressCompletionOnce = false

            if (!paused) {
                intentionallyPaused = false
                FileLogger.log(context, "restoreAt play pos=$pos")
                val mp = mediaPlayer
                if (mp == null) {
                    pendingSeekMs = pos
                    waitSeekBeforeStart = true
                    openOriginal(looping = false, autoPlay = true)
                    return@runOnPlayerThread
                }
                try {
                    waitSeekBeforeStart = true
                    seekInternal(mp, pos)
                } catch (e: Exception) {
                    FileLogger.log(context, "restoreAt ERROR: ${e.message}")
                    waitSeekBeforeStart = false
                }
                return@runOnPlayerThread
            }

            // --- Pausado ---
            intentionallyPaused = true
            // Fin de video: margen amplio; si pos es 0 tras onCompletion
            // de algunos devices, el caller debe pasar duration.
            val nearEnd =
                dur > 0 && (
                    pos >= (dur - 500).coerceAtLeast(0) ||
                        pos >= dur
                )

            if (nearEnd) {
                // Suficiente margen antes del EOF para no pasarse al inicio
                val seekPos = (dur - 350).coerceAtLeast(0)
                pauseAtEnd = true
                suppressCompletionOnce = true
                FileLogger.log(
                    context,
                    "restoreAt pause@END seek=$seekPos (dur=$dur)"
                )
                val mp = mediaPlayer
                if (mp == null) {
                    pendingSeekMs = seekPos
                    waitSeekBeforeStart = true
                    openOriginal(looping = false, autoPlay = true)
                    return@runOnPlayerThread
                }
                try {
                    waitSeekBeforeStart = true
                    seekInternal(mp, seekPos)
                } catch (e: Exception) {
                    FileLogger.log(context, "restoreAt END ERROR: ${e.message}")
                    waitSeekBeforeStart = false
                    pauseAtEnd = false
                    suppressCompletionOnce = false
                }
            } else {
                // Inicio o medio: play-pause corto
                pauseAfterFrame = true
                FileLogger.log(context, "restoreAt pause@mid/start pos=$pos")
                val mp = mediaPlayer
                if (mp == null) {
                    pendingSeekMs = pos
                    waitSeekBeforeStart = true
                    openOriginal(looping = false, autoPlay = true)
                    return@runOnPlayerThread
                }
                try {
                    waitSeekBeforeStart = true
                    seekInternal(mp, pos)
                } catch (e: Exception) {
                    FileLogger.log(context, "restoreAt mid ERROR: ${e.message}")
                    waitSeekBeforeStart = false
                    pauseAfterFrame = false
                }
            }
        }
    }

    /**
     * Seek + play y avisa [onReady] en main cuando el decoder ya arrancó
     * (útil para revelar el overlay tras ocultarlo en un lock).
     */
    fun setDirection(
        newDirection: OverlayPlaybackDirection,
        reverseFileName: String? = null,
        startPositionMs: Int = 0,
        reverseKind: ReverseClipKind = ReverseClipKind.NONE,
        onReady: (() -> Unit)? = null
    ) {
        if (onReady != null) {
            onReadyAfterSeek = onReady
        }
        // Early-out solo si no hay onReady (si hay, hay que esperar seek+start)
        if (
            onReady == null &&
            newDirection == OverlayPlaybackDirection.FORWARD &&
            direction == OverlayPlaybackDirection.FORWARD &&
            !isPlayingReverseClip
        ) {
            if (startPositionMs >= 0) {
                pendingSeekMs = -1
                runOnPlayerThread {
                    mediaPlayer?.let {
                        waitSeekBeforeStart = false
                        seekInternal(it, startPositionMs)
                    }
                }
            }
            play()
            return
        }

        if (
            newDirection == OverlayPlaybackDirection.REVERSE &&
            isPlayingReverseClip
        ) {
            runOnPlayerThread {
                mediaPlayer?.let {
                    waitSeekBeforeStart = false
                    seekInternal(it, 0)
                }
            }
            play()
            return
        }

        if (!switching.compareAndSet(false, true)) {
            FileLogger.log(
                context,
                "setDirection ignorado: ya hay un cambio en curso"
            )
            return
        }

        FileLogger.log(
            context,
            "OverlayVideoPlayer.setDirection($newDirection) file=$reverseFileName start=$startPositionMs"
        )

        intentionallyPaused = false

        // Incluye 0 (seek al inicio al bloquear / fin de reverse locked)
        pendingSeekMs = startPositionMs.coerceAtLeast(0)

        runOnPlayerThread {
            try {
                when (newDirection) {
                    OverlayPlaybackDirection.FORWARD -> {
                        openOriginal(looping = false, autoPlay = true)
                    }
                    OverlayPlaybackDirection.REVERSE -> {
                        if (reverseFileName.isNullOrBlank()) {
                            FileLogger.log(
                                context,
                                "setDirection REVERSE sin archivo — ignorado"
                            )
                            switching.set(false)
                            return@runOnPlayerThread
                        }
                        // Reverse siempre desde el inicio del clip invertido
                        pendingSeekMs = -1
                        val kind =
                            if (reverseKind != ReverseClipKind.NONE) reverseKind
                            else ReverseClipKind.LOCKED
                        openReverseFile(
                            reverseFileName,
                            autoPlay = true,
                            kind = kind
                        )
                    }
                }

                playerHandler.postDelayed({
                    if (switching.get()) {
                        FileLogger.log(
                            context,
                            "setDirection: timeout liberando switching"
                        )
                        waitSeekBeforeStart = false
                        switching.set(false)
                    }
                }, 3_000)
            } catch (e: Exception) {
                FileLogger.log(
                    context,
                    "setDirection ERROR: ${e.message}"
                )
                switching.set(false)
            }
        }
    }

    fun getCurrentPosition(): Int = cachedPositionMs

    fun getDuration(): Int = cachedDurationMs

    fun getVideoWidth(): Int = cachedVideoWidth

    fun getVideoHeight(): Int = cachedVideoHeight

    fun isPlaying(): Boolean = cachedIsPlaying

    fun setLooping(looping: Boolean) {
        runOnPlayerThread {
            mediaPlayer?.let {
                try {
                    if (it.isLooping != looping) {
                        FileLogger.log(
                            context,
                            "MediaPlayer.setLooping($looping)"
                        )
                        it.isLooping = looping
                    }
                } catch (_: Exception) {
                }
            }
        }
    }

    fun setOnCompletionListener(listener: () -> Unit) {
        onCompletion = listener
    }

    fun setVolume(volume: Float) {
        currentVolume = volume.coerceIn(0f, 1f)
        runOnPlayerThread {
            try {
                mediaPlayer?.setVolume(currentVolume, currentVolume)
            } catch (_: Exception) {
            }
        }
    }

    fun release() {
        FileLogger.log(context, "MediaPlayer.release()")
        prepared = false
        mainHandler.removeCallbacksAndMessages(null)
        playerHandler.removeCallbacksAndMessages(null)

        // Liberar en el hilo del player y esperar un momento
        val latch = java.util.concurrent.CountDownLatch(1)
        playerHandler.post {
            try {
                mediaPlayer?.setOnCompletionListener(null)
                mediaPlayer?.setOnPreparedListener(null)
                mediaPlayer?.setOnErrorListener(null)
                mediaPlayer?.setOnSeekCompleteListener(null)
                try {
                    mediaPlayer?.setSurface(null)
                } catch (_: Exception) {
                }
                try {
                    if (mediaPlayer?.isPlaying == true) mediaPlayer?.stop()
                } catch (_: Exception) {
                }
                mediaPlayer?.reset()
                mediaPlayer?.release()
            } catch (_: Exception) {
            }
            mediaPlayer = null
            latch.countDown()
        }
        try {
            latch.await(500, java.util.concurrent.TimeUnit.MILLISECONDS)
        } catch (_: Exception) {
        }

        onCompletion = null
        surface = null
        surfaceProvider = null
        isPlayingReverseClip = false
        reverseClipKind = ReverseClipKind.NONE
        intentionallyPaused = false
        pauseAfterFrame = false
        pauseAtEnd = false
        suppressCompletionOnce = false
        onReadyAfterSeek = null
        direction = OverlayPlaybackDirection.FORWARD
        switching.set(false)
        pendingSeekMs = -1
        waitSeekBeforeStart = false
        cachedVideoWidth = 0
        cachedVideoHeight = 0
        cachedPositionMs = 0
        cachedDurationMs = 0
        cachedIsPlaying = false

        try {
            playerThread.quitSafely()
        } catch (_: Exception) {
        }
    }

    fun reload(surface: Surface) {
        // release mata el thread; hay que recrear el player thread si se usa reload
        // En la práctica el renderer hace release + initialize nuevo en instancia nueva
        // o reinicializa. Aquí solo re-init si el thread sigue vivo.
        this.surface = surface
        prepared = false
        if (!playerThread.isAlive) {
            // No se puede reiniciar HandlerThread fácilmente; el renderer debería
            // crear un OverlayVideoPlayer nuevo. Fallback:
            FileLogger.log(context, "reload: playerThread muerto — no-op")
            return
        }
        initialize(surface)
        play()
    }

}
