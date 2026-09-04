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
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * Capa de fondo difuminado bajo el reloj.
 *
 * El blur se dibuja OPACO dentro de la forma de los glifos (máscara reconstruida
 * sin la transparencia del cristal). El reloj coloreado va encima y, al subir
 * la transparencia del cristal, deja ver este blur sin que el blur se desvanezca.
 */
class ClockBackdropBlur {

    private var texId = 0
    private var program = 0
    private var posHandle = 0
    private var uvHandle = 0
    private var blurSampler = 0
    private var maskSampler = 0
    private var alphaHandle = 0
    private var glassFactorHandle = 0
    private var quadBuf: FloatBuffer? = null
    private var hasTexture = false
    private var initialized = false

    fun initialize() {
        if (initialized) return
        val ids = IntArray(1)
        GLES20.glGenTextures(1, ids, 0)
        texId = ids[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

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
            uniform sampler2D uBlur;
            uniform sampler2D uMask;
            uniform float uAlpha;
            uniform float uGlassFactor;
            void main() {
              float ca = texture2D(uMask, vUv).a;
              // Reconstruir cobertura del glifo SIN la transparencia del cristal:
              // en el bitmap, a ≈ coverage * glassFactor
              float coverage = clamp(ca / max(uGlassFactor, 0.04), 0.0, 1.0);
              float a = coverage * uAlpha;
              if (a < 0.004) {
                gl_FragColor = vec4(0.0);
                return;
              }
              vec3 rgb = texture2D(uBlur, vUv).rgb;
              // Premultiplicado, opaco en el interior del glifo
              gl_FragColor = vec4(rgb * a, a);
            }
            """.trimIndent()
        )
        posHandle = GLES20.glGetAttribLocation(program, "aPos")
        uvHandle = GLES20.glGetAttribLocation(program, "aUv")
        blurSampler = GLES20.glGetUniformLocation(program, "uBlur")
        maskSampler = GLES20.glGetUniformLocation(program, "uMask")
        alphaHandle = GLES20.glGetUniformLocation(program, "uAlpha")
        glassFactorHandle = GLES20.glGetUniformLocation(program, "uGlassFactor")
        quadBuf = ByteBuffer.allocateDirect(24 * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
        initialized = true
    }

    fun upload(bitmap: Bitmap) {
        if (!initialized) initialize()
        if (bitmap.isRecycled || texId == 0) return
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
        hasTexture = true
    }

    fun clear() {
        hasTexture = false
    }

    fun hasContent(): Boolean = hasTexture && texId != 0

    /**
     * Capa de blur opaca en la forma del reloj.
     * @param glassFactor mismo factor que usa ClockRenderer (0.08…0.92)
     * @param alpha solo soft-start del reloj (NO la transparencia del cristal)
     */
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

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId)
        GLES20.glUniform1i(blurSampler, 0)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, maskTexId)
        GLES20.glUniform1i(maskSampler, 1)

        GLES20.glUniform1f(alphaHandle, alpha.coerceIn(0f, 1f))
        GLES20.glUniform1f(glassFactorHandle, glassFactor.coerceIn(0.04f, 1f))
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 6)

        GLES20.glDisableVertexAttribArray(posHandle)
        GLES20.glDisableVertexAttribArray(uvHandle)
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
        if (texId != 0) GLES20.glDeleteTextures(1, intArrayOf(texId), 0)
        if (program != 0) GLES20.glDeleteProgram(program)
        texId = 0
        program = 0
        hasTexture = false
        initialized = false
    }

    companion object {
        /** Radios suaves tipo cristal esmerilado (no exagerados). */
        fun radiusForLevel(level: Int): Int = when (level) {
            1 -> 1
            2 -> 2
            3 -> 3
            else -> 0
        }

        /** Mismo cálculo que ClockRenderer para el alfa del cristal. */
        fun glassFactorFromSettings(crystalBlur: Float): Float {
            val blur = crystalBlur.coerceIn(0f, 50f)
            return (0.92f - (blur / 50f) * 0.82f).coerceIn(0.08f, 1f)
        }

        fun processCapture(src: Bitmap, level: Int): Bitmap? {
            if (level <= 0 || src.isRecycled) return null
            val radius = radiusForLevel(level)
            if (radius <= 0) return null

            // Baja resolución: menos CPU y efecto de vidrio suave al estirar
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
            // Una sola pasada H+V: cristal suave, menos CPU
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
