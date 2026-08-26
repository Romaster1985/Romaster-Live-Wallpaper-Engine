package com.romaster.livewallengine.render

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface

import com.romaster.livewallengine.font.FontManager
import com.romaster.livewallengine.model.ClockSettings
import com.romaster.livewallengine.model.DateFormat
import com.romaster.livewallengine.model.TextAlignment
import com.romaster.livewallengine.model.TimeFormat

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ClockRenderer {

    private val paint =
        Paint(Paint.ANTI_ALIAS_FLAG)

    fun draw(
        context: android.content.Context,
        canvas: Canvas,
        settings: ClockSettings
    ) {

        val drawClock = settings.enabled
        val drawDate = settings.showDate

        if (!drawClock && !drawDate) {
            return
        }

        val baseX = canvas.width * settings.x
        val baseY = canvas.height * settings.y
        val spacing = settings.dateSpacing

        if (settings.swapTimeAndDate) {
            // Fecha arriba (baseline = baseY), hora debajo
            var bottom = baseY
            if (drawDate) {
                bottom = drawTextLine(
                    context,
                    canvas,
                    text = buildDate(settings),
                    x = baseX,
                    baselineY = baseY,
                    textSize = settings.dateSize,
                    colorHex = settings.dateColor,
                    fontFile = settings.dateFont,
                    alignment = settings.alignment,
                    deformPx = settings.dateVerticalDeform
                )
            }
            if (drawClock) {
                preparePaint(
                    context,
                    settings.clockSize,
                    settings.clockColor,
                    settings.clockFont,
                    settings.alignment
                )
                val scaleY = verticalScale(
                    settings.clockSize,
                    settings.clockVerticalDeform
                )
                val metrics = paint.fontMetrics
                val baseline =
                    if (drawDate) {
                        bottom + spacing - metrics.ascent * scaleY
                    } else {
                        baseY
                    }
                drawTextLine(
                    context,
                    canvas,
                    text = buildTime(settings),
                    x = baseX,
                    baselineY = baseline,
                    textSize = settings.clockSize,
                    colorHex = settings.clockColor,
                    fontFile = settings.clockFont,
                    alignment = settings.alignment,
                    deformPx = settings.clockVerticalDeform
                )
            }
        } else {
            // Hora arriba (baseline = baseY), fecha debajo
            var bottom = baseY
            if (drawClock) {
                bottom = drawTextLine(
                    context,
                    canvas,
                    text = buildTime(settings),
                    x = baseX,
                    baselineY = baseY,
                    textSize = settings.clockSize,
                    colorHex = settings.clockColor,
                    fontFile = settings.clockFont,
                    alignment = settings.alignment,
                    deformPx = settings.clockVerticalDeform
                )
            }
            if (drawDate) {
                preparePaint(
                    context,
                    settings.dateSize,
                    settings.dateColor,
                    settings.dateFont,
                    settings.alignment
                )
                val scaleY = verticalScale(
                    settings.dateSize,
                    settings.dateVerticalDeform
                )
                val metrics = paint.fontMetrics
                val baseline =
                    if (drawClock) {
                        bottom + spacing - metrics.ascent * scaleY
                    } else {
                        baseY
                    }
                drawTextLine(
                    context,
                    canvas,
                    text = buildDate(settings),
                    x = baseX,
                    baselineY = baseline,
                    textSize = settings.dateSize,
                    colorHex = settings.dateColor,
                    fontFile = settings.dateFont,
                    alignment = settings.alignment,
                    deformPx = settings.dateVerticalDeform
                )
            }
        }
    }

    private fun preparePaint(
        context: android.content.Context,
        textSize: Float,
        colorHex: String,
        fontFile: String?,
        alignment: TextAlignment
    ) {
        paint.color = Color.parseColor(colorHex)
        paint.textSize = textSize
        paint.typeface = fontFile?.let {
            FontManager.loadTypeface(context, it)
        } ?: Typeface.DEFAULT
        paint.textAlign = convertAlignment(alignment)
    }

    /**
     * Dibuja una línea de texto con deformación vertical opcional.
     * @return coordenada Y del borde inferior del glifo (para apilar)
     */
    private fun drawTextLine(
        context: android.content.Context,
        canvas: Canvas,
        text: String,
        x: Float,
        baselineY: Float,
        textSize: Float,
        colorHex: String,
        fontFile: String?,
        alignment: TextAlignment,
        deformPx: Float
    ): Float {
        preparePaint(context, textSize, colorHex, fontFile, alignment)
        val scaleY = verticalScale(textSize, deformPx)
        val metrics = paint.fontMetrics

        canvas.save()
        canvas.scale(1f, scaleY, x, baselineY)
        canvas.drawText(text, x, baselineY, paint)
        canvas.restore()

        return baselineY + metrics.descent * scaleY
    }

    /** 0 = normal; positivo estira; negativo comprime. */
    private fun verticalScale(textSize: Float, deformPx: Float): Float {
        if (textSize <= 0f) return 1f
        return ((textSize + deformPx) / textSize).coerceIn(0.05f, 8f)
    }

    private fun convertAlignment(
        alignment: TextAlignment
    ): Paint.Align {
        return when (alignment) {
            TextAlignment.LEFT -> Paint.Align.LEFT
            TextAlignment.RIGHT -> Paint.Align.RIGHT
            TextAlignment.CENTER -> Paint.Align.CENTER
        }
    }

    private fun buildTime(settings: ClockSettings): String {
        val pattern = when (settings.timeFormat) {
            TimeFormat.HH_MM -> "HH:mm"
            TimeFormat.HH_MM_SS -> "HH:mm:ss"
            TimeFormat.HH_MM_AM_PM -> "hh:mm a"
        }
        return SimpleDateFormat(pattern, Locale.getDefault()).format(Date())
    }

    private fun buildDate(settings: ClockSettings): String {
        val pattern = when (settings.dateFormat) {
            DateFormat.DOW_DD_MON -> "EEE, dd MMM"
            DateFormat.DD_MM_YYYY -> "dd/MM/yyyy"
            DateFormat.DOW_DD_MON_YYYY -> "EEE, dd MMM yyyy"
        }
        return SimpleDateFormat(pattern, Locale.getDefault()).format(Date())
    }
}
