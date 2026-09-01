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
import android.os.SystemClock
import com.romaster.livewallengine.project.ProjectManager

/**
 * Textura del reloj a pantalla completa.
 *
 * Política de actualización (no bloquea el FPS del video):
 * - Con el tiempo (dígitos): 1 vez por segundo.
 * - Si cambian ajustes (posición, tamaño, cristal, color…): como máximo ~60 veces/s.
 * - Si no cambió nada: no se regenera el bitmap.
 */
class GLOverlayRenderer {

    private lateinit var bitmapGenerator: ClockBitmapGenerator
    private lateinit var texture: GLTexture
    private lateinit var quadRenderer: GLQuadRenderer

    private val fadeDurationMs: Long
        get() = ProjectManager.getProject().clockFadeDurationMs

    private var fadeStartTime = 0L
    private var clockAlpha = 1f
    private var softStartOverrideMs: Long? = null

    private var lastSettingsKey: String = ""
    private var lastSecondBucket: Long = -1L
    private var lastBuildElapsed: Long = 0L
    private var hasTexture = false

    fun initialize(context: Context) {
        bitmapGenerator = ClockBitmapGenerator(context)
        texture = GLTexture()
        quadRenderer = GLQuadRenderer()
        quadRenderer.initialize()
        clockAlpha = 1f
        hasTexture = false
    }

    fun draw(width: Int, height: Int) {
        if (width <= 0 || height <= 0) return

        val project = ProjectManager.getProject()
        val settings = project.clock
        val nowWall = System.currentTimeMillis()
        val nowElapsed = SystemClock.elapsedRealtime()
        val secondBucket = nowWall / 1000L

        val settingsKey = buildSettingsKey(settings)
        val settingsChanged = settingsKey != lastSettingsKey
        val timeChanged = secondBucket != lastSecondBucket

        // Máx. 60 regeneraciones/s para no matar el hilo GL (sobre todo con cristal)
        val minIntervalMs = 16L
        val canBuild = (nowElapsed - lastBuildElapsed) >= minIntervalMs

        val needBuild = !hasTexture ||
            ((settingsChanged || timeChanged) && canBuild)

        if (needBuild) {
            updateTexture(width, height)
            lastSettingsKey = settingsKey
            lastSecondBucket = secondBucket
            lastBuildElapsed = nowElapsed
            hasTexture = true
        }

        updateFade()
        if (clockAlpha > 0f && hasTexture) {
            quadRenderer.draw(texture, clockAlpha)
        }
    }

    private fun buildSettingsKey(settings: com.romaster.livewallengine.model.ClockSettings): String {
        // Todo lo que afecta al bitmap (excepto el texto de la hora, que va por secondBucket)
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

    private fun updateTexture(width: Int, height: Int) {
        val settings = ProjectManager.getProject().clock
        val bitmap = bitmapGenerator.generate(width, height, settings)
        texture.upload(bitmap)
        bitmap.recycle()
    }

    fun setLockScreenVisible(visible: Boolean, fadeIn: Boolean) {
        if (visible) {
            if (fadeIn) {
                clockAlpha = 0f
                fadeStartTime = SystemClock.elapsedRealtime()
            } else {
                clockAlpha = 1f
                fadeStartTime = 0L
            }
        } else {
            clockAlpha = 0f
            fadeStartTime = 0L
        }
    }

    fun startSoftStart(durationMs: Long? = null) {
        clockAlpha = 0f
        fadeStartTime = SystemClock.elapsedRealtime()
        softStartOverrideMs =
            if (durationMs != null && durationMs > 0L) durationMs else null
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
        lastBuildElapsed = 0L
        hasTexture = false
    }

    fun release() {
        quadRenderer.release()
        texture.release()
        hasTexture = false
    }
}
