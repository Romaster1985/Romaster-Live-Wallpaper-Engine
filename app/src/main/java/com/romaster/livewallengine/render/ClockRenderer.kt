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
import android.graphics.BlurMaskFilter
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
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
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.PI

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

        // Contenido derecho (sin reflejo por línea)
        val bounds = drawClockContent(context, canvas, settings)

        // Reflejo del BLOQUE completo (fecha + hora juntos)
        if (settings.reflectionEnabled && bounds != null) {
            drawGroupReflection(context, canvas, settings, bounds.first, bounds.second)
        }
    }

    /**
     * Dibuja hora/fecha y devuelve (groupTop, groupBottom) en coords de canvas
     * para poder espejar el conjunto como un solo bloque.
     */
    private fun drawClockContent(
        context: android.content.Context,
        canvas: Canvas,
        settings: ClockSettings
    ): Pair<Float, Float>? {
        val drawClock = settings.enabled
        val drawDate = settings.showDate
        if (!drawClock && !drawDate) return null

        val baseX = canvas.width * settings.x
        val baseY = canvas.height * settings.y
        val spacing = settings.dateSpacing
        var groupTop = Float.POSITIVE_INFINITY
        var groupBottom = Float.NEGATIVE_INFINITY

        fun track(baselineY: Float, textSize: Float, deformPx: Float, fontFile: String?) {
            preparePaint(
                context, textSize, "#FFFFFF",
                fontFile, settings.alignment, variationOf(settings)
            )
            val scaleY = verticalScale(textSize, deformPx)
            val fm = paint.fontMetrics
            val top = baselineY + fm.ascent * scaleY
            val bot = baselineY + fm.descent * scaleY
            if (top < groupTop) groupTop = top
            if (bot > groupBottom) groupBottom = bot
        }

        fun line(
            text: String,
            baselineY: Float,
            textSize: Float,
            colorHex: String,
            fontFile: String?,
            deformPx: Float,
            borderWidth: Float,
            borderColorHex: String
        ) {
            track(baselineY, textSize, deformPx, fontFile)
            drawTextLine(
                context, canvas,
                text = text,
                x = baseX,
                baselineY = baselineY,
                textSize = textSize,
                colorHex = colorHex,
                fontFile = fontFile,
                alignment = settings.alignment,
                deformPx = deformPx,
                borderWidth = borderWidth,
                borderColorHex = borderColorHex,
                variationSettings = variationOf(settings),
                enableReflection = false
            )
        }

        if (settings.allowOverlap && drawClock && drawDate) {
            val timeBaseline: Float
            val dateBaseline: Float
            if (settings.swapTimeAndDate) {
                dateBaseline = baseY
                timeBaseline = baseY + spacing
            } else {
                timeBaseline = baseY
                dateBaseline = baseY + spacing
            }
            line(
                buildTime(settings), timeBaseline, settings.clockSize,
                settings.clockColor, settings.clockFont, settings.clockVerticalDeform,
                settings.clockBorderWidth, settings.clockBorderColor
            )
            line(
                buildDate(settings), dateBaseline, settings.dateSize,
                settings.dateColor, settings.dateFont, settings.dateVerticalDeform,
                settings.dateBorderWidth, settings.dateBorderColor
            )
        } else if (settings.swapTimeAndDate) {
            var bottom = baseY
            if (drawDate) {
                line(
                    buildDate(settings), baseY, settings.dateSize,
                    settings.dateColor, settings.dateFont, settings.dateVerticalDeform,
                    settings.dateBorderWidth, settings.dateBorderColor
                )
                paint.textSize = settings.dateSize
                val scaleY = verticalScale(settings.dateSize, settings.dateVerticalDeform)
                bottom = baseY + paint.fontMetrics.descent * scaleY
            }
            if (drawClock) {
                preparePaint(
                    context, settings.clockSize, settings.clockColor,
                    settings.clockFont, settings.alignment, variationOf(settings)
                )
                val scaleY = verticalScale(settings.clockSize, settings.clockVerticalDeform)
                val metrics = paint.fontMetrics
                val baseline =
                    if (drawDate) bottom + spacing - metrics.ascent * scaleY else baseY
                line(
                    buildTime(settings), baseline, settings.clockSize,
                    settings.clockColor, settings.clockFont, settings.clockVerticalDeform,
                    settings.clockBorderWidth, settings.clockBorderColor
                )
            }
        } else {
            var bottom = baseY
            if (drawClock) {
                line(
                    buildTime(settings), baseY, settings.clockSize,
                    settings.clockColor, settings.clockFont, settings.clockVerticalDeform,
                    settings.clockBorderWidth, settings.clockBorderColor
                )
                paint.textSize = settings.clockSize
                val scaleY = verticalScale(settings.clockSize, settings.clockVerticalDeform)
                bottom = baseY + paint.fontMetrics.descent * scaleY
            }
            if (drawDate) {
                preparePaint(
                    context, settings.dateSize, settings.dateColor,
                    settings.dateFont, settings.alignment, variationOf(settings)
                )
                val scaleY = verticalScale(settings.dateSize, settings.dateVerticalDeform)
                val metrics = paint.fontMetrics
                val baseline =
                    if (drawClock) bottom + spacing - metrics.ascent * scaleY else baseY
                line(
                    buildDate(settings), baseline, settings.dateSize,
                    settings.dateColor, settings.dateFont, settings.dateVerticalDeform,
                    settings.dateBorderWidth, settings.dateBorderColor
                )
            }
        }

        if (groupTop == Float.POSITIVE_INFINITY) return null
        return groupTop to groupBottom
    }

    /**
     * Espeja fecha+hora como un solo bloque.
     * gap=0 → eje en el centro (invertido superpuesto al derecho).
     * gap↑ → el reflejo baja (puede salir de pantalla).
     */
    private fun drawGroupReflection(
        context: android.content.Context,
        canvas: Canvas,
        settings: ClockSettings,
        groupTop: Float,
        groupBottom: Float
    ) {
        val opacity = (settings.reflectionOpacity / 100f).coerceIn(0f, 1f)
        if (opacity <= 0.01f) return
        val height = groupBottom - groupTop
        if (height <= 1f) return

        val gap = settings.reflectionGap.coerceAtLeast(0f)
        val center = (groupTop + groupBottom) / 2f
        val pivotY = center + gap

        // Tras el flip, el bloque queda en [2P - groupBottom, 2P - groupTop]
        val reflTop = 2f * pivotY - groupBottom
        val reflBottom = 2f * pivotY - groupTop
        val w = canvas.width.toFloat()
        val layerTop = minOf(reflTop, reflBottom) - 2f
        val layerBottom = maxOf(reflTop, reflBottom) + 2f

        val count = canvas.saveLayer(0f, layerTop, w, layerBottom, Paint(Paint.ANTI_ALIAS_FLAG))
        canvas.save()
        canvas.scale(1f, -1f, 0f, pivotY)
        drawClockContent(context, canvas, settings)
        canvas.restore()

        val topAlpha = (opacity * 255f).toInt().coerceIn(0, 255)
        val fade = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, layerTop,
                0f, layerBottom,
                Color.argb(topAlpha, 255, 255, 255),
                Color.TRANSPARENT,
                Shader.TileMode.CLAMP
            )
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        }
        canvas.drawRect(0f, layerTop, w, layerBottom, fade)
        canvas.restoreToCount(count)
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
        variationSettings: String? = null,
        enableReflection: Boolean = true
    ): Float {
        preparePaint(
            context, textSize, colorHex, fontFile, alignment, variationSettings
        )
        val scaleY = verticalScale(textSize, deformPx)
        val metrics = paint.fontMetrics

        canvas.save()
        canvas.scale(1f, scaleY, x, baselineY)

        val crystal = currentSettings?.crystalMode == true
        val hasCrystalTex = crystal && crystalTextureCache != null &&
            crystalTextureCache?.isRecycled == false
        val bevel = currentSettings?.bevelEnabled == true

        paint.shader = null
        paint.colorFilter = null
        paint.xfermode = null

        val fillColor = try {
            Color.parseColor(colorHex)
        } catch (_: Exception) {
            Color.WHITE
        }
        val borderColor = try {
            Color.parseColor(borderColorHex)
        } catch (_: Exception) {
            Color.WHITE
        }

        // Alfa del color del picker (#AARRGGBB) × alfa del cristal (si aplica).
        // Nunca forzamos alpha=255 ni tocamos el RGB (como PixelLab opacity).
        val colorAlpha = Color.alpha(fillColor) // 0..255 del color picker
        val glassFactor = if (crystal) {
            val blur = (currentSettings?.crystalBlur ?: 12f).coerceIn(0f, 50f)
            (0.92f - (blur / 50f) * 0.82f).coerceIn(0.08f, 1f)
        } else {
            1f
        }
        val effectiveAlpha = (colorAlpha * glassFactor).toInt().coerceIn(0, 255)

        // --- 1) RELLENO primero (sólido o cristal) sobre transparente ---
        paint.style = Paint.Style.FILL
        paint.strokeWidth = 0f
        paint.maskFilter = null
        paint.xfermode = null

        if (crystal && hasCrystalTex) {
            drawCrystalFill(canvas, text, x, baselineY, colorHex, glassFactor)
        } else {
            // Con o sin cristal: RGB del picker + alfa efectivo
            paint.color = Color.argb(
                effectiveAlpha,
                Color.red(fillColor),
                Color.green(fillColor),
                Color.blue(fillColor)
            )
            canvas.drawText(text, x, baselineY, paint)
        }

        // --- 2) Borde SOLO exterior (después del relleno, no lo tapa el alfa) ---
        if (borderWidth > 0f) {
            drawExteriorOutline(
                canvas = canvas,
                text = text,
                x = x,
                baselineY = baselineY,
                outlineWidth = borderWidth,
                color = borderColor,
                soft = false
            )
        }

        // --- 3) Relieve DESPUÉS del cristal ---
        // Con cristal: solo luz (sin sombra oscura), con el mismo alfa del vidrio,
        // para no oscurecer el color al fundirse con negro.
        // Sin cristal: relieve clásico luz + sombra.
        if (bevel) {
            val strength = ((currentSettings?.bevelStrength ?: 40f) / 100f).coerceIn(0f, 1f)
            val amount = (textSize * 0.022f * (0.35f + strength)).coerceIn(1f, 6f)
            val angleDeg = currentSettings?.bevelAngle ?: 315f
            val rad = angleDeg / 180f * PI.toFloat()
            val ox = cos(rad) * amount
            val oy = sin(rad) * amount
            paint.style = Paint.Style.FILL

            val lightA = if (crystal) {
                // Luz semitransparente, RGB empujado a blanco (no a negro)
                (effectiveAlpha * (0.35f + 0.45f * strength)).toInt().coerceIn(0, 200)
            } else {
                (130 + 90 * strength).toInt().coerceIn(0, 255)
            }
            val lr = (Color.red(fillColor) + (255 - Color.red(fillColor)) * 0.75f).toInt().coerceIn(0, 255)
            val lg = (Color.green(fillColor) + (255 - Color.green(fillColor)) * 0.75f).toInt().coerceIn(0, 255)
            val lb = (Color.blue(fillColor) + (255 - Color.blue(fillColor)) * 0.75f).toInt().coerceIn(0, 255)
            paint.color = Color.argb(lightA, lr, lg, lb)
            canvas.drawText(text, x - ox, baselineY - oy, paint)

            if (!crystal) {
                // Sombra solo sin modo cristal (sobre opaco no ensucia el vidrio)
                paint.color = Color.argb(
                    (80 + 80 * strength).toInt().coerceIn(0, 255),
                    (Color.red(fillColor) * 0.25f).toInt().coerceIn(0, 255),
                    (Color.green(fillColor) * 0.25f).toInt().coerceIn(0, 255),
                    (Color.blue(fillColor) * 0.25f).toInt().coerceIn(0, 255)
                )
                canvas.drawText(text, x + ox, baselineY + oy, paint)
            } else {
                // Segunda “linterna” opuesta también de luz (sin oscurecer)
                val lightA2 = (lightA * 0.55f).toInt().coerceIn(0, 160)
                paint.color = Color.argb(lightA2, 255, 255, 255)
                canvas.drawText(text, x + ox, baselineY + oy, paint)
            }
        }

        paint.shader = null
        paint.colorFilter = null
        paint.xfermode = null
        paint.maskFilter = null
        paint.alpha = 255
        paint.style = Paint.Style.FILL
        canvas.restore()

        return baselineY + metrics.descent * scaleY
    }


    /**
     * Contorno que SOLO crece hacia afuera del glifo (nunca pinta el interior).
     *
     * Técnica:
     * 1) "Estampa" el glifo en un anillo de offsets radiales (0…width) → corona exterior
     * 2) DST_OUT con el glifo original → borra TODO lo que cae dentro del contorno
     *    (así, con relleno cristal translúcido, adentro no queda rastro del borde)
     */
    private fun drawExteriorOutline(
        canvas: Canvas,
        text: String,
        x: Float,
        baselineY: Float,
        outlineWidth: Float,
        color: Int,
        soft: Boolean
    ) {
        if (outlineWidth <= 0.01f) return

        val tw = paint.measureText(text)
        val fm = paint.fontMetrics
        // pad justo al ancho del borde (sin bucles caros)
        val pad = outlineWidth * 2f + if (soft) outlineWidth else 2f
        val left = when (paint.textAlign) {
            Paint.Align.CENTER -> x - tw / 2f
            Paint.Align.RIGHT -> x - tw
            else -> x
        }
        val top = baselineY + fm.ascent
        val bw = (tw + pad * 2f).toInt().coerceAtLeast(1)
        val bh = (fm.descent - fm.ascent + pad * 2f).toInt().coerceAtLeast(1)

        val layer = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        layer.eraseColor(Color.TRANSPARENT)
        val lc = Canvas(layer)

        val localX = when (paint.textAlign) {
            Paint.Align.CENTER -> bw / 2f
            Paint.Align.RIGHT -> bw - pad
            else -> pad
        }
        val localBaseline = pad - fm.ascent

        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = paint.textSize
            typeface = paint.typeface
            textAlign = paint.textAlign
            isFilterBitmap = true
            strokeJoin = Paint.Join.ROUND
            strokeMiter = 10f
        }

        // 2 drawText: stroke centrado 2× → DST_OUT del glifo = solo anillo exterior
        // (coste O(1), no crece con el grosor como el estampado radial)
        p.style = Paint.Style.STROKE
        p.strokeWidth = outlineWidth * 2f
        p.color = color // respeta alfa del color picker del borde
        lc.drawText(text, localX, localBaseline, p)

        p.style = Paint.Style.FILL
        p.strokeWidth = 0f
        p.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
        p.color = Color.WHITE
        lc.drawText(text, localX, localBaseline, p)
        p.xfermode = null

        val out = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            isFilterBitmap = true
            if (soft) {
                try {
                    maskFilter = BlurMaskFilter(
                        (outlineWidth * 0.5f).coerceAtLeast(1f),
                        BlurMaskFilter.Blur.NORMAL
                    )
                } catch (_: Exception) { }
            }
        }
        canvas.drawBitmap(layer, left - pad, top - pad, out)
        layer.recycle()
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
        colorHex: String,
        glassFactor: Float = 1f
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

        // 3) Tinte por capas: SRC_IN ya dejó el color/textura.
        //    Solo modulamos ALFA (el RGB no se comprime hacia gris).
        val tint = try {
            Color.parseColor(colorHex)
        } catch (_: Exception) {
            Color.WHITE
        }
        // Alfa del picker × factor cristal (RGB intacto)
        val combinedAlpha = ((Color.alpha(tint) / 255f) * glassFactor).coerceIn(0.05f, 1f)
        val tr = Color.red(tint) / 255f
        val tg = Color.green(tint) / 255f
        val tb = Color.blue(tint) / 255f
        val cm = ColorMatrix(
            floatArrayOf(
                tr, 0f, 0f, 0f, 0f,
                0f, tg, 0f, 0f, 0f,
                0f, 0f, tb, 0f, 0f,
                0f, 0f, 0f, combinedAlpha, 0f
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
