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

package com.romaster.livewallengine.effect

import android.content.Context
import android.graphics.BitmapFactory
import android.opengl.GLES20
import android.opengl.GLUtils
import com.romaster.livewallengine.image.MaskStorage
import com.romaster.livewallengine.model.EffectType
import com.romaster.livewallengine.project.ProjectManager
import java.util.concurrent.ConcurrentHashMap

/**
 * Agrega efectos de color y máscara de recorte para una capa objetivo.
 *
 * targetKey: "vbg" | "vol" | "ckol" | "img:<id>" | "wdg:<id>"
 */
object EffectEngine {

    data class ColorFxParams(
        val saturation: Float = 1f,
        val hueShift: Float = 0f,
        val invert: Boolean = false,
        val invertLumaPreserve: Boolean = false,
        val solarize: Float = -1f,
        val tintR: Float = 1f,
        val tintG: Float = 0f,
        val tintB: Float = 0f,
        val tintStrength: Float = 0f,
        val tintMode: Int = 0
    )

    data class MaskParams(
        val fileName: String,
        /** Centro en espacio ortográfico (mismo sistema que capas). */
        val centerX: Float,
        val centerY: Float,
        /** Escala lineal (1 = 100% → altura de pantalla completa). */
        val scale: Float,
        /** Rotación en radianes. */
        val rotationRad: Float,
        val invert: Boolean,
        val aspect: Float
    )

    val IDENTITY = ColorFxParams()

    // ---- Cache de texturas de máscara (hilo GL) ----
    private val maskTexIds = ConcurrentHashMap<String, Int>()
    private val maskAspect = ConcurrentHashMap<String, Float>()
    private val maskMtime = ConcurrentHashMap<String, Long>()
    @Volatile private var appContext: Context? = null
    /** Último aspect ratio de pantalla (width/height) reportado por los renderers. */
    @Volatile var lastScreenRatio: Float = 1f

    fun setContext(ctx: Context) {
        appContext = ctx.applicationContext
    }

    fun setScreenRatio(ratio: Float) {
        if (ratio > 0.01f) lastScreenRatio = ratio
    }

    fun paramsFor(targetKey: String): ColorFxParams {
        val list = try {
            ProjectManager.getProject().effectLayers.filter { it.targetKey == targetKey }
        } catch (_: Exception) {
            return IDENTITY
        }
        if (list.isEmpty()) return IDENTITY

        var sat = 1f
        var hue = 0f
        var invert = false
        var invertLuma = false
        var solar = -1f
        var tr = 1f
        var tg = 0f
        var tb = 0f
        var tStr = 0f
        var tMode = 0

        for (e in list) {
            when (e.type) {
                EffectType.SATURATION -> sat = (e.saturation / 100f).coerceIn(0f, 2f)
                EffectType.HUE -> hue = e.hueShift.coerceIn(-180f, 180f)
                EffectType.INVERT -> {
                    invert = true
                    invertLuma = e.invertLumaPreserve
                }
                EffectType.SOLARIZE -> solar = (e.solarizeThreshold / 100f).coerceIn(0f, 1f)
                EffectType.TINT -> {
                    val c = e.tintColor
                    tr = ((c shr 16) and 0xFF) / 255f
                    tg = ((c shr 8) and 0xFF) / 255f
                    tb = (c and 0xFF) / 255f
                    tStr = (e.tintStrength / 100f).coerceIn(0f, 1f)
                    tMode = e.tintMode.coerceIn(0, 1)
                }
                EffectType.CLIP_MASK -> { /* ver maskFor */ }
            }
        }
        return ColorFxParams(sat, hue, invert, invertLuma, solar, tr, tg, tb, tStr, tMode)
    }

    /**
     * Parámetros de máscara para [targetKey] (último CLIP_MASK con archivo válido).
     * [screenRatio] = width/height de la superficie de render.
     */
    fun maskFor(targetKey: String, screenRatio: Float): MaskParams? {
        val list = try {
            ProjectManager.getProject().effectLayers.filter {
                it.targetKey == targetKey && it.type == EffectType.CLIP_MASK &&
                    !it.maskFileName.isNullOrBlank()
            }
        } catch (_: Exception) {
            return null
        }
        val e = list.lastOrNull() ?: return null
        val name = e.maskFileName ?: return null
        val aspect = maskAspect[name] ?: 1f
        val scale = (e.maskScale / 100f).coerceIn(0.05f, 4f)
        val cx = (e.maskX / 100f) * screenRatio
        val cy = -(e.maskY / 100f)
        val rot = Math.toRadians(e.maskRotation.toDouble()).toFloat()
        return MaskParams(name, cx, cy, scale, rot, e.maskInvert, aspect)
    }

    /**
     * Asegura textura GL para la máscara. Llamar solo en hilo GL.
     * @return textureId o 0 si falló
     */
    fun ensureMaskTexture(fileName: String): Int {
        val ctx = appContext ?: return 0
        val file = MaskStorage.getMaskFile(ctx, fileName)
        if (!file.exists()) return 0
        val mtime = file.lastModified()
        maskTexIds[fileName]?.let { id ->
            if (id != 0 && maskMtime[fileName] == mtime) return id
            if (id != 0) GLES20.glDeleteTextures(1, intArrayOf(id), 0)
            maskTexIds.remove(fileName)
        }
        return try {
            val opts = BitmapFactory.Options().apply { inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888 }
            val bmp = BitmapFactory.decodeFile(file.absolutePath, opts) ?: return 0
            val aspect = if (bmp.height > 0) bmp.width.toFloat() / bmp.height.toFloat() else 1f
            maskAspect[fileName] = aspect
            val ids = IntArray(1)
            GLES20.glGenTextures(1, ids, 0)
            val id = ids[0]
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, id)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bmp, 0)
            bmp.recycle()
            maskTexIds[fileName] = id
            maskMtime[fileName] = mtime
            id
        } catch (_: Exception) {
            0
        }
    }

    fun invalidateMask(fileName: String?) {
        if (fileName.isNullOrBlank()) return
        maskTexIds.remove(fileName)?.let { id ->
            if (id != 0) GLES20.glDeleteTextures(1, intArrayOf(id), 0)
        }
        maskAspect.remove(fileName)
    }

    fun releaseMaskTextures() {
        for ((_, id) in maskTexIds) {
            if (id != 0) {
                GLES20.glDeleteTextures(1, intArrayOf(id), 0)
            }
        }
        maskTexIds.clear()
        maskAspect.clear()
    }

    fun bindColorFx(program: Int, p: ColorFxParams = IDENTITY) {
        if (program == 0) return
        fun loc(name: String) = GLES20.glGetUniformLocation(program, name)
        loc("uFxSat").takeIf { it >= 0 }?.let { GLES20.glUniform1f(it, p.saturation) }
        loc("uFxHue").takeIf { it >= 0 }?.let { GLES20.glUniform1f(it, p.hueShift) }
        loc("uFxInvert").takeIf { it >= 0 }?.let { GLES20.glUniform1f(it, if (p.invert) 1f else 0f) }
        loc("uFxInvertLuma").takeIf { it >= 0 }?.let { GLES20.glUniform1f(it, if (p.invertLumaPreserve) 1f else 0f) }
        loc("uFxSolar").takeIf { it >= 0 }?.let { GLES20.glUniform1f(it, p.solarize) }
        loc("uFxTint").takeIf { it >= 0 }?.let { GLES20.glUniform3f(it, p.tintR, p.tintG, p.tintB) }
        loc("uFxTintStr").takeIf { it >= 0 }?.let { GLES20.glUniform1f(it, p.tintStrength) }
        loc("uFxTintMode").takeIf { it >= 0 }?.let { GLES20.glUniform1f(it, p.tintMode.toFloat()) }
    }

    /**
     * Bindea máscara en TEXTURE1 y uniforms. Si no hay máscara, desactiva uMaskEnabled.
     */
    fun bindMaskFx(program: Int, targetKey: String, screenRatio: Float) {
        if (program == 0) return
        fun loc(name: String) = GLES20.glGetUniformLocation(program, name)
        val enabledLoc = loc("uMaskEnabled")
        val mask = maskFor(targetKey, screenRatio)
        if (mask == null) {
            if (enabledLoc >= 0) GLES20.glUniform1f(enabledLoc, 0f)
            return
        }
        val texId = ensureMaskTexture(mask.fileName)
        if (texId == 0) {
            if (enabledLoc >= 0) GLES20.glUniform1f(enabledLoc, 0f)
            return
        }
        if (enabledLoc >= 0) GLES20.glUniform1f(enabledLoc, 1f)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId)
        loc("uMask").takeIf { it >= 0 }?.let { GLES20.glUniform1i(it, 1) }
        loc("uMaskCenter").takeIf { it >= 0 }?.let { GLES20.glUniform2f(it, mask.centerX, mask.centerY) }
        loc("uMaskScale").takeIf { it >= 0 }?.let { GLES20.glUniform1f(it, mask.scale) }
        loc("uMaskAspect").takeIf { it >= 0 }?.let { GLES20.glUniform1f(it, mask.aspect) }
        loc("uMaskRot").takeIf { it >= 0 }?.let { GLES20.glUniform1f(it, mask.rotationRad) }
        loc("uMaskInvert").takeIf { it >= 0 }?.let { GLES20.glUniform1f(it, if (mask.invert) 1f else 0f) }
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
    }
}
