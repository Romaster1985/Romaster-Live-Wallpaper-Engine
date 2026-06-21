package com.romaster.livewallengine.render

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas

import com.romaster.livewallengine.model.ClockSettings

class ClockBitmapGenerator(

    private val context: Context

) {

    private val clockRenderer =
        ClockRenderer()

    fun generate(

        width: Int,

        height: Int,

        settings: ClockSettings

    ): Bitmap {

        val bitmap =
            Bitmap.createBitmap(

                width,

                height,

                Bitmap.Config.ARGB_8888
            )

        val canvas =
            Canvas(bitmap)

        clockRenderer.draw(

            context,

            canvas,

            settings
        )

        return bitmap
    }
}