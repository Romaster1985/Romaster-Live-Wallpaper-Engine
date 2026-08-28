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

        // Superposición en la misma línea: hora detrás, fecha delante.
        // spacing = 0 → misma baseline; spacing ≠ 0 desplaza el segundo texto.
        if (settings.allowOverlap && drawClock && drawDate) {
            val timeBaseline: Float
            val dateBaseline: Float
            if (settings.swapTimeAndDate) {
                // Fecha en baseY; hora desplazada (pero z-order: hora primero)
                dateBaseline = baseY
                timeBaseline = baseY + spacing
            } else {
                timeBaseline = baseY
                dateBaseline = baseY + spacing
            }
            // Siempre hora detrás (se dibuja primero), fecha encima
            drawTextLine(
                context, canvas,
                text = buildTime(settings),
                x = baseX,
                baselineY = timeBaseline,
                textSize = settings.clockSize,
                colorHex = settings.clockColor,
                fontFile = settings.clockFont,
                alignment = settings.alignment,
                deformPx = settings.clockVerticalDeform,
                borderWidth = settings.clockBorderWidth,
                borderColorHex = settings.clockBorderColor,
                variationSettings = variationOf(settings)
            )
            drawTextLine(
                context, canvas,
                text = buildDate(settings),
                x = baseX,
                baselineY = dateBaseline,
                textSize = settings.dateSize,
                colorHex = settings.dateColor,
                fontFile = settings.dateFont,
                alignment = settings.alignment,
                deformPx = settings.dateVerticalDeform,
                borderWidth = settings.dateBorderWidth,
                borderColorHex = settings.dateBorderColor,
                variationSettings = variationOf(settings)
            )
            return
        }

        if (settings.swapTimeAndDate) {
            var bottom = baseY
            if (drawDate) {
                bottom = drawTextLine(
                    context, canvas,
                    text = buildDate(settings),
                    x = baseX,
                    baselineY = baseY,
                    textSize = settings.dateSize,
                    colorHex = settings.dateColor,
                    fontFile = settings.dateFont,
                    alignment = settings.alignment,
                    deformPx = settings.dateVerticalDeform,
                    borderWidth = settings.dateBorderWidth,
                    borderColorHex = settings.dateBorderColor,
                    variationSettings = variationOf(settings)
                )
            }
            if (drawClock) {
                preparePaint(
                    context,
                    settings.clockSize,
                    settings.clockColor,
                    settings.clockFont,
                    settings.alignment,
                    variationOf(settings)
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
                    context, canvas,
                    text = buildTime(settings),
                    x = baseX,
                    baselineY = baseline,
                    textSize = settings.clockSize,
                    colorHex = settings.clockColor,
                    fontFile = settings.clockFont,
                    alignment = settings.alignment,
                    deformPx = settings.clockVerticalDeform,
                    borderWidth = settings.clockBorderWidth,
                    borderColorHex = settings.clockBorderColor,
                    variationSettings = variationOf(settings)
                )
            }
        } else {
            var bottom = baseY
            if (drawClock) {
                bottom = drawTextLine(
                    context, canvas,
                    text = buildTime(settings),
                    x = baseX,
                    baselineY = baseY,
                    textSize = settings.clockSize,
                    colorHex = settings.clockColor,
                    fontFile = settings.clockFont,
                    alignment = settings.alignment,
                    deformPx = settings.clockVerticalDeform,
                    borderWidth = settings.clockBorderWidth,
                    borderColorHex = settings.clockBorderColor,
                    variationSettings = variationOf(settings)
                )
            }
            if (drawDate) {
                preparePaint(
                    context,
                    settings.dateSize,
                    settings.dateColor,
                    settings.dateFont,
                    settings.alignment,
                    variationOf(settings)
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
                    context, canvas,
                    text = buildDate(settings),
                    x = baseX,
                    baselineY = baseline,
                    textSize = settings.dateSize,
                    colorHex = settings.dateColor,
                    fontFile = settings.dateFont,
                    alignment = settings.alignment,
                    deformPx = settings.dateVerticalDeform,
                    borderWidth = settings.dateBorderWidth,
                    borderColorHex = settings.dateBorderColor,
                    variationSettings = variationOf(settings)
                )
            }
        }
    }

    private fun preparePaint(
        context: android.content.Context,
        textSize: Float,
        colorHex: String,
        fontFile: String?,
        alignment: TextAlignment,
        variationSettings: String? = null
    ) {
        paint.color = Color.parseColor(colorHex)
        paint.textSize = textSize
        paint.typeface = fontFile?.let {
            FontManager.loadTypeface(context, it, variationSettings)
        } ?: Typeface.DEFAULT
        paint.textAlign = convertAlignment(alignment)
    }

    private fun variationOf(settings: ClockSettings): String =
        FontManager.buildVariationSettings(
            settings.fontWidth,
            settings.fontWeight,
            settings.fontOpticalSize,
            settings.fontGrade
        )

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
        deformPx: Float,
        borderWidth: Float = 0f,
        borderColorHex: String = "#000000",
        variationSettings: String? = null
    ): Float {
        preparePaint(
            context, textSize, colorHex, fontFile, alignment, variationSettings
        )
        val scaleY = verticalScale(textSize, deformPx)
        val metrics = paint.fontMetrics

        canvas.save()
        canvas.scale(1f, scaleY, x, baselineY)

        if (borderWidth > 0f) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = borderWidth
            paint.strokeJoin = Paint.Join.ROUND
            paint.strokeMiter = 10f
            try {
                paint.color = Color.parseColor(borderColorHex)
            } catch (_: Exception) {
                paint.color = Color.BLACK
            }
            canvas.drawText(text, x, baselineY, paint)

            paint.style = Paint.Style.FILL
            try {
                paint.color = Color.parseColor(colorHex)
            } catch (_: Exception) {
                paint.color = Color.WHITE
            }
        }

        canvas.drawText(text, x, baselineY, paint)
        canvas.restore()

        return baselineY + metrics.descent * scaleY
    }

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
