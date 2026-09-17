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
    private val lastEvalMs = mutableMapOf<String, Long>()
    private val fadeStartTimes = mutableMapOf<String, Long>()
    private val forceHidden = mutableMapOf<String, Boolean>()

    private var screenW = 1
    private var screenH = 1
    private var context: Context? = null

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
        screenW = width.coerceAtLeast(1)
        screenH = height.coerceAtLeast(1)
    }

    fun reloadFromProject(force: Boolean = false) {
        val layers = ProjectManager.getProject().widgetLayers
        val ids = layers.map { it.id }.toSet()
        textures.keys.filter { it !in ids }.forEach { id ->
            textures.remove(id)?.release()
            bitmapSize.remove(id)
            lastText.remove(id)
            lastEvalMs.remove(id)
            fadeStartTimes.remove(id)
            forceHidden.remove(id)
        }
        layers.forEach { layer ->
            if (force || layer.id !in textures) {
                rebuildLayerBitmap(layer, force = true)
            }
        }
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

        val screenRatio = screenW.toFloat() / screenH.toFloat()
        val baseW = (bw.toFloat() / screenW) * 2f * layer.zoom
        val baseH = (bh.toFloat() / screenH) * 2f * layer.zoom
        // Ajuste por aspect de pantalla en X
        val w = baseW * screenRatio
        val h = baseH

        val cx = (layer.x * 2f - 1f) * screenRatio
        val cy = 1f - layer.y * 2f

        val mvp = FloatArray(16)
        Matrix.setIdentityM(mvp, 0)
        Matrix.translateM(mvp, 0, cx, cy, 0f)
        Matrix.rotateM(mvp, 0, layer.rotation, 0f, 0f, 1f)
        Matrix.scaleM(mvp, 0, w / 2f, h / 2f, 1f)

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
        if (!force && text == lastText[layer.id] && layer.id in textures) {
            lastEvalMs[layer.id] = SystemClock.elapsedRealtime()
            return
        }

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = layer.textColor
            textSize = layer.textSize.coerceIn(8f, 512f)
            textAlign = Paint.Align.LEFT
            typeface = Typeface.DEFAULT
            // TODO: cargar fontName / icon font cuando esté la galería Icons
        }
        val pad = (paint.textSize * 0.25f).toInt().coerceAtLeast(4)
        val tw = (paint.measureText(text) + pad * 2).toInt().coerceAtLeast(1)
        val fm = paint.fontMetrics
        val th = ((fm.bottom - fm.top) + pad * 2).toInt().coerceAtLeast(1)

        val bmp = Bitmap.createBitmap(tw, th, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.TRANSPARENT)
        val baseline = pad - fm.top
        canvas.drawText(text, pad.toFloat(), baseline, paint)

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

    fun applyLockScreenState(deviceLocked: Boolean) {
        ProjectManager.getProject().widgetLayers.forEach { layer ->
            if (layer.disableOnLockScreen && deviceLocked) {
                forceHidden[layer.id] = true
            } else if (layer.disableOnLockScreen && !deviceLocked) {
                // se revela con soft start al desbloquear
            } else {
                forceHidden[layer.id] = false
            }
        }
    }

    fun startSoftStartOnLockScreen() {
        val now = SystemClock.elapsedRealtime()
        ProjectManager.getProject().widgetLayers.forEach { layer ->
            if (!layer.disableOnLockScreen) {
                forceHidden[layer.id] = false
                fadeStartTimes[layer.id] = now + layer.delayStartMs.coerceAtLeast(0L)
            } else {
                forceHidden[layer.id] = true
            }
        }
    }

    fun revealAfterUnlock() {
        val now = SystemClock.elapsedRealtime()
        ProjectManager.getProject().widgetLayers.forEach { layer ->
            if (layer.disableOnLockScreen) {
                forceHidden[layer.id] = false
                fadeStartTimes[layer.id] = now + layer.delayStartMs.coerceAtLeast(0L)
            }
        }
    }

    fun release() {
        textures.values.forEach { it.release() }
        textures.clear()
        bitmapSize.clear()
        lastText.clear()
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
