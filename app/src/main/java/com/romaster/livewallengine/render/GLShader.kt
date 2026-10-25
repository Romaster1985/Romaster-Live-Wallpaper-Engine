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

import com.romaster.livewallengine.effect.EffectEngine

import android.opengl.GLES20
import java.nio.FloatBuffer

class GLShader {

    private var program = 0

    private var positionHandle = 0

    private var texCoordHandle = 0

    private var samplerHandle = 0
    
    private var alphaHandle = 0

    private val vertexShaderCode =
        """
        attribute vec2 aPosition;
        attribute vec2 aTexCoord;

        varying vec2 vTexCoord;

        void main() {

            gl_Position =
                vec4(
                    aPosition,
                    0.0,
                    1.0
                );

            vTexCoord =
                aTexCoord;
        }
        """.trimIndent()

    private val fragmentShaderCode =
        """
        precision mediump float;

        varying vec2 vTexCoord;

        uniform sampler2D uTexture;

        uniform float uAlpha;

        uniform float uFxSat;
        uniform float uFxHue;
        uniform float uFxInvert;
        uniform float uFxInvertLuma;
        uniform float uFxSolar;
        uniform vec3  uFxTint;
        uniform float uFxTintStr;
        uniform float uFxTintMode;

        uniform sampler2D uMask;
        uniform float uMaskEnabled;
        uniform vec2 uMaskOffset;
        uniform float uMaskScale;
        uniform float uMaskRot;
        uniform float uMaskInvert;
        uniform float uMaskAspect;
        uniform float uLayerAspect;
        uniform float uMaskFit;

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
        float fxMaskAlpha() {
            if (uMaskEnabled < 0.5) return 1.0;
            vec2 p = vTexCoord - 0.5;
            p -= uMaskOffset;
            float la = max(uLayerAspect, 0.01);
            float ma = max(uMaskAspect, 0.01);
            vec2 s = vec2(p.x * la, p.y);
            float c0 = cos(-uMaskRot);
            float s0 = sin(-uMaskRot);
            s = vec2(c0 * s.x - s0 * s.y, s0 * s.x + c0 * s.y);
            float sc = max(uMaskScale, 0.001);
            s /= sc;
            vec2 uv;
            if (uMaskFit > 0.5) {
                uv = vec2(s.x / la, s.y) + 0.5;
            } else {
                uv = vec2(s.x / ma, s.y) + 0.5;
            }
            float m = 0.0;
            if (uv.x >= 0.0 && uv.x <= 1.0 && uv.y >= 0.0 && uv.y <= 1.0) {
                vec4 mt = texture2D(uMask, uv);
                float lum = max(mt.r, max(mt.g, mt.b));
                m = mt.a * lum;
            }
            if (uMaskInvert > 0.5) m = 1.0 - m;
            return m;
        }

        void main() {
            vec4 color = texture2D(uTexture, vTexCoord);
            color.rgb = fxApplyColor(color.rgb);
            float m = fxMaskAlpha();
            gl_FragColor = color * uAlpha * m;
        }
        """.trimIndent()

    fun initialize() {

        val vertexShader =
            compileShader(
                GLES20.GL_VERTEX_SHADER,
                vertexShaderCode
            )

        val fragmentShader =
            compileShader(
                GLES20.GL_FRAGMENT_SHADER,
                fragmentShaderCode
            )

        program =
            GLES20.glCreateProgram()

        GLES20.glAttachShader(
            program,
            vertexShader
        )

        GLES20.glAttachShader(
            program,
            fragmentShader
        )

        GLES20.glLinkProgram(
            program
        )

        positionHandle =
            GLES20.glGetAttribLocation(
                program,
                "aPosition"
            )

        texCoordHandle =
            GLES20.glGetAttribLocation(
                program,
                "aTexCoord"
            )

        samplerHandle =
            GLES20.glGetUniformLocation(
                program,
                "uTexture"
            )
        
        alphaHandle =
            GLES20.glGetUniformLocation(
                program,
                "uAlpha"
            )

        GLES20.glDeleteShader(
            vertexShader
        )

        GLES20.glDeleteShader(
            fragmentShader
        )
    }

    private fun compileShader(
        type: Int,
        source: String
    ): Int {

        val shader =
            GLES20.glCreateShader(type)

        GLES20.glShaderSource(
            shader,
            source
        )

        GLES20.glCompileShader(
            shader
        )

        val status = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) {
            val log = GLES20.glGetShaderInfoLog(shader)
            GLES20.glDeleteShader(shader)
            throw RuntimeException("GLShader compile error: $log")
        }

        return shader
    }

    fun use() {

        GLES20.glUseProgram(
            program
        )
    }

    fun draw(
        vertexBuffer: FloatBuffer,
        textureId: Int,
        alpha: Float,
        layerAspect: Float = 1f
    ) {

        vertexBuffer.position(0)

        GLES20.glVertexAttribPointer(
            positionHandle,
            2,
            GLES20.GL_FLOAT,
            false,
            16,
            vertexBuffer
        )

        GLES20.glEnableVertexAttribArray(
            positionHandle
        )

        vertexBuffer.position(2)

        GLES20.glVertexAttribPointer(
            texCoordHandle,
            2,
            GLES20.GL_FLOAT,
            false,
            16,
            vertexBuffer
        )

        GLES20.glEnableVertexAttribArray(
            texCoordHandle
        )

        GLES20.glActiveTexture(
            GLES20.GL_TEXTURE0
        )

        GLES20.glBindTexture(
            GLES20.GL_TEXTURE_2D,
            textureId
        )

        GLES20.glUniform1i(
            samplerHandle,
            0
        )
        
        GLES20.glUniform1f(
            alphaHandle,
            alpha
        )

        EffectEngine.bindColorFx(program, EffectEngine.paramsFor("ckol"))
        EffectEngine.bindMaskFx(program, "ckol", layerAspect)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        GLES20.glUniform1i(samplerHandle, 0)

        GLES20.glDrawArrays(
            GLES20.GL_TRIANGLES,
            0,
            6
        )

        GLES20.glDisableVertexAttribArray(
            positionHandle
        )

        GLES20.glDisableVertexAttribArray(
            texCoordHandle
        )
    }

    fun release() {

        if (program != 0) {

            GLES20.glDeleteProgram(
                program
            )

            program = 0
        }
    }
}