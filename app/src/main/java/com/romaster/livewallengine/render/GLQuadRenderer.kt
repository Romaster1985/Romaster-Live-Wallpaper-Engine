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
        texture: GLTexture
    ) {
        
        GLES20.glEnable(
            GLES20.GL_BLEND
        )
        
        GLES20.glBlendFunc(
            GLES20.GL_SRC_ALPHA,
            GLES20.GL_ONE_MINUS_SRC_ALPHA
        )

        shader.use()

        shader.draw(
            vertexBuffer,
            texture.getTextureId()
        )
        
        GLES20.glDisable(
            GLES20.GL_BLEND
        )
    }

    fun release() {

        shader.release()
    }
}