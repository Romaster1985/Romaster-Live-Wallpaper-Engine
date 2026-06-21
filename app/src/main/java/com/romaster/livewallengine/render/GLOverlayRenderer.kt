package com.romaster.livewallengine.render

import android.content.Context
import com.romaster.livewallengine.project.ProjectManager

class GLOverlayRenderer {

    private lateinit var bitmapGenerator:
        ClockBitmapGenerator

    private lateinit var texture:
        GLTexture

    private lateinit var quadRenderer:
        GLQuadRenderer

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

        quadRenderer.draw(
            texture
        )
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

    fun release() {

        quadRenderer.release()

        texture.release()
    }
}