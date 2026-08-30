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
import android.graphics.Canvas
import android.graphics.Color

import com.romaster.livewallengine.model.WallpaperProject

class WallpaperRenderer {

    private val clockRenderer =
        ClockRenderer()

    fun draw(
        context: Context,
        canvas: Canvas,
        project: WallpaperProject
    ) {

        canvas.drawColor(
            Color.BLACK
        )

        val clock =
            project.clock

        if (
            clock.enabled ||
            clock.showDate
        ) {

            clockRenderer.draw(
                context,
                canvas,
                clock
            )
        }
    }
}