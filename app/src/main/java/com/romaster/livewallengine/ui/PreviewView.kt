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
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.View

import com.romaster.livewallengine.model.WallpaperProject
import com.romaster.livewallengine.render.WallpaperRenderer

class PreviewView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(
    context,
    attrs
) {

    private val renderer =
        WallpaperRenderer()

    private var project:
        WallpaperProject? = null

    fun setProject(
        project: WallpaperProject
    ) {

        this.project =
            project

        invalidate()
    }

    override fun onDraw(
        canvas: Canvas
    ) {

        super.onDraw(canvas)

        val project =
            project ?: return

        val screenWidth =
            resources.displayMetrics
                .widthPixels
                .toFloat()

        val screenHeight =
            resources.displayMetrics
                .heightPixels
                .toFloat()

        val scaleX =
            width / screenWidth

        val scaleY =
            height / screenHeight

        val scale =
            minOf(
                scaleX,
                scaleY
            )

        canvas.save()

        canvas.scale(
            scale,
            scale
        )

        renderer.draw(
            context,
            canvas,
            project
        )

        canvas.restore()
        
        postInvalidateDelayed(
            1000
        )
    }
    override fun onMeasure(
        widthMeasureSpec: Int,
        heightMeasureSpec: Int
    ) {
    
        val screenWidth =
            resources.displayMetrics
                .widthPixels
    
        val screenHeight =
            resources.displayMetrics
                .heightPixels
    
        val width =
            MeasureSpec.getSize(
                widthMeasureSpec
            )
    
        val ratio =
            screenHeight.toFloat() /
            screenWidth.toFloat()
    
        val height =
            (width * ratio)
                .toInt()
    
        setMeasuredDimension(
            width,
            height
        )
    }
}