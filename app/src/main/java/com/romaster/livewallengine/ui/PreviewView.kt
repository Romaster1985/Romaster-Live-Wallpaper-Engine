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