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
import android.graphics.Bitmap
import android.opengl.GLES20
import android.os.SystemClock
import com.romaster.livewallengine.debug.FileLogger
import com.romaster.livewallengine.model.ClockSettings
import com.romaster.livewallengine.project.ProjectManager
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Textura del reloj a pantalla completa.
 *
 * El bitmap se genera en un hilo worker (CPU/Canvas). El hilo GL solo:
 *  - toma el bitmap listo
 *  - lo sube a textura
 *  - dibuja el quad
 *
 * Así el render de video no se bloquea con Canvas/bordes/texturas del reloj.
 */
class GLOverlayRenderer {

    private lateinit var appContext: Context
    private lateinit var bitmapGenerator: ClockBitmapGenerator
    private lateinit var texture: GLTexture
    private lateinit var quadRenderer: GLQuadRenderer
    private var backdropBlur: ClockBackdropBlur? = null

    private var worker: ExecutorService? = null
    private val lock = Any()

    /** Frame listo para subir en el hilo GL (producido por el worker). */
    private var pendingFrame: ClockFrame? = null
    private var pendingSettingsKey: String = ""
    private var pendingSecondBucket: Long = -1L

    private var destLeftNdc = -1f
    private var destTopNdc = 1f
    private var destRightNdc = 1f
    private var destBottomNdc = -1f

    private var destLeftPx = 0f
    private var destTopPx = 0f
    private var destRightPx = 1f
    private var destBottomPx = 1f
    private var frameScreenW = 1
    private var frameScreenH = 1

    private var buildInFlight = false
    private var dirty = false
    private var reqWidth = 0
    private var reqHeight = 0
    private var reqSettingsKey: String = ""
    private var reqSecondBucket: Long = -1L

    private val fadeDurationMs: Long
        get() = ProjectManager.getProject().clockFadeDurationMs

    private var fadeStartTime = 0L
    private var clockAlpha = 1f
    private var softStartOverrideMs: Long? = null

    /**
     * Si true, el soft start está pedido pero aún no hay textura lista:
     * se arranca el timer recién al subir el primer bitmap.
     */
    private var awaitTextureForSoftStart = false

    /**
     * Soft start con desenfoque: no arrancar el fade hasta tener blur listo
     * (evita reloj “a medias” reconstruyéndose durante el fade).
     */
    private var awaitBackdropForSoftStart = false

    /** true = reloj oculto a propósito (p.ej. no habilitado en pantalla de bloqueo) */
    private var forceHidden = false

    /** Delay Start del reloj (timestamp hasta el cual alpha=0). */
    private var clockDelayUntil = 0L

    /** true cuando BG/OL/Pics ya terminaron soft start (captura de blur válida). */
    @Volatile private var backgroundsSettled = true

    private var lastSettingsKey: String = ""
    private var lastSecondBucket: Long = -1L
    private var hasTexture = false

    /** Captura de fondo para blur (bajo demanda + refresco suave) */
    private var lastBackdropCaptureMs: Long = 0L
    private var backdropCaptureInFlight = AtomicBoolean(false)
    private var pendingBackdropBitmap: Bitmap? = null
    private var lastDefocusLevel: Int = -1
    private var backdropReady = false

    /** Worker aparte del del reloj para no competir con Canvas del clock */
    private var blurWorker: ExecutorService? = null

    private val released = AtomicBoolean(false)

    companion object {
        /** Refresco del blur detrás del video (ms). No cada frame ni cada 1s. */
        private const val BACKDROP_REFRESH_MS = 2800L
    }

    fun initialize(context: Context) {
        appContext = context.applicationContext
        bitmapGenerator = ClockBitmapGenerator(appContext)
        texture = GLTexture()
        quadRenderer = GLQuadRenderer()
        quadRenderer.initialize()
        backdropBlur = ClockBackdropBlur()
        backdropBlur?.initialize()
        clockAlpha = 1f
        hasTexture = false
        released.set(false)
        backdropReady = false
        awaitBackdropForSoftStart = false
        worker = Executors.newSingleThreadExecutor { r ->
            Thread(r, "ClockBitmapWorker").apply {
                priority = Thread.NORM_PRIORITY - 1
            }
        }
        blurWorker = Executors.newSingleThreadExecutor { r ->
            Thread(r, "ClockBlurWorker").apply {
                priority = Thread.NORM_PRIORITY - 2
            }
        }
    }

    /**
     * @param layoutW/layoutH tamaño lógico del wallpaper (posicionamiento del reloj)
     * @param fbW/fbH tamaño real del framebuffer OpenGL (glReadPixels)
     */
    fun draw(layoutW: Int, layoutH: Int, fbW: Int = layoutW, fbH: Int = layoutH) {
        if (layoutW <= 0 || layoutH <= 0 || released.get()) return

        // 1) Consumir bitmap listo en el hilo GL (upload rápido)
        consumePendingTexture()

        val settings = ProjectManager.getProject().clock
        val secondBucket = System.currentTimeMillis() / 1000L
        val settingsKey = buildSettingsKey(settings)
        val settingsChanged = settingsKey != lastSettingsKey
        val timeChanged = secondBucket != lastSecondBucket

        // 2) Pedir regeneración en background si hace falta
        if (!hasTexture || settingsChanged || timeChanged) {
            requestBuild(layoutW, layoutH, settingsKey, secondBucket)
        }

        // 3) Subir backdrop difuminado si el worker lo terminó
        consumePendingBackdrop()

        val defocusLevel = settings.crystalDefocusLevel.coerceIn(0, 3)
        if (!settings.crystalMode || defocusLevel == 0) {
            if (lastDefocusLevel != 0) {
                lastDefocusLevel = 0
                lastBackdropCaptureMs = 0L
                backdropReady = false
                awaitBackdropForSoftStart = false
                backdropBlur?.clear()
                FileLogger.log(appContext, "CrystalBlur: OFF")
            }
        } else if (defocusLevel != lastDefocusLevel) {
            FileLogger.log(appContext, "CrystalBlur: level -> $defocusLevel")
            lastDefocusLevel = defocusLevel
            lastBackdropCaptureMs = 0L
            // No borramos el blur anterior: se reemplaza cuando el nuevo esté listo
        }

        // 4) Captura: primera vez al instante; luego solo cada BACKDROP_REFRESH_MS
        val needBackdrop = settings.crystalMode && defocusLevel > 0 && hasTexture
        val hasBackdrop = backdropReady && backdropBlur?.hasContent() == true
        if (needBackdrop && !backdropCaptureInFlight.get() && backgroundsSettled) {
            val now = SystemClock.elapsedRealtime()
            val dueFirst = !hasBackdrop
            val dueRefresh = hasBackdrop && (now - lastBackdropCaptureMs) >= BACKDROP_REFRESH_MS
            if (dueFirst || dueRefresh) {
                captureBackdropRegion(fbW, fbH, defocusLevel)
            }
        }

        // 5) Oculto a propósito → no dibujar ni arrancar soft start
        if (forceHidden) {
            clockAlpha = 0f
            fadeStartTime = 0L
            awaitBackdropForSoftStart = false
            return
        }
        // Si hace falta blur y aún no está, no mostrar el reloj (evita soft start sin blur)
        if (needBackdrop && !hasBackdrop) {
            clockAlpha = 0f
            fadeStartTime = 0L
            awaitBackdropForSoftStart = true
            return
        }
        if (awaitBackdropForSoftStart && hasBackdrop) {
            clockAlpha = 0f
            awaitBackdropForSoftStart = false
            awaitTextureForSoftStart = false
            startClockFadeOrDelay()
        }
        // Delay Start del reloj en curso
        if (clockDelayUntil > 0L) {
            clockAlpha = 0f
            if (SystemClock.elapsedRealtime() >= clockDelayUntil) {
                clockDelayUntil = 0L
                fadeStartTime = SystemClock.elapsedRealtime()
            }
        }

        // 6) Blur opaco (forma del glifo) + reloj coloreado encima (con transparencia cristal)
        updateFade()
        if (clockAlpha > 0f && hasTexture) {
            if (needBackdrop && hasBackdrop) {
                try {
                    val gf = ClockBackdropBlur.glassFactorFromSettings(settings.crystalBlur)
                    if (timeChanged) {
                        FileLogger.log(
                            appContext,
                            "CrystalBlur: UNDERLAY+CLOCK glassFactor=$gf alpha=$clockAlpha level=$defocusLevel"
                        )
                    }
                    // 1) Base difuminada OPACA en la forma del texto
                    backdropBlur?.drawUnderlay(
                        maskTexId = texture.getTextureId(),
                        leftNdc = destLeftNdc,
                        topNdc = destTopNdc,
                        rightNdc = destRightNdc,
                        bottomNdc = destBottomNdc,
                        alpha = clockAlpha,
                        glassFactor = gf
                    )
                } catch (e: Exception) {
                    FileLogger.logException(appContext, "CrystalBlur.underlay", e)
                }
            } else if (needBackdrop && timeChanged) {
                FileLogger.log(
                    appContext,
                    "CrystalBlur: sin textura aún hasBackdrop=$hasBackdrop inFlight=${backdropCaptureInFlight.get()}"
                )
            }
            // 2) Siempre el reloj (color + transparencia del cristal)
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            quadRenderer.draw(
                texture,
                clockAlpha,
                destLeftNdc,
                destTopNdc,
                destRightNdc,
                destBottomNdc
            )
        }
    }

    private fun consumePendingBackdrop() {
        val bmp: Bitmap
        synchronized(lock) {
            bmp = pendingBackdropBitmap ?: return
            pendingBackdropBitmap = null
        }
        if (bmp.isRecycled) return
        try {
            backdropBlur?.upload(bmp)
            backdropReady = backdropBlur?.hasContent() == true
            FileLogger.log(
                appContext,
                "CrystalBlur: READY ${bmp.width}x${bmp.height}"
            )
            // Soft start esperaba el blur: arrancar fade ahora (solo si debe verse)
            if (awaitBackdropForSoftStart && backdropReady && !forceHidden) {
                clockAlpha = 0f
                awaitBackdropForSoftStart = false
                awaitTextureForSoftStart = false
                startClockFadeOrDelay()
            }
        } catch (e: Exception) {
            FileLogger.logException(appContext, "CrystalBlur.upload", e)
        } finally {
            bmp.recycle()
        }
    }

    /**
     * Lee solo el rectángulo del reloj del framebuffer actual (capas ya dibujadas
     * detrás del clock) y procesa el blur en el worker.
     */
    private fun captureBackdropRegion(fbW: Int, fbH: Int, level: Int) {
        if (fbW <= 0 || fbH <= 0) return
        if (frameScreenW <= 1 || frameScreenH <= 1) return

        val sx = fbW.toFloat() / frameScreenW.coerceAtLeast(1)
        val sy = fbH.toFloat() / frameScreenH.coerceAtLeast(1)
        val left = (destLeftPx * sx).toInt().coerceIn(0, fbW - 1)
        val top = (destTopPx * sy).toInt().coerceIn(0, fbH - 1)
        val right = (destRightPx * sx).toInt().coerceIn(left + 1, fbW)
        val bottom = (destBottomPx * sy).toInt().coerceIn(top + 1, fbH)
        var rw = right - left
        var rh = bottom - top
        rw = (rw / 4) * 4
        rh = (rh / 4) * 4
        if (rw < 16 || rh < 16) return
        if (!backdropCaptureInFlight.compareAndSet(false, true)) return

        lastBackdropCaptureMs = SystemClock.elapsedRealtime()

        try {
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
            GLES20.glPixelStorei(GLES20.GL_PACK_ALIGNMENT, 1)

            val glY = (fbH - top - rh).coerceIn(0, (fbH - rh).coerceAtLeast(0))
            val glX = left.coerceIn(0, (fbW - rw).coerceAtLeast(0))

            // Solo lectura GL en este hilo; conversión+blur en ClockBlurWorker
            val buf = ByteBuffer.allocateDirect(rw * rh * 4).order(ByteOrder.nativeOrder())
            while (GLES20.glGetError() != GLES20.GL_NO_ERROR) { /* drain */ }
            GLES20.glReadPixels(glX, glY, rw, rh, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buf)
            val err = GLES20.glGetError()
            if (err != GLES20.GL_NO_ERROR) {
                FileLogger.log(appContext, "CrystalBlur: glReadPixels ERROR 0x${Integer.toHexString(err)}")
                backdropCaptureInFlight.set(false)
                return
            }
            buf.rewind()

            val exec = blurWorker ?: worker
            if (exec == null || released.get()) {
                backdropCaptureInFlight.set(false)
                return
            }
            exec.execute {
                try {
                    val pixels = IntArray(rw * rh)
                    for (i in 0 until rw * rh) {
                        val r = buf.get().toInt() and 0xFF
                        val g = buf.get().toInt() and 0xFF
                        val b = buf.get().toInt() and 0xFF
                        val a = buf.get().toInt() and 0xFF
                        pixels[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
                    }
                    val raw = Bitmap.createBitmap(rw, rh, Bitmap.Config.ARGB_8888)
                    raw.setPixels(pixels, 0, rw, 0, 0, rw, rh)
                    val matrix = android.graphics.Matrix().apply { preScale(1f, -1f) }
                    val flipped = Bitmap.createBitmap(raw, 0, 0, rw, rh, matrix, false)
                    if (flipped !== raw) raw.recycle()

                    val t0 = SystemClock.elapsedRealtime()
                    val blurred = ClockBackdropBlur.processCapture(flipped, level)
                    if (!flipped.isRecycled) flipped.recycle()
                    val dt = SystemClock.elapsedRealtime() - t0
                    if (blurred != null && !released.get()) {
                        FileLogger.log(appContext, "CrystalBlur: blur OK ${blurred.width}x${blurred.height} ${dt}ms")
                        synchronized(lock) {
                            pendingBackdropBitmap?.recycle()
                            pendingBackdropBitmap = blurred
                        }
                    } else {
                        blurred?.recycle()
                    }
                } catch (e: Exception) {
                    FileLogger.logException(appContext, "CrystalBlur.processCapture", e)
                } finally {
                    backdropCaptureInFlight.set(false)
                }
            }
        } catch (e: Exception) {
            FileLogger.logException(appContext, "CrystalBlur.capture", e)
            backdropCaptureInFlight.set(false)
        }
    }

    private fun consumePendingTexture() {
        val key: String
        val second: Long
        val frame: ClockFrame
        synchronized(lock) {
            val pending = pendingFrame ?: return
            pendingFrame = null
            frame = pending
            key = pendingSettingsKey
            second = pendingSecondBucket
        }
        val ready = frame.bitmap
        if (ready.isRecycled) return
        try {
            texture.upload(ready)
            hasTexture = true
            lastSettingsKey = key
            lastSecondBucket = second
            applyDestRect(frame)
            // Soft start: esperar textura (+ blur fresco si hace falta)
            if (awaitTextureForSoftStart) {
                val needBlur = ProjectManager.getProject().clock.let {
                    it.crystalMode && it.crystalDefocusLevel > 0
                }
                if (needBlur) {
                    // Siempre esperar blur después de la textura (backdropReady pudo quedar stale)
                    backdropReady = false
                    lastBackdropCaptureMs = 0L
                    awaitBackdropForSoftStart = true
                    awaitTextureForSoftStart = false
                    clockAlpha = 0f
                    fadeStartTime = 0L
                } else {
                    awaitTextureForSoftStart = false
                    startClockFadeOrDelay()
                }
            }
        } finally {
            ready.recycle()
        }
    }

    private fun requestBuild(
        width: Int,
        height: Int,
        settingsKey: String,
        secondBucket: Long
    ) {
        val exec = worker ?: return
        synchronized(lock) {
            reqWidth = width
            reqHeight = height
            reqSettingsKey = settingsKey
            reqSecondBucket = secondBucket
            if (buildInFlight) {
                dirty = true
                return
            }
            buildInFlight = true
            dirty = false
        }
        exec.execute { runBuildLoop() }
    }

    private fun runBuildLoop() {
        while (!released.get()) {
            val w: Int
            val h: Int
            val key: String
            val second: Long
            synchronized(lock) {
                w = reqWidth
                h = reqHeight
                key = reqSettingsKey
                second = reqSecondBucket
                dirty = false
            }
            if (w <= 0 || h <= 0) {
                synchronized(lock) { buildInFlight = false }
                return
            }

            val frame: ClockFrame = try {
                val settings = ProjectManager.getProject().clock
                bitmapGenerator.generate(w, h, settings)
            } catch (_: Exception) {
                synchronized(lock) {
                    if (dirty && !released.get()) {
                        // reintentar con el pedido más reciente
                    } else {
                        buildInFlight = false
                        return
                    }
                }
                continue
            }

            synchronized(lock) {
                if (released.get()) {
                    frame.bitmap.recycle()
                    buildInFlight = false
                    return
                }
                pendingFrame?.bitmap?.recycle()
                pendingFrame = frame
                pendingSettingsKey = key
                pendingSecondBucket = second
                if (dirty) {
                    // Hay un pedido más nuevo: seguir en el loop
                } else {
                    buildInFlight = false
                    return
                }
            }
        }
        synchronized(lock) { buildInFlight = false }
    }

    private fun buildSettingsKey(settings: ClockSettings): String {
        return buildString {
            append(settings.enabled).append('|')
            append(settings.showDate).append('|')
            append(settings.timeFormat).append('|')
            append(settings.dateFormat).append('|')
            append(settings.clockSize).append('|')
            append(settings.dateSize).append('|')
            append(settings.clockColor).append('|')
            append(settings.dateColor).append('|')
            append(settings.x).append('|')
            append(settings.y).append('|')
            append(settings.dateSpacing).append('|')
            append(settings.alignment).append('|')
            append(settings.clockFont).append('|')
            append(settings.dateFont).append('|')
            append(settings.swapTimeAndDate).append('|')
            append(settings.allowOverlap).append('|')
            append(settings.clockVerticalDeform).append('|')
            append(settings.dateVerticalDeform).append('|')
            append(settings.clockBorderWidth).append('|')
            append(settings.dateBorderWidth).append('|')
            append(settings.clockBorderColor).append('|')
            append(settings.dateBorderColor).append('|')
            append(settings.crystalMode).append('|')
            append(settings.crystalBlur).append('|')
            // crystalDefocusLevel NO va acá: no debe regenerar el bitmap del reloj
            append(settings.crystalTextureFile).append('|')
            append(settings.reflectionEnabled).append('|')
            append(settings.reflectionOpacity).append('|')
            append(settings.reflectionGap).append('|')
            append(settings.bevelEnabled).append('|')
            append(settings.bevelAngle).append('|')
            append(settings.bevelStrength).append('|')
            append(settings.fontWidth).append('|')
            append(settings.fontWeight).append('|')
            append(settings.fontOpticalSize).append('|')
            append(settings.fontGrade).append('|')
            append(settings.fontSlant).append('|')
            append(settings.fontXopq).append('|')
            append(settings.fontYopq).append('|')
            append(settings.fontXtra).append('|')
            append(settings.fontYtuc).append('|')
            append(settings.fontYtlc).append('|')
            append(settings.fontYtas).append('|')
            append(settings.fontYtde).append('|')
            append(settings.fontYtfi)
        }
    }

    private fun applyDestRect(frame: ClockFrame) {
        val sw = frame.screenWidth.coerceAtLeast(1).toFloat()
        val sh = frame.screenHeight.coerceAtLeast(1).toFloat()
        destLeftPx = frame.left
        destTopPx = frame.top
        destRightPx = frame.right
        destBottomPx = frame.bottom
        frameScreenW = frame.screenWidth.coerceAtLeast(1)
        frameScreenH = frame.screenHeight.coerceAtLeast(1)
        destLeftNdc = (frame.left / sw) * 2f - 1f
        destRightNdc = (frame.right / sw) * 2f - 1f
        destTopNdc = 1f - (frame.top / sh) * 2f
        destBottomNdc = 1f - (frame.bottom / sh) * 2f
    }

    fun setBackgroundsSettled(settled: Boolean) {
        backgroundsSettled = settled
    }

    fun setLockScreenVisible(visible: Boolean, fadeIn: Boolean) {
        if (visible) {
            forceHidden = false
            val clock = ProjectManager.getProject().clock
            val needBlur = clock.crystalMode && clock.crystalDefocusLevel > 0
            if (fadeIn) {
                beginSoftStart(null)
            } else if (needBlur && !backdropReady) {
                // Visible sin fade, pero aún no hay blur → esperar blur (alpha 0)
                beginSoftStart(null)
            } else {
                clockAlpha = 1f
                fadeStartTime = 0L
                awaitTextureForSoftStart = false
                awaitBackdropForSoftStart = false
                softStartOverrideMs = null
            }
        } else {
            // Oculto a propósito (lock screen deshabilitado, etc.)
            forceHidden = true
            clockAlpha = 0f
            fadeStartTime = 0L
            clockDelayUntil = 0L
            awaitTextureForSoftStart = false
            awaitBackdropForSoftStart = false
            softStartOverrideMs = null
            // Invalidar blur para el próximo show (soft start limpio)
            backdropReady = false
            lastBackdropCaptureMs = 0L
        }
    }

    fun startSoftStart(durationMs: Long? = null) {
        beginSoftStart(durationMs)
    }

    /**
     * Prepara el fade-in. Si la textura ya está, arranca ya;
     * si no, espera a [consumePendingTexture] para no "comerse" el inicio del soft start.
     */
    private fun beginSoftStart(durationMs: Long?) {
        forceHidden = false
        clockAlpha = 0f
        softStartOverrideMs =
            if (durationMs != null && durationMs > 0L) durationMs else null
        awaitTextureForSoftStart = false
        awaitBackdropForSoftStart = false
        fadeStartTime = 0L

        val clock = ProjectManager.getProject().clock
        val needBlur = clock.crystalMode && clock.crystalDefocusLevel > 0

        // Soft start con blur: siempre exigir un blur FRESCO (no reutilizar el de antes
        // de ocultar / cambiar visibilidad). Así el fade arranca con el cristal listo.
        if (needBlur) {
            backdropReady = false
            lastBackdropCaptureMs = 0L
            awaitBackdropForSoftStart = true
            if (!hasTexture) {
                awaitTextureForSoftStart = true
            }
            // Capture se dispara en el próximo draw(); el fade en consumePendingBackdrop / gate
            return
        }

        when {
            !hasTexture -> awaitTextureForSoftStart = true
            else -> startClockFadeOrDelay()
        }
    }

    /**
     * Con desenfoque: se llama cuando el blur ya está listo (otros módulos terminaron).
     * Sin desenfoque: al iniciar soft start con textura lista.
     * Aplica clockDelayStartMs y luego el fade.
     */
    private fun startClockFadeOrDelay() {
        val delay = try {
            ProjectManager.getProject().clockDelayStartMs.coerceAtLeast(0L)
        } catch (_: Exception) { 0L }
        clockAlpha = 0f
        if (delay > 0L) {
            clockDelayUntil = SystemClock.elapsedRealtime() + delay
            fadeStartTime = 0L
        } else {
            clockDelayUntil = 0L
            fadeStartTime = SystemClock.elapsedRealtime()
        }
    }

    private fun updateFade() {
        if (fadeStartTime <= 0L) return
        val elapsed = SystemClock.elapsedRealtime() - fadeStartTime
        val dur = (softStartOverrideMs ?: fadeDurationMs).coerceAtLeast(1L)
        clockAlpha = (elapsed.toFloat() / dur.toFloat()).coerceIn(0f, 1f)
        if (clockAlpha >= 1f) {
            clockAlpha = 1f
            fadeStartTime = 0L
            softStartOverrideMs = null
        }
    }

    fun forceTextureRefresh() {
        lastSettingsKey = ""
        lastSecondBucket = -1L
        hasTexture = false
        synchronized(lock) {
            dirty = true
        }
    }

    fun release() {
        released.set(true)
        worker?.shutdownNow()
        worker = null
        blurWorker?.shutdownNow()
        blurWorker = null
        synchronized(lock) {
            pendingFrame?.bitmap?.recycle()
            pendingFrame = null
            pendingBackdropBitmap?.recycle()
            pendingBackdropBitmap = null
            buildInFlight = false
            dirty = false
        }
        backdropBlur?.release()
        backdropBlur = null
        backdropReady = false
        awaitBackdropForSoftStart = false
        forceHidden = false
        quadRenderer.release()
        texture.release()
        hasTexture = false
    }
}



