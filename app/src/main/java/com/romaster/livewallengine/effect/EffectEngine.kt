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

import android.opengl.GLES20
import com.romaster.livewallengine.model.EffectType
import com.romaster.livewallengine.project.ProjectManager

/**
 * Agrega los efectos de color activos para una capa objetivo y expone
 * uniforms GLSL + helpers para los renderers.
 *
 * targetKey: "vbg" | "vol" | "ckol" | "img:<id>" | "wdg:<id>"
 */
object EffectEngine {

    data class ColorFxParams(
        /** 0 = B&N, 1 = normal, 2 = hiper (desde slider 0–200). */
        val saturation: Float = 1f,
        /** Desplazamiento de matiz en grados (−180…180). */
        val hueShift: Float = 0f,
        val invert: Boolean = false,
        val invertLumaPreserve: Boolean = false,
        /** −1 = off; 0…1 = umbral de solarizado. */
        val solarize: Float = -1f,
        val tintR: Float = 1f,
        val tintG: Float = 0f,
        val tintB: Float = 0f,
        /** 0…1 */
        val tintStrength: Float = 0f,
        /** 0 = mix, 1 = multiply */
        val tintMode: Int = 0
    ) {
        val hasAny: Boolean
            get() = saturation != 1f ||
                hueShift != 0f ||
                invert ||
                solarize >= 0f ||
                tintStrength > 0.001f
    }

    val IDENTITY = ColorFxParams()

    /**
     * Fragmento GLSL: uniforms de efectos de color.
     * Insertar junto al resto de uniforms del fragment shader.
     */
    const val GLSL_UNIFORMS = """
        uniform float uFxSat;
        uniform float uFxHue;
        uniform float uFxInvert;
        uniform float uFxInvertLuma;
        uniform float uFxSolar;
        uniform vec3  uFxTint;
        uniform float uFxTintStr;
        uniform float uFxTintMode;
    """

    /**
     * Funciones GLSL para sat / hue / tint / solarize / invert.
     * Llamar: `color.rgb = fxApplyColor(color.rgb);`
     */
    const val GLSL_FUNCS = """
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
                if (uFxTintMode > 0.5) {
                    c = mix(c, c * uFxTint, uFxTintStr);
                } else {
                    c = mix(c, uFxTint, uFxTintStr);
                }
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
    """

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
                EffectType.SATURATION -> {
                    sat = (e.saturation / 100f).coerceIn(0f, 2f)
                }
                EffectType.HUE -> {
                    hue = e.hueShift.coerceIn(-180f, 180f)
                }
                EffectType.INVERT -> {
                    invert = true
                    invertLuma = e.invertLumaPreserve
                }
                EffectType.SOLARIZE -> {
                    solar = (e.solarizeThreshold / 100f).coerceIn(0f, 1f)
                }
                EffectType.TINT -> {
                    val c = e.tintColor
                    tr = ((c shr 16) and 0xFF) / 255f
                    tg = ((c shr 8) and 0xFF) / 255f
                    tb = (c and 0xFF) / 255f
                    tStr = (e.tintStrength / 100f).coerceIn(0f, 1f)
                    tMode = e.tintMode.coerceIn(0, 1)
                }
                EffectType.CLIP_MASK -> {
                    // Etapa D
                }
            }
        }
        return ColorFxParams(sat, hue, invert, invertLuma, solar, tr, tg, tb, tStr, tMode)
    }

    /**
     * Envía uniforms al programa actual (debe estar en use).
     * Resuelve locations por nombre cada vez (barato frente al resto del frame).
     */
    fun bindColorFx(program: Int, p: ColorFxParams = IDENTITY) {
        if (program == 0) return
        fun loc(name: String) = GLES20.glGetUniformLocation(program, name)
        val sat = loc("uFxSat")
        if (sat >= 0) GLES20.glUniform1f(sat, p.saturation)
        val hue = loc("uFxHue")
        if (hue >= 0) GLES20.glUniform1f(hue, p.hueShift)
        val inv = loc("uFxInvert")
        if (inv >= 0) GLES20.glUniform1f(inv, if (p.invert) 1f else 0f)
        val invL = loc("uFxInvertLuma")
        if (invL >= 0) GLES20.glUniform1f(invL, if (p.invertLumaPreserve) 1f else 0f)
        val sol = loc("uFxSolar")
        if (sol >= 0) GLES20.glUniform1f(sol, p.solarize)
        val tint = loc("uFxTint")
        if (tint >= 0) GLES20.glUniform3f(tint, p.tintR, p.tintG, p.tintB)
        val tStr = loc("uFxTintStr")
        if (tStr >= 0) GLES20.glUniform1f(tStr, p.tintStrength)
        val tMode = loc("uFxTintMode")
        if (tMode >= 0) GLES20.glUniform1f(tMode, p.tintMode.toFloat())
    }
}
