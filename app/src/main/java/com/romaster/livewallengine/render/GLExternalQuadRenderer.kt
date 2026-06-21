package com.romaster.livewallengine.render

import android.opengl.GLES20
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

class GLExternalQuadRenderer {

    private val shader =
        GLExternalShader()

    private lateinit var vertexBuffer:
        FloatBuffer
    
    private var left = -1f

    private var right = 1f
    
    private var bottom = -1f
    
    private var top = 1f

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
    
    fun setRect(
        centerX: Float,
        centerY: Float,
        width: Float,
        height: Float
    ) {
    
        val left =
            centerX - width / 2f
    
        val right =
            centerX + width / 2f
    
        val top =
            centerY + height / 2f
    
        val bottom =
            centerY - height / 2f
    
    
        val vertices = floatArrayOf(
    
            left,  bottom, 0f, 1f,
            right, bottom, 1f, 1f,
            left,  top,    0f, 0f,
    
            left,  top,    0f, 0f,
            right, bottom, 1f, 1f,
            right, top,    1f, 0f
    
        )
    
        vertexBuffer.position(0)
        vertexBuffer.put(vertices)
        vertexBuffer.position(0)
    }

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
    
        setRect(
            0f,
            0f,
            1f,
            1f
        )
    }

    fun draw(

        textureId: Int,
    
        textureMatrix: FloatArray,
    
        alpha: Float,
    
        chromaEnabled: Boolean,
    
        chromaColor: Int,
    
        threshold: Float,
    
        softness: Float
    
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
        
            textureId,
        
            textureMatrix,
        
            alpha,
        
            chromaEnabled,
        
            chromaColor,
        
            threshold,
        
            softness
        )

        GLES20.glDisable(
            GLES20.GL_BLEND
        )
    }

    fun release() {

        shader.release()
    }
}