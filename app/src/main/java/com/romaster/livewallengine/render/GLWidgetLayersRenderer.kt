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
import com.romaster.livewallengine.font.FontManager
import com.romaster.livewallengine.font.IconFontStorage
import com.romaster.livewallengine.model.WidgetLayer
import com.romaster.livewallengine.project.ProjectManager
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
        varying vec2 vTexCoord;
        void main() {
            gl_Position = uMVP * vec4(aPosition, 0.0, 1.0);
            vTexCoord = aTexCoord;
        }
    """.trimIndent()

    private val fShader = """
        precision mediump float;
        uniform sampler2D uTexture;
        uniform float uAlpha;
        varying vec2 vTexCoord;
        void main() {
            vec4 c = texture2D(uTexture, vTexCoord);
            gl_FragColor = vec4(c.rgb, c.a * uAlpha);
        }
    """.trimIndent()

    fun initialize(ctx: Context, width: Int, height: Int) {
        context = ctx.applicationContext
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

        // Misma proyección ortográfica que Pics-OL para no deformar al rotar.
        // Escala en unidades de altura de pantalla → aspect ratio del bitmap se conserva.
        val screenRatio = screenW.toFloat() / screenH.toFloat().coerceAtLeast(1f)
        val zoom = layer.zoom.coerceIn(0.05f, 10f)
        val scaleX = (bw.toFloat() / screenH) * zoom
        val scaleY = (bh.toFloat() / screenH) * zoom

        val tx = (layer.x.coerceIn(0f, 1f) - 0.5f) * 2f * screenRatio
        val ty = (0.5f - layer.y.coerceIn(0f, 1f)) * 2f

        val mvp = FloatArray(16)
        val proj = FloatArray(16)
        val model = FloatArray(16)
        Matrix.orthoM(proj, 0, -screenRatio, screenRatio, -1f, 1f, -1f, 1f)
        Matrix.setIdentityM(model, 0)
        Matrix.translateM(model, 0, tx, ty, 0f)
        Matrix.rotateM(model, 0, layer.rotation, 0f, 0f, 1f)
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
        GLES20.glUniform1f(alphaHandle, alpha)
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
        val text = try {
            FormulaEngine.evaluate(layer.formula, ctx)
        } catch (_: Exception) {
            layer.formula
        }
        val styleKey = listOf(
            layer.textSize, layer.textColor, layer.borderWidth, layer.borderColor,
            layer.fontName, layer.useIconFont, layer.iconFontName
        ).joinToString("|")
        if (!force && text == lastText[layer.id] && styleKey == lastStyle[layer.id] && layer.id in textures) {
            lastEvalMs[layer.id] = SystemClock.elapsedRealtime()
            return
        }

        // textSize del usuario = px de diseño sobre altura de referencia 1920.
        // Así preview (superficie chica) y wallpaper (pantalla real) se ven igual.
        val designScale = (screenH / 1920f).coerceIn(0.25f, 4f)
        // Icon font: mapear nombres de glifo → unicode antes de medir/dibujar
        val drawText = if (layer.useIconFont && !layer.iconFontName.isNullOrBlank()) {
            val glyphs = IconFontStorage.loadGlyphMap(ctx, layer.iconFontName!!)
            IconFontStorage.resolveGlyphText(text, glyphs)
        } else {
            text
        }

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = layer.textColor
            textSize = layer.textSize.coerceIn(0f, 800f).coerceAtLeast(1f) * designScale
            textAlign = Paint.Align.LEFT
            typeface = try {
                if (layer.useIconFont && !layer.iconFontName.isNullOrBlank()) {
                    IconFontStorage.loadTypeface(ctx, layer.iconFontName!!)
                        ?: Typeface.DEFAULT
                } else {
                    val name = layer.fontName
                    if (!name.isNullOrBlank()) {
                        FontManager.loadTypeface(ctx, name) ?: Typeface.DEFAULT
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
            if (borderPx > 0.5f) {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = borderPx * 2f
                paint.color = layer.borderColor
                canvas.drawText(line, pad.toFloat(), y, paint)
            }
            paint.style = Paint.Style.FILL
            paint.strokeWidth = 0f
            paint.color = layer.textColor
            canvas.drawText(line, pad.toFloat(), y, paint)
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

    fun revealAfterUnlock() {
        lastDeviceLocked = false
        val now = SystemClock.elapsedRealtime()
        ProjectManager.getProject().widgetLayers.forEach { layer ->
            if (shouldShow(layer, deviceLocked = false)) {
                forceHidden[layer.id] = false
                fadeStartTimes[layer.id] = now + layer.delayStartMs.coerceAtLeast(0L)
            } else {
                forceHidden[layer.id] = true
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
