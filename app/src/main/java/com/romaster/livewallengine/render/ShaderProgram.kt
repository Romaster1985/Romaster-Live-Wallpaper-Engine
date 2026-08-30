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

import android.opengl.GLES20

object ShaderProgram {

    fun create(

        vertexSource: String,

        fragmentSource: String

    ): Int {

        val vertexShader =
            compile(
                GLES20.GL_VERTEX_SHADER,
                vertexSource
            )

        val fragmentShader =
            compile(
                GLES20.GL_FRAGMENT_SHADER,
                fragmentSource
            )

        val program =
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

        val status =
            IntArray(1)

        GLES20.glGetProgramiv(

            program,

            GLES20.GL_LINK_STATUS,

            status,

            0
        )

        GLES20.glDeleteShader(
            vertexShader
        )

        GLES20.glDeleteShader(
            fragmentShader
        )

        if (
            status[0] == 0
        ) {

            val log =
                GLES20.glGetProgramInfoLog(
                    program
                )

            GLES20.glDeleteProgram(
                program
            )

            throw RuntimeException(
                "Program link error:\n$log"
            )
        }

        return program
    }

    private fun compile(

        type: Int,

        source: String

    ): Int {

        val shader =
            GLES20.glCreateShader(
                type
            )

        GLES20.glShaderSource(

            shader,

            source
        )

        GLES20.glCompileShader(
            shader
        )

        val status =
            IntArray(1)

        GLES20.glGetShaderiv(

            shader,

            GLES20.GL_COMPILE_STATUS,

            status,

            0
        )

        if (
            status[0] == 0
        ) {

            val log =
                GLES20.glGetShaderInfoLog(
                    shader
                )

            GLES20.glDeleteShader(
                shader
            )

            throw RuntimeException(
                "Shader compile error:\n$log"
            )
        }

        return shader
    }
}