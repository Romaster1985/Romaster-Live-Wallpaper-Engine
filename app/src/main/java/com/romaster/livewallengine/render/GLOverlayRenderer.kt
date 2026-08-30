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
import com.romaster.livewallengine.project.ProjectManager
import android.os.SystemClock

class GLOverlayRenderer {

    private lateinit var bitmapGenerator:
        ClockBitmapGenerator

    private lateinit var texture:
        GLTexture

    private lateinit var quadRenderer:
        GLQuadRenderer
    
    private val fadeDurationMs: Long
        get() =
            ProjectManager
                .getProject()
                .clockFadeDurationMs

    private var fadeStartTime = 0L
    
    private var clockAlpha = 1f

    private var lastUpdate = 0L

    fun initialize(
        context: Context
    ) {

        bitmapGenerator =
            ClockBitmapGenerator(
                context
            )

        texture =
            GLTexture()

        quadRenderer =
            GLQuadRenderer()

        quadRenderer.initialize()
        
        clockAlpha = 1f
    }

    fun draw(
        width: Int,
        height: Int
    ) {

        val now =
            System.currentTimeMillis()

        if (
            now - lastUpdate >= 1000L
        ) {

            updateTexture(
                width,
                height
            )

            lastUpdate = now
        }

        updateFade()

        if (clockAlpha > 0f) {

            quadRenderer.draw(
                texture,
                clockAlpha
            )
        }
    }

    private fun updateTexture(
        width: Int,
        height: Int
    ) {

        val settings =
            ProjectManager
                .getProject()
                .clock

        val bitmap =
            bitmapGenerator.generate(
                width,
                height,
                settings
            )

        texture.upload(
            bitmap
        )

        bitmap.recycle()
    }
    
    fun setLockScreenVisible(
        visible: Boolean,
        fadeIn: Boolean
    ) {
    
        if (visible) {
    
            if (fadeIn) {
    
                clockAlpha = 0f
    
                fadeStartTime =
                    SystemClock.elapsedRealtime()
    
            } else {
    
                clockAlpha = 1f
    
                fadeStartTime = 0L
            }
    
        } else {
    
            clockAlpha = 0f
    
            fadeStartTime = 0L
        }
    }
    
    private fun updateFade() {

        if (fadeStartTime <= 0L) {
            return
        }
    
        val elapsed =
            SystemClock.elapsedRealtime() -
            fadeStartTime
    
        clockAlpha =
            (
                elapsed.toFloat() /
                fadeDurationMs.toFloat()
            ).coerceIn(
                0f,
                1f
            )
    
        if (clockAlpha >= 1f) {
    
            clockAlpha = 1f
    
            fadeStartTime = 0L
        }
    }

    fun release() {

        quadRenderer.release()

        texture.release()
    }
}