package com.romaster.livewallengine.render

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint

import com.romaster.livewallengine.model.ClockSettings
import com.romaster.livewallengine.model.DateFormat
import com.romaster.livewallengine.model.TextAlignment
import com.romaster.livewallengine.model.TimeFormat

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import android.graphics.Typeface

import com.romaster.livewallengine.font.FontManager

class ClockRenderer {

    private val paint =
        Paint(Paint.ANTI_ALIAS_FLAG)

    fun draw(
        context: android.content.Context,
        canvas: Canvas,
        settings: ClockSettings
    ) {

        val drawClock =
            settings.enabled

        val drawDate =
            settings.showDate

        if (
            !drawClock &&
            !drawDate
        ) {
            return
        }

        val baseX =
            canvas.width *
                    settings.x

        val baseY =
            canvas.height *
                    settings.y

        if (drawClock) {

            paint.color =
                Color.parseColor(
                    settings.clockColor
                )

            paint.textSize =
                settings.clockSize
            
            paint.typeface =
                settings.clockFont?.let {
            
                    FontManager.loadTypeface(
                        context,
                        it
                    )
            
                } ?: Typeface.DEFAULT

            paint.textAlign =
                convertAlignment(
                    settings.alignment
                )

            canvas.drawText(
                buildTime(settings),
                baseX,
                baseY,
                paint
            )
        }

        if (drawDate) {

            paint.color =
                Color.parseColor(
                    settings.dateColor
                )

            paint.textSize =
                settings.dateSize
            
            paint.typeface =
                settings.dateFont?.let {
            
                    FontManager.loadTypeface(
                        context,
                        it
                    )
            
                } ?: Typeface.DEFAULT

            paint.textAlign =
                convertAlignment(
                    settings.alignment
                )

            val dateY =
                if (drawClock) {

                    baseY +
                            settings.clockSize +
                            settings.dateSpacing

                } else {

                    baseY
                }

            canvas.drawText(
                buildDate(settings),
                baseX,
                dateY,
                paint
            )
        }
    }

    private fun convertAlignment(
        alignment: TextAlignment
    ): Paint.Align {

        return when (
            alignment
        ) {

            TextAlignment.LEFT ->
                Paint.Align.LEFT

            TextAlignment.RIGHT ->
                Paint.Align.RIGHT

            TextAlignment.CENTER ->
                Paint.Align.CENTER
        }
    }

    private fun buildTime(
        settings: ClockSettings
    ): String {

        val pattern =
            when (
                settings.timeFormat
            ) {

                TimeFormat.HH_MM ->
                    "HH:mm"

                TimeFormat.HH_MM_SS ->
                    "HH:mm:ss"

                TimeFormat.HH_MM_AM_PM ->
                    "hh:mm a"
            }

        return SimpleDateFormat(
            pattern,
            Locale.getDefault()
        ).format(Date())
    }

    private fun buildDate(
        settings: ClockSettings
    ): String {

        val pattern =
            when (
                settings.dateFormat
            ) {

                DateFormat.DOW_DD_MON ->
                    "EEE, dd MMM"

                DateFormat.DD_MM_YYYY ->
                    "dd/MM/yyyy"

                DateFormat.DOW_DD_MON_YYYY ->
                    "EEE, dd MMM yyyy"
            }

        return SimpleDateFormat(
            pattern,
            Locale.getDefault()
        ).format(Date())
    }
}