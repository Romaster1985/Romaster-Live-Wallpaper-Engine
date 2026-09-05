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
import android.opengl.GLES20
import android.opengl.GLUtils
import android.os.SystemClock
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * Fondo difuminado bajo el reloj con crossfade entre actualizaciones.
 * Dos texturas en ping-pong: al llegar un blur nuevo se mezcla con el anterior.
 */
class ClockBackdropBlur {

    private var texA = 0
    private var texB = 0
    /** Índice de la textura "actual" (0 = A, 1 = B). */
    private var currentSlot = 0
    private var hasA = false
    private var hasB = false

    private var program = 0
    private var posHandle = 0
    private var uvHandle = 0
    private var blurOldSampler = 0
    private var blurNewSampler = 0
    private var maskSampler = 0
    private var alphaHandle = 0
    private var glassFactorHandle = 0
    private var crossHandle = 0
    private var quadBuf: FloatBuffer? = null
    private var initialized = false

    private var crossStartMs = 0L
    private var crossfading = false

    fun initialize() {
        if (initialized) return
        val ids = IntArray(2)
        GLES20.glGenTextures(2, ids, 0)
        texA = ids[0]
        texB = ids[1]
        for (id in ids) {
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, id)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        }
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)

        program = link(
            """
            attribute vec2 aPos;
            attribute vec2 aUv;
            varying vec2 vUv;
            void main() {
              vUv = aUv;
              gl_Position = vec4(aPos, 0.0, 1.0);
            }
            """.trimIndent(),
            """
            precision mediump float;
            varying vec2 vUv;
            uniform sampler2D uBlurOld;
            uniform sampler2D uBlurNew;
            uniform sampler2D uMask;
            uniform float uAlpha;
            uniform float uGlassFactor;
            uniform float uCross;
            void main() {
              float ca = texture2D(uMask, vUv).a;
              float coverage = clamp(ca / max(uGlassFactor, 0.04), 0.0, 1.0);
              float a = coverage * uAlpha;
              if (a < 0.004) {
                gl_FragColor = vec4(0.0);
                return;
              }
              vec3 oldC = texture2D(uBlurOld, vUv).rgb;
              vec3 newC = texture2D(uBlurNew, vUv).rgb;
              vec3 rgb = mix(oldC, newC, clamp(uCross, 0.0, 1.0));
              gl_FragColor = vec4(rgb * a, a);
            }
            """.trimIndent()
        )
        posHandle = GLES20.glGetAttribLocation(program, "aPos")
        uvHandle = GLES20.glGetAttribLocation(program, "aUv")
        blurOldSampler = GLES20.glGetUniformLocation(program, "uBlurOld")
        blurNewSampler = GLES20.glGetUniformLocation(program, "uBlurNew")
        maskSampler = GLES20.glGetUniformLocation(program, "uMask")
        alphaHandle = GLES20.glGetUniformLocation(program, "uAlpha")
        glassFactorHandle = GLES20.glGetUniformLocation(program, "uGlassFactor")
        crossHandle = GLES20.glGetUniformLocation(program, "uCross")
        quadBuf = ByteBuffer.allocateDirect(24 * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
        initialized = true
    }

    /**
     * Sube un blur nuevo. Si ya había uno, inicia crossfade hacia el nuevo.
     */
    fun upload(bitmap: Bitmap) {
        if (!initialized) initialize()
        if (bitmap.isRecycled) return

        val hadContent = hasContent()
        val targetSlot = if (!hadContent) {
            currentSlot
        } else {
            1 - currentSlot
        }
        val texId = if (targetSlot == 0) texA else texB
        if (texId == 0) return

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)

        if (targetSlot == 0) hasA = true else hasB = true

        if (!hadContent) {
            currentSlot = targetSlot
            crossfading = false
            crossStartMs = 0L
        } else {
            // currentSlot sigue siendo el viejo; target es el nuevo
            // al terminar el cross, currentSlot = targetSlot
            crossStartMs = SystemClock.elapsedRealtime()
            crossfading = true
            // Guardamos el slot nuevo en "pending" vía invertir al completar
            // Mientras crossfade: old = currentSlot, new = 1-currentSlot
            // Al completar: currentSlot = 1 - currentSlot
        }
    }

    fun clear() {
        hasA = false
        hasB = false
        crossfading = false
        crossStartMs = 0L
        currentSlot = 0
    }

    fun hasContent(): Boolean = (hasA || hasB) && (texA != 0 || texB != 0)

    /** 0 = solo textura actual; 1 = solo la nueva (tras el fundido). */
    private fun crossAmount(): Float {
        if (!crossfading) return 1f
        val elapsed = SystemClock.elapsedRealtime() - crossStartMs
        return (elapsed.toFloat() / CROSSFADE_MS).coerceIn(0f, 1f)
    }

    private fun finishCrossfadeIfNeeded(cross: Float) {
        if (crossfading && cross >= 1f) {
            crossfading = false
            currentSlot = 1 - currentSlot
        }
    }

    fun drawUnderlay(
        maskTexId: Int,
        leftNdc: Float,
        topNdc: Float,
        rightNdc: Float,
        bottomNdc: Float,
        alpha: Float,
        glassFactor: Float
    ) {
        if (!hasContent() || maskTexId == 0 || alpha <= 0.01f) return
        if (!initialized) initialize()

        val cross = crossAmount()
        // Durante el fade: old = current, new = el otro. Al terminar, current pasa a ser el nuevo.
        val oldSlot = currentSlot
        val newSlot = if (crossfading) 1 - currentSlot else currentSlot
        val oldTex = if (oldSlot == 0) texA else texB
        val newTex = if (newSlot == 0) texA else texB
        val oldOk = if (oldSlot == 0) hasA else hasB
        val newOk = if (newSlot == 0) hasA else hasB
        if (!oldOk && !newOk) return
        finishCrossfadeIfNeeded(cross)

        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        GLES20.glUseProgram(program)

        val buf = quadBuf ?: return
        buf.clear()
        val verts = floatArrayOf(
            leftNdc, bottomNdc, 0f, 1f,
            rightNdc, bottomNdc, 1f, 1f,
            leftNdc, topNdc, 0f, 0f,
            leftNdc, topNdc, 0f, 0f,
            rightNdc, bottomNdc, 1f, 1f,
            rightNdc, topNdc, 1f, 0f
        )
        buf.put(verts)
        buf.position(0)
        GLES20.glEnableVertexAttribArray(posHandle)
        GLES20.glVertexAttribPointer(posHandle, 2, GLES20.GL_FLOAT, false, 16, buf)
        buf.position(2)
        GLES20.glEnableVertexAttribArray(uvHandle)
        GLES20.glVertexAttribPointer(uvHandle, 2, GLES20.GL_FLOAT, false, 16, buf)

        // Si solo hay una textura válida, usar la misma en old y new
        val bindOld = if (oldOk) oldTex else newTex
        val bindNew = if (newOk) newTex else oldTex
        val uCross = if (oldOk && newOk && bindOld != bindNew) cross else 1f

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, bindOld)
        GLES20.glUniform1i(blurOldSampler, 0)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, bindNew)
        GLES20.glUniform1i(blurNewSampler, 1)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE2)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, maskTexId)
        GLES20.glUniform1i(maskSampler, 2)

        GLES20.glUniform1f(alphaHandle, alpha.coerceIn(0f, 1f))
        GLES20.glUniform1f(glassFactorHandle, glassFactor.coerceIn(0.04f, 1f))
        GLES20.glUniform1f(crossHandle, uCross)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 6)

        GLES20.glDisableVertexAttribArray(posHandle)
        GLES20.glDisableVertexAttribArray(uvHandle)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE2)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
        GLES20.glUseProgram(0)
    }

    private fun link(vsSrc: String, fsSrc: String): Int {
        val vs = compile(GLES20.GL_VERTEX_SHADER, vsSrc)
        val fs = compile(GLES20.GL_FRAGMENT_SHADER, fsSrc)
        val prog = GLES20.glCreateProgram()
        GLES20.glAttachShader(prog, vs)
        GLES20.glAttachShader(prog, fs)
        GLES20.glLinkProgram(prog)
        GLES20.glDeleteShader(vs)
        GLES20.glDeleteShader(fs)
        return prog
    }

    private fun compile(type: Int, src: String): Int {
        val s = GLES20.glCreateShader(type)
        GLES20.glShaderSource(s, src)
        GLES20.glCompileShader(s)
        return s
    }

    fun release() {
        if (!initialized) return
        val ids = intArrayOf(texA, texB).filter { it != 0 }.toIntArray()
        if (ids.isNotEmpty()) GLES20.glDeleteTextures(ids.size, ids, 0)
        if (program != 0) GLES20.glDeleteProgram(program)
        texA = 0
        texB = 0
        program = 0
        hasA = false
        hasB = false
        crossfading = false
        initialized = false
    }

    companion object {
        /** Duración del fundido entre un blur y el siguiente. */
        const val CROSSFADE_MS = 600f

        fun radiusForLevel(level: Int): Int = when (level) {
            1 -> 1
            2 -> 2
            3 -> 3
            else -> 0
        }

        fun glassFactorFromSettings(crystalBlur: Float): Float {
            val blur = crystalBlur.coerceIn(0f, 50f)
            return (0.92f - (blur / 50f) * 0.82f).coerceIn(0.08f, 1f)
        }

        fun processCapture(src: Bitmap, level: Int): Bitmap? {
            if (level <= 0 || src.isRecycled) return null
            val radius = radiusForLevel(level)
            if (radius <= 0) return null

            val maxSide = 96
            val scale = minOf(1f, maxSide.toFloat() / maxOf(src.width, src.height).coerceAtLeast(1))
            val w = (src.width * scale).toInt().coerceAtLeast(8)
            val h = (src.height * scale).toInt().coerceAtLeast(8)
            val scaled = Bitmap.createScaledBitmap(src, w, h, true)
            val result = boxBlur(scaled, radius)
            if (scaled !== result && scaled !== src && !scaled.isRecycled) {
                scaled.recycle()
            }
            return result
        }

        private fun boxBlur(src: Bitmap, radius: Int): Bitmap {
            val w = src.width
            val h = src.height
            val pixels = IntArray(w * h)
            src.getPixels(pixels, 0, w, 0, 0, w, h)
            val tmp = IntArray(w * h)
            val r = radius.coerceIn(1, 24)
            blurPassH(pixels, tmp, w, h, r)
            blurPassV(tmp, pixels, w, h, r)
            val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            out.setPixels(pixels, 0, w, 0, 0, w, h)
            return out
        }

        private fun blurPassH(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int) {
            for (y in 0 until h) {
                for (x in 0 until w) {
                    var ar = 0; var ag = 0; var ab = 0; var aa = 0; var count = 0
                    val row = y * w
                    for (dx in -r..r) {
                        val p = src[row + (x + dx).coerceIn(0, w - 1)]
                        aa += (p ushr 24) and 0xFF
                        ar += (p ushr 16) and 0xFF
                        ag += (p ushr 8) and 0xFF
                        ab += p and 0xFF
                        count++
                    }
                    dst[row + x] =
                        ((aa / count) shl 24) or ((ar / count) shl 16) or
                            ((ag / count) shl 8) or (ab / count)
                }
            }
        }

        private fun blurPassV(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int) {
            for (x in 0 until w) {
                for (y in 0 until h) {
                    var ar = 0; var ag = 0; var ab = 0; var aa = 0; var count = 0
                    for (dy in -r..r) {
                        val p = src[(y + dy).coerceIn(0, h - 1) * w + x]
                        aa += (p ushr 24) and 0xFF
                        ar += (p ushr 16) and 0xFF
                        ag += (p ushr 8) and 0xFF
                        ab += p and 0xFF
                        count++
                    }
                    dst[y * w + x] =
                        ((aa / count) shl 24) or ((ar / count) shl 16) or
                            ((ag / count) shl 8) or (ab / count)
                }
            }
        }
    }
}
