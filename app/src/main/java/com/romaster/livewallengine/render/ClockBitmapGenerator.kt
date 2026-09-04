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
import android.graphics.Canvas
import android.graphics.RectF

import com.romaster.livewallengine.model.ClockSettings

/**
 * Bitmap del reloj + rectángulo de destino en píxeles de pantalla.
 */
class ClockFrame(
    val bitmap: Bitmap,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val screenWidth: Int,
    val screenHeight: Int
)

class ClockBitmapGenerator(

    private val context: Context

) {

    private val clockRenderer =
        ClockRenderer()

    fun generate(

        width: Int,

        height: Int,

        settings: ClockSettings

    ): ClockFrame {
        val screenW = width.coerceAtLeast(1)
        val screenH = height.coerceAtLeast(1)

        val bounds = clockRenderer.measureBounds(context, screenW, screenH, settings)
        val cropped = clampBounds(bounds, screenW, screenH)

        val bw = cropped.width().toInt().coerceAtLeast(1)
        val bh = cropped.height().toInt().coerceAtLeast(1)

        val bitmap = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        clockRenderer.draw(
            context,
            canvas,
            settings,
            layoutWidth = screenW,
            layoutHeight = screenH,
            cropLeft = cropped.left,
            cropTop = cropped.top
        )

        return ClockFrame(
            bitmap = bitmap,
            left = cropped.left,
            top = cropped.top,
            right = cropped.right,
            bottom = cropped.bottom,
            screenWidth = screenW,
            screenHeight = screenH
        )
    }

    private fun clampBounds(src: RectF, screenW: Int, screenH: Int): RectF {
        // Permitimos un poco fuera de pantalla (borde/reflejo) pero no bitmaps gigantes
        val margin = 64f
        val minL = -margin
        val minT = -margin
        val maxR = screenW + margin
        val maxB = screenH + margin
        val left = src.left.coerceIn(minL, maxR - 1f)
        val top = src.top.coerceIn(minT, maxB - 1f)
        val right = src.right.coerceAtLeast(left + 1f).coerceAtMost(maxR)
        val bottom = src.bottom.coerceAtLeast(top + 1f).coerceAtMost(maxB)
        return RectF(left, top, right, bottom)
    }
}
