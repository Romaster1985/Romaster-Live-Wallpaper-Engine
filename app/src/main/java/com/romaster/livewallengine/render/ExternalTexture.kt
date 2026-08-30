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

import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.view.Surface

class ExternalTexture {

    private var textureId = 0

    private var surfaceTexture: SurfaceTexture? = null

    private var surface: Surface? = null

    private val textureMatrix =
        FloatArray(16)

    fun initialize() {

        val textures =
            IntArray(1)

        GLES20.glGenTextures(
            1,
            textures,
            0
        )

        textureId =
            textures[0]

        GLES20.glBindTexture(

            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,

            textureId
        )

        GLES20.glTexParameteri(

            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,

            GLES20.GL_TEXTURE_MIN_FILTER,

            GLES20.GL_LINEAR
        )

        GLES20.glTexParameteri(

            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,

            GLES20.GL_TEXTURE_MAG_FILTER,

            GLES20.GL_LINEAR
        )

        GLES20.glTexParameteri(

            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,

            GLES20.GL_TEXTURE_WRAP_S,

            GLES20.GL_CLAMP_TO_EDGE
        )

        GLES20.glTexParameteri(

            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,

            GLES20.GL_TEXTURE_WRAP_T,

            GLES20.GL_CLAMP_TO_EDGE
        )

        surfaceTexture =
            SurfaceTexture(textureId)

        surface =
            Surface(surfaceTexture)

        android.opengl.Matrix.setIdentityM(
            textureMatrix,
            0
        )
    }

    /**
     * Tras liberar un MediaPlayer, el Surface asociado al SurfaceTexture
     * queda "abandoned". Hay que crear uno nuevo antes de enganchar
     * otro producer (otro MediaPlayer).
     */
    fun recreateSurface(): Surface {
        try {
            surface?.release()
        } catch (_: Exception) {
        }
        surface = null

        val st = surfaceTexture
            ?: throw IllegalStateException("SurfaceTexture no inicializado")

        surface = Surface(st)
        return surface!!
    }

    fun update() {

        try {
    
            surfaceTexture?.updateTexImage()
    
            surfaceTexture?.getTransformMatrix(
                textureMatrix
            )
            
            android.opengl.Matrix.scaleM(
                textureMatrix,
                0,
                1f,
                -1f,
                1f
            )
            
            android.opengl.Matrix.translateM(
                textureMatrix,
                0,
                0f,
                -1f,
                0f
            )
    
        } catch (_: IllegalStateException) {
    
            // Puede ocurrir mientras la Surface
            // se está creando o destruyendo.
            // Se ignora y se intentará nuevamente
            // en el siguiente frame.
    
        }
    }

    fun getTextureId(): Int {

        return textureId
    }

    fun getTextureMatrix(): FloatArray {

        return textureMatrix
    }

    fun getSurface(): Surface {

        return surface!!
    }

    fun release() {

        surface?.release()
        surface = null

        surfaceTexture?.release()
        surfaceTexture = null

        if (textureId != 0) {

            GLES20.glDeleteTextures(
                1,
                intArrayOf(textureId),
                0
            )

            textureId = 0
        }
    }
}
