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

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Shader
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

    private var crystalTextureCache: Bitmap? = null
    private var crystalTextureName: String? = null
    private var currentSettings: ClockSettings? = null

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

        currentSettings = settings
        ensureCrystalTexture(context, settings)

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
            width = settings.fontWidth,
            weight = settings.fontWeight,
            opticalSize = settings.fontOpticalSize,
            grade = settings.fontGrade,
            slant = settings.fontSlant,
            xopq = settings.fontXopq,
            yopq = settings.fontYopq,
            xtra = settings.fontXtra,
            ytuc = settings.fontYtuc,
            ytlc = settings.fontYtlc,
            ytas = settings.fontYtas,
            ytde = settings.fontYtde,
            ytfi = settings.fontYtfi
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

        val crystal = currentSettings?.crystalMode == true
        val hasCrystalTex = crystal && crystalTextureCache != null && crystalTextureCache?.isRecycled == false

        paint.shader = null
        paint.colorFilter = null

        if (crystal && hasCrystalTex) {
            // Textura solo dentro del glifo (offscreen + SRC_IN)
            drawCrystalFill(canvas, text, x, baselineY, colorHex)
            if (borderWidth > 0f) {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = borderWidth
                paint.strokeJoin = Paint.Join.ROUND
                paint.alpha = 255
                try {
                    paint.color = Color.parseColor(borderColorHex)
                } catch (_: Exception) {
                    paint.color = Color.BLACK
                }
                canvas.drawText(text, x, baselineY, paint)
            }
        } else if (crystal) {
            // Cristal sin textura: un solo drawText con alpha (barato, sin bitmap extra)
            val blur = (currentSettings?.crystalBlur ?: 12f).coerceIn(0f, 50f)
            val glassAlpha = ((0.85f - (blur / 50f) * 0.60f) * 255f).toInt().coerceIn(50, 230)
            paint.style = Paint.Style.FILL
            try {
                val c = Color.parseColor(colorHex)
                paint.color = Color.argb(glassAlpha, Color.red(c), Color.green(c), Color.blue(c))
            } catch (_: Exception) {
                paint.color = Color.argb(glassAlpha, 255, 255, 255)
            }
            canvas.drawText(text, x, baselineY, paint)
            if (borderWidth > 0f) {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = borderWidth
                paint.strokeJoin = Paint.Join.ROUND
                paint.alpha = 255
                try {
                    paint.color = Color.parseColor(borderColorHex)
                } catch (_: Exception) {
                    paint.color = Color.BLACK
                }
                canvas.drawText(text, x, baselineY, paint)
            }
        } else {
            if (borderWidth > 0f) {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = borderWidth
                paint.strokeJoin = Paint.Join.ROUND
                paint.strokeMiter = 10f
                paint.alpha = 255
                try {
                    paint.color = Color.parseColor(borderColorHex)
                } catch (_: Exception) {
                    paint.color = Color.BLACK
                }
                canvas.drawText(text, x, baselineY, paint)
            }
            paint.style = Paint.Style.FILL
            paint.alpha = 255
            try {
                paint.color = Color.parseColor(colorHex)
            } catch (_: Exception) {
                paint.color = Color.WHITE
            }
            canvas.drawText(text, x, baselineY, paint)
        }

        paint.shader = null
        paint.colorFilter = null
        paint.alpha = 255
        paint.style = Paint.Style.FILL
        canvas.restore()

        return baselineY + metrics.descent * scaleY
    }

    private fun verticalScale(textSize: Float, deformPx: Float): Float {
        if (textSize <= 0f) return 1f
        return ((textSize + deformPx) / textSize).coerceIn(0.05f, 8f)
    }


    private fun ensureCrystalTexture(context: android.content.Context, settings: ClockSettings) {
        val name = settings.crystalTextureFile
        if (name.isNullOrBlank()) {
            crystalTextureCache = null
            crystalTextureName = null
            return
        }
        if (name == crystalTextureName && crystalTextureCache != null) return
        crystalTextureName = name
        crystalTextureCache = try {
            val f = java.io.File(context.filesDir, "images/$name")
            if (f.exists()) BitmapFactory.decodeFile(f.absolutePath) else null
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Relleno cristalino SOLO dentro del glifo:
     * 1) dibuja la letra en blanco (máscara alpha)
     * 2) rellena con textura o color vía SRC_IN
     * 3) tiñe con el color del reloj y alpha según crystalBlur
     */
    private fun drawCrystalFill(
        canvas: Canvas,
        text: String,
        x: Float,
        baselineY: Float,
        colorHex: String
    ) {
        val tw = paint.measureText(text)
        val fm = paint.fontMetrics
        val pad = (paint.textSize * 0.2f).coerceAtLeast(4f)
        val left = when (paint.textAlign) {
            Paint.Align.CENTER -> x - tw / 2f
            Paint.Align.RIGHT -> x - tw
            else -> x
        }
        val top = baselineY + fm.ascent
        val bw = (tw + pad * 2).toInt().coerceAtLeast(1)
        val bh = (fm.descent - fm.ascent + pad * 2).toInt().coerceAtLeast(1)

        val layer = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        layer.eraseColor(Color.TRANSPARENT)
        val lc = Canvas(layer)
        val localX = when (paint.textAlign) {
            Paint.Align.CENTER -> bw / 2f
            Paint.Align.RIGHT -> bw - pad
            else -> pad
        }
        val localBaseline = pad - fm.ascent

        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = paint.textSize
            typeface = paint.typeface
            textAlign = paint.textAlign
            style = Paint.Style.FILL
            isFilterBitmap = true
        }

        // 1) Máscara del glifo (alpha)
        fillPaint.color = Color.WHITE
        fillPaint.alpha = 255
        fillPaint.shader = null
        fillPaint.xfermode = null
        lc.drawText(text, localX, localBaseline, fillPaint)

        // 2) Relleno solo donde hay glifo (SRC_IN)
        fillPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        val tex = crystalTextureCache
        if (tex != null && !tex.isRecycled) {
            fillPaint.shader = BitmapShader(tex, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
            lc.drawRect(0f, 0f, bw.toFloat(), bh.toFloat(), fillPaint)
            fillPaint.shader = null
        } else {
            try {
                fillPaint.color = Color.parseColor(colorHex)
            } catch (_: Exception) {
                fillPaint.color = Color.WHITE
            }
            fillPaint.alpha = 255
            lc.drawRect(0f, 0f, bw.toFloat(), bh.toFloat(), fillPaint)
        }
        fillPaint.xfermode = null

        // 3) Tinte + transparencia del cristal (crystalBlur 0→50 → alpha 0.85→0.25)
        val tint = try {
            Color.parseColor(colorHex)
        } catch (_: Exception) {
            Color.WHITE
        }
        val blur = (currentSettings?.crystalBlur ?: 12f).coerceIn(0f, 50f)
        val glassAlpha = (0.85f - (blur / 50f) * 0.60f).coerceIn(0.20f, 0.90f)
        val r = Color.red(tint) / 255f
        val g = Color.green(tint) / 255f
        val b = Color.blue(tint) / 255f
        val cm = ColorMatrix(
            floatArrayOf(
                r, 0f, 0f, 0f, 0f,
                0f, g, 0f, 0f, 0f,
                0f, 0f, b, 0f, 0f,
                0f, 0f, 0f, glassAlpha, 0f
            )
        )
        val outPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            isFilterBitmap = true
            colorFilter = ColorMatrixColorFilter(cm)
        }
        canvas.drawBitmap(layer, left - pad, top - pad, outPaint)
        layer.recycle()
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
