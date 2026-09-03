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

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import android.opengl.GLES20

class GLQuadRenderer {

    private val shader =
        GLShader()

    private lateinit var vertexBuffer:
        FloatBuffer

    private val quadVertices =
        floatArrayOf(

            // X      Y      U     V

            -1f, -1f, 0f, 1f,
             1f, -1f, 1f, 1f,
            -1f,  1f, 0f, 0f,

            -1f,  1f, 0f, 0f,
             1f, -1f, 1f, 1f,
             1f,  1f, 1f, 0f
        )

    fun initialize() {

        shader.initialize()

        vertexBuffer =
            ByteBuffer
                .allocateDirect(
                    quadVertices.size * 4
                )
                .order(
                    ByteOrder.nativeOrder()
                )
                .asFloatBuffer()

        vertexBuffer.put(
            quadVertices
        )

        vertexBuffer.position(0)
    }

    fun draw(
        texture: GLTexture,
        alpha: Float
    ) {
        
        GLES20.glEnable(
            GLES20.GL_BLEND
        )
        
        // Bitmaps de Canvas (ARGB_8888) vienen con alfa PREMULTIPLICADO.
        // SRC_ALPHA + ONE_MINUS_SRC_ALPHA vuelve a multiplicar el RGB y oscurece
        // los colores translúcidos (rojo → marrón). El blend correcto es:
        GLES20.glBlendFunc(
            GLES20.GL_ONE,
            GLES20.GL_ONE_MINUS_SRC_ALPHA
        )

        shader.use()

        shader.draw(
            vertexBuffer,
            texture.getTextureId(),
            alpha
        )
        
        GLES20.glDisable(
            GLES20.GL_BLEND
        )
    }

    fun release() {

        shader.release()
    }
}