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