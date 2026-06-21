package com.romaster.livewallengine.render

import android.opengl.GLES20
import java.nio.FloatBuffer

class GLShader {

    private var program = 0

    private var positionHandle = 0

    private var texCoordHandle = 0

    private var samplerHandle = 0

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

        void main() {

            gl_FragColor =
                texture2D(
                    uTexture,
                    vTexCoord
                );
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

        return shader
    }

    fun use() {

        GLES20.glUseProgram(
            program
        )
    }

    fun draw(
        vertexBuffer: FloatBuffer,
        textureId: Int
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