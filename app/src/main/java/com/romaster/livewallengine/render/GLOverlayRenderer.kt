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
import android.os.SystemClock
import com.romaster.livewallengine.model.ClockSettings
import com.romaster.livewallengine.project.ProjectManager
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

    private var lastSettingsKey: String = ""
    private var lastSecondBucket: Long = -1L
    private var hasTexture = false

    private val released = AtomicBoolean(false)

    fun initialize(context: Context) {
        appContext = context.applicationContext
        bitmapGenerator = ClockBitmapGenerator(appContext)
        texture = GLTexture()
        quadRenderer = GLQuadRenderer()
        quadRenderer.initialize()
        clockAlpha = 1f
        hasTexture = false
        released.set(false)
        worker = Executors.newSingleThreadExecutor { r ->
            Thread(r, "ClockBitmapWorker").apply {
                priority = Thread.NORM_PRIORITY - 1
            }
        }
    }

    fun draw(width: Int, height: Int) {
        if (width <= 0 || height <= 0 || released.get()) return

        // 1) Consumir bitmap listo en el hilo GL (upload rápido)
        consumePendingTexture()

        val settings = ProjectManager.getProject().clock
        val secondBucket = System.currentTimeMillis() / 1000L
        val settingsKey = buildSettingsKey(settings)
        val settingsChanged = settingsKey != lastSettingsKey
        val timeChanged = secondBucket != lastSecondBucket

        // 2) Pedir regeneración en background si hace falta
        if (!hasTexture || settingsChanged || timeChanged) {
            requestBuild(width, height, settingsKey, secondBucket)
        }

        // 3) Dibujar la textura actual (puede ser la del segundo anterior un frame)
        updateFade()
        if (clockAlpha > 0f && hasTexture) {
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
            // Soft start: la rampa de alpha empieza acá, no cuando se pidió el fade
            if (awaitTextureForSoftStart) {
                clockAlpha = 0f
                fadeStartTime = SystemClock.elapsedRealtime()
                awaitTextureForSoftStart = false
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
        destLeftNdc = (frame.left / sw) * 2f - 1f
        destRightNdc = (frame.right / sw) * 2f - 1f
        destTopNdc = 1f - (frame.top / sh) * 2f
        destBottomNdc = 1f - (frame.bottom / sh) * 2f
    }

    fun setLockScreenVisible(visible: Boolean, fadeIn: Boolean) {
        if (visible) {
            if (fadeIn) {
                beginSoftStart(null)
            } else {
                clockAlpha = 1f
                fadeStartTime = 0L
                awaitTextureForSoftStart = false
                softStartOverrideMs = null
            }
        } else {
            clockAlpha = 0f
            fadeStartTime = 0L
            awaitTextureForSoftStart = false
            softStartOverrideMs = null
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
        clockAlpha = 0f
        softStartOverrideMs =
            if (durationMs != null && durationMs > 0L) durationMs else null
        if (hasTexture) {
            fadeStartTime = SystemClock.elapsedRealtime()
            awaitTextureForSoftStart = false
        } else {
            fadeStartTime = 0L
            awaitTextureForSoftStart = true
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
        synchronized(lock) {
            pendingFrame?.bitmap?.recycle()
            pendingFrame = null
            buildInFlight = false
            dirty = false
        }
        quadRenderer.release()
        texture.release()
        hasTexture = false
    }
}
