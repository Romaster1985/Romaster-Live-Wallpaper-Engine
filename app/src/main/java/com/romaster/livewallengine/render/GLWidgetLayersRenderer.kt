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

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.opengl.GLES20
import android.opengl.Matrix
import android.os.SystemClock
import com.romaster.livewallengine.formula.FormulaEngine
import com.romaster.livewallengine.LocaleHelper
import com.romaster.livewallengine.font.FontManager
import com.romaster.livewallengine.font.IconFontStorage
import android.graphics.RectF
import android.graphics.SweepGradient
import com.romaster.livewallengine.model.ProgressColorMode
import com.romaster.livewallengine.model.ProgressFillMode
import com.romaster.livewallengine.model.ProgressShape
import com.romaster.livewallengine.model.TextAlignment
import com.romaster.livewallengine.model.VerticalAlignment
import com.romaster.livewallengine.model.WidgetLayer
import com.romaster.livewallengine.model.WidgetType
import com.romaster.livewallengine.project.ProjectManager
import com.romaster.livewallengine.animation.AnimationEngine
import com.romaster.livewallengine.effect.EffectEngine
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * Renderiza capas Widgets-OL: evalúa la fórmula, rasteriza texto a bitmap
 * y dibuja con la misma pipeline GL que Pics-OL.
 *
 * Actualiza el texto ~1 Hz (o al cambiar fórmula/estilo).
 */
class GLWidgetLayersRenderer {

    private var program = 0
    private var positionHandle = 0
    private var texCoordHandle = 0
    private var samplerHandle = 0
    private var alphaHandle = 0
    private var mvpHandle = 0
    private var modelHandle = 0

    private lateinit var vertexBuffer: FloatBuffer
    private val textures = mutableMapOf<String, GLTexture>()
    private val bitmapSize = mutableMapOf<String, Pair<Int, Int>>()
    private val lastText = mutableMapOf<String, String>()
    private val lastStyle = mutableMapOf<String, String>()
    private val lastEvalMs = mutableMapOf<String, Long>()
    private val fadeStartTimes = mutableMapOf<String, Long>()
    private val forceHidden = mutableMapOf<String, Boolean>()

    private var screenW = 1
    private var screenH = 1
    private var context: Context? = null
    private var lastDeviceLocked = false

    private val quad = floatArrayOf(
        -1f, -1f, 0f, 1f,
        1f, -1f, 1f, 1f,
        -1f, 1f, 0f, 0f,
        -1f, 1f, 0f, 0f,
        1f, -1f, 1f, 1f,
        1f, 1f, 1f, 0f
    )

    private val vShader = """
        attribute vec2 aPosition;
        attribute vec2 aTexCoord;
        uniform mat4 uMVP;
        uniform mat4 uModel;
        varying vec2 vTexCoord;
        varying vec2 vOrtho;
        void main() {
            vec4 o = uModel * vec4(aPosition, 0.0, 1.0);
            vOrtho = o.xy;
            gl_Position = uMVP * vec4(aPosition, 0.0, 1.0);
            vTexCoord = aTexCoord;
        }
    """.trimIndent()

    private val fShader = """
        precision mediump float;
        uniform sampler2D uTexture;
        uniform float uAlpha;
        varying vec2 vTexCoord;

        uniform float uFxSat;
        uniform float uFxHue;
        uniform float uFxInvert;
        uniform float uFxInvertLuma;
        uniform float uFxSolar;
        uniform vec3  uFxTint;
        uniform float uFxTintStr;
        uniform float uFxTintMode;
        vec3 fxRgb2Hsv(vec3 c) {
            vec4 K = vec4(0.0, -1.0 / 3.0, 2.0 / 3.0, -1.0);
            vec4 p = mix(vec4(c.bg, K.wz), vec4(c.gb, K.xy), step(c.b, c.g));
            vec4 q = mix(vec4(p.xyw, c.r), vec4(c.r, p.yzx), step(p.x, c.r));
            float d = q.x - min(q.w, q.y);
            float e = 1.0e-10;
            return vec3(abs(q.z + (q.w - q.y) / (6.0 * d + e)), d / (q.x + e), q.x);
        }
        vec3 fxHsv2Rgb(vec3 c) {
            vec4 K = vec4(1.0, 2.0 / 3.0, 1.0 / 3.0, 3.0);
            vec3 p = abs(fract(c.xxx + K.xyz) * 6.0 - K.www);
            return c.z * mix(K.xxx, clamp(p - K.xxx, 0.0, 1.0), c.y);
        }
        vec3 fxApplyColor(vec3 c) {
            float gray = dot(c, vec3(0.299, 0.587, 0.114));
            c = mix(vec3(gray), c, uFxSat);
            if (abs(uFxHue) > 0.01) {
                vec3 hsv = fxRgb2Hsv(clamp(c, 0.0, 1.0));
                hsv.x = fract(hsv.x + uFxHue / 360.0);
                c = fxHsv2Rgb(hsv);
            }
            if (uFxTintStr > 0.001) {
                if (uFxTintMode > 0.5) c = mix(c, c * uFxTint, uFxTintStr);
                else c = mix(c, uFxTint, uFxTintStr);
            }
            if (uFxSolar >= 0.0) {
                float l = dot(c, vec3(0.299, 0.587, 0.114));
                if (l > uFxSolar) c = 1.0 - c;
            }
            if (uFxInvert > 0.5) {
                if (uFxInvertLuma > 0.5) {
                    vec3 hsv = fxRgb2Hsv(clamp(c, 0.0, 1.0));
                    hsv.x = fract(hsv.x + 0.5);
                    c = fxHsv2Rgb(hsv);
                } else {
                    c = 1.0 - c;
                }
            }
            return clamp(c, 0.0, 1.0);
        }

        uniform sampler2D uMask;
        uniform float uMaskEnabled;
        uniform vec2 uMaskCenter;
        uniform float uMaskScale;
        uniform float uMaskAspect;
        uniform float uMaskRot;
        uniform float uMaskInvert;
        varying vec2 vOrtho;
        float fxMaskAlpha() {
            if (uMaskEnabled < 0.5) return 1.0;
            vec2 d = vOrtho - uMaskCenter;
            float c0 = cos(-uMaskRot);
            float s0 = sin(-uMaskRot);
            vec2 r = vec2(c0 * d.x - s0 * d.y, s0 * d.x + c0 * d.y);
            float halfH = max(uMaskScale, 0.001);
            float halfW = halfH * max(uMaskAspect, 0.001);
            vec2 uv = vec2(r.x / halfW, r.y / halfH) * 0.5 + 0.5;
            float m = 0.0;
            if (uv.x >= 0.0 && uv.x <= 1.0 && uv.y >= 0.0 && uv.y <= 1.0) {
                vec4 mt = texture2D(uMask, uv);
                m = max(mt.a, max(mt.r, max(mt.g, mt.b)));
            }
            if (uMaskInvert > 0.5) m = 1.0 - m;
            return m;
        }
        void main() {
            vec4 c = texture2D(uTexture, vTexCoord);
            c.rgb = fxApplyColor(c.rgb);
            gl_FragColor = vec4(c.rgb, c.a * uAlpha * fxMaskAlpha());
        }
    """.trimIndent()

    fun initialize(ctx: Context, width: Int, height: Int) {
        context = LocaleHelper.wrap(ctx.applicationContext)
        screenW = width.coerceAtLeast(1)
        screenH = height.coerceAtLeast(1)

        vertexBuffer = ByteBuffer.allocateDirect(quad.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(quad)
                position(0)
            }

        val vs = loadShader(GLES20.GL_VERTEX_SHADER, vShader)
        val fs = loadShader(GLES20.GL_FRAGMENT_SHADER, fShader)
        program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vs)
        GLES20.glAttachShader(program, fs)
        GLES20.glLinkProgram(program)

        positionHandle = GLES20.glGetAttribLocation(program, "aPosition")
        texCoordHandle = GLES20.glGetAttribLocation(program, "aTexCoord")
        samplerHandle = GLES20.glGetUniformLocation(program, "uTexture")
        alphaHandle = GLES20.glGetUniformLocation(program, "uAlpha")
        mvpHandle = GLES20.glGetUniformLocation(program, "uMVP")
        modelHandle = GLES20.glGetUniformLocation(program, "uModel")

        EffectEngine.setContext(ctx)
        reloadFromProject(force = true)
    }

    fun setSize(width: Int, height: Int) {
        val nw = width.coerceAtLeast(1)
        val nh = height.coerceAtLeast(1)
        val changed = nw != screenW || nh != screenH
        screenW = nw
        screenH = nh
        // El bitmap se rasteriza en px absolutos: si cambia el tamaño de superficie
        // hay que regenerarlo para que preview y wallpaper coincidan.
        if (changed) {
            lastText.clear()
        lastStyle.clear()
            lastEvalMs.clear()
            reloadFromProject(force = true)
        }
    }

    fun reloadFromProject(force: Boolean = false) {
        val layers = ProjectManager.getProject().widgetLayers
        val ids = layers.map { it.id }.toSet()
        textures.keys.filter { it !in ids }.forEach { id ->
            textures.remove(id)?.release()
            bitmapSize.remove(id)
            lastText.remove(id)
            lastStyle.remove(id)
            lastEvalMs.remove(id)
            fadeStartTimes.remove(id)
            forceHidden.remove(id)
        }
        layers.forEach { layer ->
            if (force || layer.id !in textures) {
                rebuildLayerBitmap(layer, force = true)
            }
        }
        // Reaplicar visibilidad bloqueo/launcher con el último estado conocido
        applyLockScreenState(lastDeviceLocked)
    }

    fun drawById(id: String) {
        val layer = ProjectManager.getProject().widgetLayers.find { it.id == id } ?: return
        if (forceHidden[id] == true) return
        maybeRefresh(layer)
        val tex = textures[id] ?: return
        val size = bitmapSize[id] ?: return
        val alpha = (layer.opacity * layerFadeAlpha(layer)).coerceIn(0f, 1f)
        if (alpha < 0.01f) return

        val (bw, bh) = size
        if (bw <= 0 || bh <= 0) return

        val anim = AnimationEngine.stateForWidget(layer.id)
        val alphaAnim = (alpha * anim.alphaMul).coerceIn(0f, 1f)
        if (alphaAnim < 0.01f) return

        // Misma proyección ortográfica que Pics-OL para no deformar al rotar.
        // Escala en unidades de altura de pantalla → aspect ratio del bitmap se conserva.
        val screenRatio = screenW.toFloat() / screenH.toFloat().coerceAtLeast(1f)
        val zoom = (layer.zoom * anim.zoomMul).coerceIn(0.05f, 20f)
        val scaleX = (bw.toFloat() / screenH) * zoom
        val scaleY = (bh.toFloat() / screenH) * zoom

        // Ancla de posición según alineación (el "cero" de X/Y):
        // LEFT/CENTER/RIGHT → borde izq / centro / borde der del bitmap
        // TOP/MIDDLE/BOTTOM → borde sup / centro / borde inf
        // x,y: centro=0 en % de pantalla (-200..200)
        val anchorX = (layer.x / 100f) * screenRatio + anim.offsetXNorm * 2f * screenRatio
        val anchorY = -(layer.y / 100f) - anim.offsetYNorm * 2f
        val ox = when (layer.alignH) {
            TextAlignment.LEFT -> scaleX
            TextAlignment.RIGHT -> -scaleX
            TextAlignment.CENTER -> 0f
        }
        val oy = when (layer.alignV) {
            VerticalAlignment.TOP -> -scaleY
            VerticalAlignment.BOTTOM -> scaleY
            VerticalAlignment.MIDDLE -> 0f
        }
        val tx = anchorX + ox
        val ty = anchorY + oy

        val mvp = FloatArray(16)
        val proj = FloatArray(16)
        val model = FloatArray(16)
        Matrix.orthoM(proj, 0, -screenRatio, screenRatio, -1f, 1f, -1f, 1f)
        Matrix.setIdentityM(model, 0)
        Matrix.translateM(model, 0, tx, ty, 0f)
        Matrix.rotateM(model, 0, layer.rotation + anim.rotationDeg, 0f, 0f, 1f)
        Matrix.scaleM(model, 0, scaleX, scaleY, 1f)
        Matrix.multiplyMM(mvp, 0, proj, 0, model, 0)

        GLES20.glUseProgram(program)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)

        vertexBuffer.position(0)
        GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false, 16, vertexBuffer)
        GLES20.glEnableVertexAttribArray(positionHandle)
        vertexBuffer.position(2)
        GLES20.glVertexAttribPointer(texCoordHandle, 2, GLES20.GL_FLOAT, false, 16, vertexBuffer)
        GLES20.glEnableVertexAttribArray(texCoordHandle)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex.getTextureId())
        GLES20.glUniform1i(samplerHandle, 0)
        GLES20.glUniform1f(alphaHandle, alphaAnim)
        EffectEngine.bindColorFx(program, EffectEngine.paramsFor("wdg:" + id))
        if (modelHandle >= 0) {
            GLES20.glUniformMatrix4fv(modelHandle, 1, false, model, 0)
        }
        EffectEngine.setScreenRatio(screenRatio)
        EffectEngine.bindMaskFx(program, "wdg:" + id, screenRatio)

        GLES20.glUniformMatrix4fv(mvpHandle, 1, false, mvp, 0)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 6)

        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(texCoordHandle)
        GLES20.glDisable(GLES20.GL_BLEND)
    }

    private fun maybeRefresh(layer: WidgetLayer) {
        val now = SystemClock.elapsedRealtime()
        val last = lastEvalMs[layer.id] ?: 0L
        // Reloj/batería: ~1 Hz; si cambió fórmula se fuerza en rebuild
        if (now - last < 900L && layer.id in textures) return
        rebuildLayerBitmap(layer, force = false)
    }

    private fun rebuildLayerBitmap(layer: WidgetLayer, force: Boolean) {
        val ctx = context ?: return
        if (layer.widgetType == WidgetType.PROGRESS) {
            rebuildProgressBitmap(layer, force)
            return
        }
        val text = try {
            FormulaEngine.evaluate(layer.formula, ctx)
        } catch (_: Exception) {
            layer.formula
        }
        val styleKey = listOf(
            layer.widgetType,
            layer.textSize, layer.textColor, layer.borderWidth, layer.borderColor,
            layer.fontName, layer.iconFontName, layer.iconGlyphMap.entries.sortedBy { it.key },
            layer.enableFontVariations, layer.fontWidth, layer.fontWeight, layer.fontOpticalSize,
            layer.fontGrade, layer.fontSlant, layer.fontXopq, layer.fontYopq, layer.fontXtra,
            layer.fontYtuc, layer.fontYtlc, layer.fontYtas, layer.fontYtde, layer.fontYtfi,
            layer.alignH, layer.alignV
        ).joinToString("|")
        if (!force && text == lastText[layer.id] && styleKey == lastStyle[layer.id] && layer.id in textures) {
            lastEvalMs[layer.id] = SystemClock.elapsedRealtime()
            return
        }

        // textSize del usuario = px de diseño sobre altura de referencia 1920.
        // Así preview (superficie chica) y wallpaper (pantalla real) se ven igual.
        val designScale = (screenH / 1920f).coerceIn(0.25f, 4f)
        // Icon font: mapear nombres de glifo → unicode antes de medir/dibujar
        val useIcons = !layer.iconFontName.isNullOrBlank()
        val drawText = if (useIcons) {
            val glyphs = IconFontStorage.loadGlyphMap(ctx, layer.iconFontName!!)
            // Aplicar reasignaciones: RAIN → nombre de glifo elegido por el usuario
            val mapped = applyIconGlyphMap(text, layer.iconGlyphMap)
            IconFontStorage.resolveGlyphText(mapped, glyphs)
        } else {
            text
        }

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = layer.textColor
            textSize = layer.textSize.coerceIn(0f, 800f).coerceAtLeast(1f) * designScale
            textAlign = Paint.Align.LEFT
            typeface = try {
                if (useIcons) {
                    IconFontStorage.loadTypeface(ctx, layer.iconFontName!!)
                        ?: Typeface.DEFAULT
                } else {
                    val name = layer.fontName
                    val variation = if (layer.enableFontVariations) {
                        FontManager.buildVariationSettings(
                            width = layer.fontWidth,
                            weight = layer.fontWeight,
                            opticalSize = layer.fontOpticalSize,
                            grade = layer.fontGrade,
                            slant = layer.fontSlant,
                            xopq = layer.fontXopq,
                            yopq = layer.fontYopq,
                            xtra = layer.fontXtra,
                            ytuc = layer.fontYtuc,
                            ytlc = layer.fontYtlc,
                            ytas = layer.fontYtas,
                            ytde = layer.fontYtde,
                            ytfi = layer.fontYtfi
                        )
                    } else null
                    if (!name.isNullOrBlank()) {
                        FontManager.loadTypeface(ctx, name, variation) ?: Typeface.DEFAULT
                    } else {
                        Typeface.DEFAULT
                    }
                }
            } catch (_: Exception) {
                Typeface.DEFAULT
            }
        }
        val borderPxForPad = (layer.borderWidth * designScale).coerceAtLeast(0f)
        val pad = ((paint.textSize * 0.25f) + borderPxForPad * 2f).toInt().coerceAtLeast(4)
        val lines = drawText.lines()
        val fm = paint.fontMetrics
        val lineHeight = (fm.bottom - fm.top)
        val maxLineW = lines.maxOfOrNull { paint.measureText(it) } ?: 0f
        val tw = (maxLineW + pad * 2).toInt().coerceAtLeast(1)
        val th = (lineHeight * lines.size + pad * 2).toInt().coerceAtLeast(1)

        val bmp = Bitmap.createBitmap(tw, th, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.TRANSPARENT)
        val borderPx = (layer.borderWidth * designScale).coerceAtLeast(0f)
        var y = pad - fm.top
        for (line in lines) {
            val lineW = paint.measureText(line)
            val x = when (layer.alignH) {
                TextAlignment.LEFT -> pad.toFloat()
                TextAlignment.RIGHT -> (tw - pad).toFloat() - lineW
                TextAlignment.CENTER -> (tw - lineW) / 2f
            }
            if (borderPx > 0.5f) {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = borderPx * 2f
                paint.color = layer.borderColor
                canvas.drawText(line, x, y, paint)
            }
            paint.style = Paint.Style.FILL
            paint.strokeWidth = 0f
            paint.color = layer.textColor
            canvas.drawText(line, x, y, paint)
            y += lineHeight
        }

        val existing = textures[layer.id]
        if (existing != null) {
            existing.upload(bmp)
        } else {
            val tex = GLTexture()
            tex.initialize()
            tex.upload(bmp)
            textures[layer.id] = tex
        }
        if (!bmp.isRecycled) bmp.recycle()

        bitmapSize[layer.id] = tw to th
        lastText[layer.id] = text
        lastStyle[layer.id] = styleKey
        lastEvalMs[layer.id] = SystemClock.elapsedRealtime()
    }

    /**
     * Rasteriza barra / anillo de progreso según [WidgetLayer] tipo PROGRESS.
     * Nivel se normaliza entre min y max de las fórmulas evaluadas.
     */
    private fun rebuildProgressBitmap(layer: WidgetLayer, force: Boolean) {
        val ctx = context ?: return
        val minV = evalProgressNumber(layer.progressMinFormula, ctx, 0f)
        val maxV = evalProgressNumber(layer.progressMaxFormula, ctx, 100f)
        val levelV = evalProgressNumber(layer.progressLevelFormula, ctx, 0f)
        val range = (maxV - minV).let { if (kotlin.math.abs(it) < 1e-6f) 1f else it }
        val fraction = ((levelV - minV) / range).coerceIn(0f, 1f)

        val styleKey = listOf(
            "PROGRESS",
            layer.progressMinFormula, layer.progressMaxFormula, layer.progressLevelFormula,
            layer.progressFillMode, layer.progressSections, layer.progressShape,
            layer.progressSize, layer.progressThickness, layer.progressColorMode,
            layer.progressFgColor, layer.progressGrColor, layer.progressBgColor,
            // incluir valores numéricos para refrescar cuando cambian batería/etc.
            minV, maxV, levelV
        ).joinToString("|")
        val textKey = "p:$fraction"
        if (!force && textKey == lastText[layer.id] && styleKey == lastStyle[layer.id] && layer.id in textures) {
            lastEvalMs[layer.id] = SystemClock.elapsedRealtime()
            return
        }

        val designScale = (screenH / 1920f).coerceIn(0.25f, 4f)
        val sizePx = (layer.progressSize.coerceIn(20f, 2000f) * designScale).toInt().coerceAtLeast(8)
        val thickPx = (layer.progressThickness.coerceIn(2f, 500f) * designScale).toInt().coerceAtLeast(2)
        val sections = layer.progressSections.coerceIn(2, 100)
        val gapRatio = 0.25f // espacio entre segmentos en modo dividido
        // Borde exterior crece hacia afuera → padding del bitmap
        val borderPx = (layer.progressBorderWidth.coerceIn(0f, 50f) * designScale).toInt().coerceAtLeast(0)
        val pad = borderPx // margen en cada lado para que el stroke no se recorte

        val bmp: Bitmap
        val canvas: Canvas
        when (layer.progressShape) {
            ProgressShape.LINEAR -> {
                val w = sizePx
                val h = thickPx
                val bw = (w + pad * 2).coerceAtLeast(1)
                val bh = (h + pad * 2).coerceAtLeast(1)
                bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
                canvas = Canvas(bmp)
                canvas.drawColor(Color.TRANSPARENT)
                canvas.save()
                canvas.translate(pad.toFloat(), pad.toFloat())
                drawLinearProgress(
                    canvas, w.toFloat(), h.toFloat(), fraction, sections, gapRatio, layer
                )
                canvas.restore()
                if (borderPx > 0) {
                    val stroke = borderPx.toFloat()
                    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        style = Paint.Style.STROKE
                        strokeWidth = stroke
                        color = layer.progressBorderColor
                    }
                    // Rect centrado en el área del progreso + mitad del stroke hacia afuera
                    val half = stroke / 2f
                    canvas.drawRect(
                        pad - half, pad - half,
                        pad + w + half, pad + h + half,
                        borderPaint
                    )
                }
            }
            ProgressShape.CIRCULAR -> {
                val d = sizePx
                val bw = (d + pad * 2).coerceAtLeast(1)
                val bh = bw
                bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
                canvas = Canvas(bmp)
                canvas.drawColor(Color.TRANSPARENT)
                canvas.save()
                canvas.translate(pad.toFloat(), pad.toFloat())
                drawCircularProgress(
                    canvas, d.toFloat(), thickPx.toFloat(), fraction, sections, gapRatio, layer
                )
                canvas.restore()
                if (borderPx > 0) {
                    val stroke = borderPx.toFloat()
                    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        style = Paint.Style.STROKE
                        strokeWidth = stroke
                        color = layer.progressBorderColor
                    }
                    val cx = pad + d / 2f
                    val cy = pad + d / 2f
                    // Contorno exterior del anillo (radio exterior + mitad del stroke)
                    val outerR = d / 2f + stroke / 2f
                    canvas.drawCircle(cx, cy, outerR, borderPaint)
                }
            }
        }

        val outW: Int = bmp.width
        val outH: Int = bmp.height
        val existing = textures[layer.id]
        if (existing != null) {
            existing.upload(bmp)
        } else {
            val tex = GLTexture()
            tex.initialize()
            tex.upload(bmp)
            textures[layer.id] = tex
        }
        if (!bmp.isRecycled) bmp.recycle()

        bitmapSize[layer.id] = outW to outH
        lastText[layer.id] = textKey
        lastStyle[layer.id] = styleKey
        lastEvalMs[layer.id] = SystemClock.elapsedRealtime()
    }

    private fun evalProgressNumber(formula: String, ctx: Context, fallback: Float): Float {
        val raw = try {
            FormulaEngine.evaluate(formula, ctx)
        } catch (_: Exception) {
            formula
        }
        // Extraer primer número (soporta "57", "57%", "57.5", etc.)
        val m = Regex("""-?\d+(?:[.,]\d+)?""").find(raw.replace(',', '.'))
        return m?.value?.toFloatOrNull() ?: fallback
    }

    private fun drawLinearProgress(
        canvas: Canvas,
        w: Float,
        h: Float,
        fraction: Float,
        sections: Int,
        gapRatio: Float,
        layer: WidgetLayer
    ) {
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = layer.progressBgColor
        }
        val fgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = layer.progressFgColor
        }

        when (layer.progressFillMode) {
            ProgressFillMode.FLAT -> {
                canvas.drawRect(0f, 0f, w, h, bgPaint)
                val fw = w * fraction
                if (fw > 0.5f) {
                    if (layer.progressColorMode == ProgressColorMode.GRADIENT) {
                        fgPaint.shader = android.graphics.LinearGradient(
                            0f, 0f, w, 0f,
                            layer.progressFgColor, layer.progressGrColor,
                            android.graphics.Shader.TileMode.CLAMP
                        )
                    }
                    canvas.drawRect(0f, 0f, fw, h, fgPaint)
                    fgPaint.shader = null
                }
            }
            ProgressFillMode.DIVIDED -> {
                // Segmentos completos: ceil(fraction * n) segmentos llenos (sin medias pintadas)
                val n = sections.coerceAtLeast(1)
                val gap = (w / n) * gapRatio
                val seg = (w - gap * (n - 1)) / n
                val filledCount = if (fraction <= 0f) 0
                else kotlin.math.ceil((fraction * n) - 1e-6).toInt().coerceIn(0, n)
                for (i in 0 until n) {
                    val left = i * (seg + gap)
                    val right = left + seg
                    canvas.drawRect(left, 0f, right, h, bgPaint)
                    if (i < filledCount) {
                        if (layer.progressColorMode == ProgressColorMode.GRADIENT) {
                            val t0 = i.toFloat() / n
                            val t1 = (i + 1).toFloat() / n
                            val c0 = lerpColor(layer.progressFgColor, layer.progressGrColor, t0)
                            val c1 = lerpColor(layer.progressFgColor, layer.progressGrColor, t1)
                            fgPaint.shader = android.graphics.LinearGradient(
                                left, 0f, right, 0f, c0, c1,
                                android.graphics.Shader.TileMode.CLAMP
                            )
                        } else {
                            fgPaint.shader = null
                            fgPaint.color = layer.progressFgColor
                        }
                        canvas.drawRect(left, 0f, right, h, fgPaint)
                        fgPaint.shader = null
                    }
                }
            }
        }
    }

    private fun drawCircularProgress(
        canvas: Canvas,
        diameter: Float,
        stroke: Float,
        fraction: Float,
        sections: Int,
        gapRatio: Float,
        layer: WidgetLayer
    ) {
        val cx = diameter / 2f
        val cy = diameter / 2f
        val strokeW = stroke.coerceAtMost(diameter * 0.45f)
        val radius = (diameter - strokeW) / 2f
        val oval = RectF(cx - radius, cy - radius, cx + radius, cy + radius)

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = strokeW
            strokeCap = Paint.Cap.BUTT
            color = layer.progressBgColor
        }
        val fgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = strokeW
            strokeCap = Paint.Cap.BUTT
            color = layer.progressFgColor
        }

        // Ángulo: 0° = arriba, sentido horario (como KLWP / reloj)
        fun drawArcSweep(paint: Paint, startDeg: Float, sweepDeg: Float) {
            if (kotlin.math.abs(sweepDeg) < 0.1f) return
            // Canvas: 0° = derecha, antihorario positivo → ajustar
            val canvasStart = startDeg - 90f
            canvas.drawArc(oval, canvasStart, sweepDeg, false, paint)
        }

        when (layer.progressFillMode) {
            ProgressFillMode.FLAT -> {
                drawArcSweep(bgPaint, 0f, 360f)
                val sweep = 360f * fraction
                if (sweep > 0.1f) {
                    if (layer.progressColorMode == ProgressColorMode.GRADIENT) {
                        // SweepGradient: 0 = este (derecha); rotamos -90° para empezar arriba
                        val shader = SweepGradient(
                            cx, cy,
                            intArrayOf(layer.progressFgColor, layer.progressGrColor, layer.progressFgColor),
                            floatArrayOf(0f, fraction.coerceIn(0.01f, 1f), 1f)
                        )
                        val m = android.graphics.Matrix()
                        m.postRotate(-90f, cx, cy)
                        shader.setLocalMatrix(m)
                        fgPaint.shader = shader
                    }
                    drawArcSweep(fgPaint, 0f, sweep)
                    fgPaint.shader = null
                }
            }
            ProgressFillMode.DIVIDED -> {
                // Segmentos completos (sin arcos parciales)
                val n = sections.coerceAtLeast(1)
                val gapDeg = (360f / n) * gapRatio
                val segDeg = (360f / n) - gapDeg
                val filledCount = if (fraction <= 0f) 0
                else kotlin.math.ceil((fraction * n) - 1e-6).toInt().coerceIn(0, n)
                for (i in 0 until n) {
                    val start = i * (segDeg + gapDeg)
                    drawArcSweep(bgPaint, start, segDeg)
                    if (i < filledCount) {
                        if (layer.progressColorMode == ProgressColorMode.GRADIENT) {
                            val tt = (i + 0.5f) / n
                            fgPaint.shader = null
                            fgPaint.color = lerpColor(layer.progressFgColor, layer.progressGrColor, tt)
                        } else {
                            fgPaint.shader = null
                            fgPaint.color = layer.progressFgColor
                        }
                        drawArcSweep(fgPaint, start, segDeg)
                    }
                }
            }
        }
    }

    private fun lerpColor(c0: Int, c1: Int, t: Float): Int {
        val a = t.coerceIn(0f, 1f)
        val a0 = (c0 ushr 24) and 0xFF
        val r0 = (c0 ushr 16) and 0xFF
        val g0 = (c0 ushr 8) and 0xFF
        val b0 = c0 and 0xFF
        val a1 = (c1 ushr 24) and 0xFF
        val r1 = (c1 ushr 16) and 0xFF
        val g1 = (c1 ushr 8) and 0xFF
        val b1 = c1 and 0xFF
        val aa = (a0 + (a1 - a0) * a).toInt().coerceIn(0, 255)
        val rr = (r0 + (r1 - r0) * a).toInt().coerceIn(0, 255)
        val gg = (g0 + (g1 - g0) * a).toInt().coerceIn(0, 255)
        val bb = (b0 + (b1 - b0) * a).toInt().coerceIn(0, 255)
        return (aa shl 24) or (rr shl 16) or (gg shl 8) or bb
    }

    private fun layerFadeAlpha(layer: WidgetLayer): Float {
        val start = fadeStartTimes[layer.id] ?: return 1f
        if (start < 0L) return 0f
        if (start == 0L) return 1f
        val now = SystemClock.elapsedRealtime()
        if (now < start) return 0f
        val dur = layer.fadeDurationMs.coerceAtLeast(1L).toFloat()
        val t = ((now - start).toFloat() / dur).coerceIn(0f, 1f)
        if (t >= 1f) fadeStartTimes[layer.id] = 0L
        return t
    }

    fun isFadeComplete(): Boolean {
        val layers = ProjectManager.getProject().widgetLayers
        if (layers.isEmpty()) return true
        return layers.all {
            forceHidden[it.id] == true || layerFadeAlpha(it) >= 0.999f
        }
    }

    fun startSoftStartAll() {
        val now = SystemClock.elapsedRealtime()
        ProjectManager.getProject().widgetLayers.forEach { layer ->
            if (forceHidden[layer.id] == true) return@forEach
            val delay = layer.delayStartMs.coerceAtLeast(0L)
            fadeStartTimes[layer.id] = if (delay > 0) now + delay else now
            // delay: usamos start futuro y layerFadeAlpha trata start>now como 0
            if (delay > 0) {
                // Marca especial: negativo = esperando; simplificado: start = now+delay
                // layerFadeAlpha si start > now → 0
            }
        }
    }


    /** Aplica mapa weatherKey → glyphName (case-insensitive en la clave). */
    private fun applyIconGlyphMap(text: String, map: Map<String, String>): String {
        if (map.isEmpty()) return text
        val trimmed = text.trim()
        map[trimmed]?.let { return it }
        map.entries.firstOrNull { it.key.equals(trimmed, ignoreCase = true) }?.let {
            return it.value
        }
        // tokenizar por espacios
        return trimmed.split(Regex("\\s+")).joinToString(" ") { token ->
            map.entries.firstOrNull { it.key.equals(token, ignoreCase = true) }?.value
                ?: token
        }
    }

    /** Visible según flags de bloqueo / launcher. */
    private fun shouldShow(layer: WidgetLayer, deviceLocked: Boolean): Boolean {
        if (deviceLocked && layer.disableOnLockScreen) return false
        if (!deviceLocked && layer.disableOnLauncher) return false
        return true
    }

    fun applyLockScreenState(deviceLocked: Boolean) {
        lastDeviceLocked = deviceLocked
        ProjectManager.getProject().widgetLayers.forEach { layer ->
            forceHidden[layer.id] = !shouldShow(layer, deviceLocked)
        }
    }

    fun startSoftStartOnLockScreen() {
        lastDeviceLocked = true
        val now = SystemClock.elapsedRealtime()
        ProjectManager.getProject().widgetLayers.forEach { layer ->
            if (shouldShow(layer, deviceLocked = true)) {
                forceHidden[layer.id] = false
                fadeStartTimes[layer.id] = now + layer.delayStartMs.coerceAtLeast(0L)
            } else {
                forceHidden[layer.id] = true
            }
        }
    }

    /**
     * Al desbloquear:
     * - visible en lock y launcher → sin re-fade (ya se veía)
     * - oculto en lock (disableOnLockScreen) y visible en launcher → Soft Start
     * - oculto en launcher (disableOnLauncher) → hidden
     */
    fun revealAfterUnlock() {
        lastDeviceLocked = false
        val now = SystemClock.elapsedRealtime()
        ProjectManager.getProject().widgetLayers.forEach { layer ->
            if (!shouldShow(layer, deviceLocked = false)) {
                forceHidden[layer.id] = true
                fadeStartTimes.remove(layer.id)
            } else {
                forceHidden[layer.id] = false
                if (layer.disableOnLockScreen) {
                    // Estaba oculto en bloqueo → fade al desbloquear
                    fadeStartTimes[layer.id] = now + layer.delayStartMs.coerceAtLeast(0L)
                } else {
                    // Ya visible en lock screen → no reiniciar soft start
                    fadeStartTimes.remove(layer.id)
                }
            }
        }
    }

    fun release() {
        textures.values.forEach { it.release() }
        textures.clear()
        bitmapSize.clear()
        lastText.clear()
        lastStyle.clear()
        lastEvalMs.clear()
        fadeStartTimes.clear()
        forceHidden.clear()
        if (program != 0) {
            GLES20.glDeleteProgram(program)
            program = 0
        }
        context = null
    }

    private fun loadShader(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        return shader
    }
}
