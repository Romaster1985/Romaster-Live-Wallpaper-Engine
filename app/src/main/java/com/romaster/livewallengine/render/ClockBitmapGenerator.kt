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
import com.romaster.livewallengine.model.ClockSettings
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

class ClockBitmapGenerator(
    private val context: Context
) {

    private val clockRenderer = ClockRenderer()

    fun generate(
        width: Int,
        height: Int,
        settings: ClockSettings
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        clockRenderer.draw(context, canvas, settings)

        if (settings.glowEnabled) {
            val intensity = (settings.glowIntensity / 100f).coerceIn(0f, 1.5f)
            val radius = settings.glowRadius.toInt().coerceIn(1, 15)
            // Halo exterior solo sobre el ROI del texto (no blur a pantalla completa)
            applyOuterGlowInPlace(bitmap, intensity, radius)
        }
        return bitmap
    }

    /**
     * Brillo tipo resplandor exterior:
     * 1) busca el rectángulo del texto (píxeles con alpha)
     * 2) difumina solo el canal alpha de esa región
     * 3) halo = alphaDifuminado - alphaOriginal (solo fuera del glifo)
     * 4) suma blanco aditivo en ese halo
     *
     * Así se ve en tipografía blanca (el aditivo sobre el relleno blanco no se nota;
     * el efecto está en el contorno exterior) y no traba el preview.
     */
    private fun applyOuterGlowInPlace(bitmap: Bitmap, intensity: Float, radius: Int) {
        val bw = bitmap.width
        val bh = bitmap.height
        if (bw <= 0 || bh <= 0 || intensity <= 0.01f) return

        val full = IntArray(bw * bh)
        bitmap.getPixels(full, 0, bw, 0, 0, bw, bh)

        // Bounds del contenido
        var minX = bw
        var minY = bh
        var maxX = -1
        var maxY = -1
        for (y in 0 until bh) {
            val row = y * bw
            for (x in 0 until bw) {
                if (((full[row + x] ushr 24) and 0xFF) > 8) {
                    if (x < minX) minX = x
                    if (y < minY) minY = y
                    if (x > maxX) maxX = x
                    if (y > maxY) maxY = y
                }
            }
        }
        if (maxX < minX || maxY < minY) return

        val pad = (radius * 3 + 4).coerceAtLeast(8)
        val left = (minX - pad).coerceAtLeast(0)
        val top = (minY - pad).coerceAtLeast(0)
        val right = (maxX + pad).coerceAtMost(bw - 1)
        val bottom = (maxY + pad).coerceAtMost(bh - 1)
        val rw = right - left + 1
        val rh = bottom - top + 1
        if (rw <= 0 || rh <= 0) return

        // Alpha original de la región
        val alpha = FloatArray(rw * rh)
        for (y in 0 until rh) {
            val srcRow = (top + y) * bw
            val dstRow = y * rw
            for (x in 0 until rw) {
                alpha[dstRow + x] =
                    (((full[srcRow + left + x] ushr 24) and 0xFF) / 255f)
            }
        }

        val blurred = blurFloatSeparable(alpha, rw, rh, radius)

        // Componer halo exterior en el bitmap completo
        val strength = (intensity * 1.35f).coerceIn(0f, 2f)
        for (y in 0 until rh) {
            val srcRow = (top + y) * bw
            val localRow = y * rw
            for (x in 0 until rw) {
                val i = localRow + x
                val outer = (blurred[i] - alpha[i]).coerceAtLeast(0f) * strength
                if (outer < 0.004f) continue

                val idx = srcRow + left + x
                val pixel = full[idx]
                val oa = (pixel ushr 24) and 0xFF
                val or_ = (pixel ushr 16) and 0xFF
                val og = (pixel ushr 8) and 0xFF
                val ob = pixel and 0xFF

                val ga = (outer * 255f).toInt().coerceIn(0, 255)
                // Aditivo blanco
                val nr = min(255, or_ + ga)
                val ng = min(255, og + ga)
                val nb = min(255, ob + ga)
                val na = max(oa, ga)
                full[idx] = (na shl 24) or (nr shl 16) or (ng shl 8) or nb
            }
        }

        bitmap.setPixels(full, 0, bw, 0, 0, bw, bh)
    }

    private fun blurFloatSeparable(
        src: FloatArray,
        width: Int,
        height: Int,
        radius: Int
    ): FloatArray {
        val r = radius.coerceIn(1, 15)
        val kernelSize = r * 2 + 1
        val kernel = FloatArray(kernelSize)
        var sumK = 0f
        val sigma = r / 2f + 0.5f
        for (i in 0 until kernelSize) {
            val x = (i - r).toFloat()
            val v = exp(-(x * x) / (2f * sigma * sigma))
            kernel[i] = v
            sumK += v
        }
        for (i in 0 until kernelSize) kernel[i] /= sumK

        val tmp = FloatArray(src.size)
        val out = FloatArray(src.size)

        // Horizontal
        for (y in 0 until height) {
            val row = y * width
            for (x in 0 until width) {
                var acc = 0f
                for (k in 0 until kernelSize) {
                    val ix = (x + k - r).coerceIn(0, width - 1)
                    acc += src[row + ix] * kernel[k]
                }
                tmp[row + x] = acc
            }
        }
        // Vertical
        for (y in 0 until height) {
            for (x in 0 until width) {
                var acc = 0f
                for (k in 0 until kernelSize) {
                    val iy = (y + k - r).coerceIn(0, height - 1)
                    acc += tmp[iy * width + x] * kernel[k]
                }
                out[y * width + x] = acc
            }
        }
        return out
    }
}
