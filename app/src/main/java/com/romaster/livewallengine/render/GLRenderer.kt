package com.romaster.livewallengine.render

import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.view.Surface
import android.view.SurfaceHolder
import android.content.Context

class GLRenderer(

    private val context: Context,

    private val holder: SurfaceHolder

) {

    private val egl =
        EGLHelper()

    private var program = 0

    private var textureId = 0

    private var surfaceTexture: SurfaceTexture? = null

    private var videoSurface: Surface? = null
    
    private var overlayRenderer: GLOverlayRenderer? = null

    private var positionHandle = 0

    private var texCoordHandle = 0

    private var samplerHandle = 0

    private var texMatrixHandle = 0

    @Volatile
    private var frameAvailable = false

    private val textureMatrix =
        FloatArray(16)

    private var vertexBuffer:
        java.nio.FloatBuffer? = null

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

    private val vertexShaderCode =
        """
    attribute vec2 aPosition;
    attribute vec2 aTexCoord;
    
    uniform mat4 uTexMatrix;
    
    varying vec2 vTexCoord;
    
    void main() {
    
        vec4 tex =
            uTexMatrix *
            vec4(
                aTexCoord,
                0.0,
                1.0
            );
    
        vTexCoord =
            tex.xy;
    
        gl_Position =
            vec4(
                aPosition,
                0.0,
                1.0
            );
    }
    """.trimIndent()
    
        private val fragmentShaderCode =
            """
    #extension GL_OES_EGL_image_external : require
    
    precision mediump float;
    
    varying vec2 vTexCoord;
    
    uniform samplerExternalOES uTexture;
    
    void main() {
    
        gl_FragColor =
            texture2D(
                uTexture,
                vTexCoord
            );
    }
    """.trimIndent()
    
    fun initialize() {

        if (!egl.initialize(holder)) {

            throw RuntimeException(
                "No se pudo inicializar EGL"
            )
        }

        initializeTriangle()

        textureId =
            createExternalTexture()

        surfaceTexture =
            SurfaceTexture(textureId)

        surfaceTexture!!.setOnFrameAvailableListener {

            frameAvailable = true
        }

        videoSurface =
            Surface(surfaceTexture)

        android.opengl.Matrix.setIdentityM(
            textureMatrix,
            0
        )
        
        overlayRenderer = GLOverlayRenderer()
        overlayRenderer!!.initialize(context)
    }

    private fun initializeTriangle() {

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

        val status =
            IntArray(1)

        GLES20.glGetProgramiv(

            program,

            GLES20.GL_LINK_STATUS,

            status,

            0
        )

        if (status[0] == 0) {

            throw RuntimeException(
                GLES20.glGetProgramInfoLog(
                    program
                )
            )
        }

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

        texMatrixHandle =
            GLES20.glGetUniformLocation(
                program,
                "uTexMatrix"
            )

        vertexBuffer =
            java.nio.ByteBuffer

                .allocateDirect(
                    quadVertices.size * 4
                )

                .order(
                    java.nio.ByteOrder.nativeOrder()
                )

                .asFloatBuffer()

        vertexBuffer!!.put(
            quadVertices
        )

        vertexBuffer!!.position(0)
    }

    private fun createExternalTexture(): Int {

        val textures =
            IntArray(1)

        GLES20.glGenTextures(
            1,
            textures,
            0
        )

        GLES20.glBindTexture(

            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,

            textures[0]
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

        return textures[0]
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

        val status =
            IntArray(1)

        GLES20.glGetShaderiv(

            shader,

            GLES20.GL_COMPILE_STATUS,

            status,

            0
        )

        if (status[0] == 0) {

            throw RuntimeException(

                GLES20.glGetShaderInfoLog(
                    shader
                )
            )
        }

        return shader
    }
    
    fun drawFrame() {

        if (frameAvailable) {

            surfaceTexture?.updateTexImage()

            surfaceTexture?.getTransformMatrix(
                textureMatrix
            )

            frameAvailable = false
        }

        GLES20.glViewport(

            0,

            0,

            holder.surfaceFrame.width(),

            holder.surfaceFrame.height()
        )

        GLES20.glClearColor(

            0f,

            0f,

            0f,

            1f
        )

        GLES20.glClear(
            GLES20.GL_COLOR_BUFFER_BIT
        )

        GLES20.glUseProgram(
            program
        )

        vertexBuffer!!.position(0)

        GLES20.glVertexAttribPointer(

            positionHandle,

            2,

            GLES20.GL_FLOAT,

            false,

            4 * 4,

            vertexBuffer
        )

        GLES20.glEnableVertexAttribArray(
            positionHandle
        )

        vertexBuffer!!.position(2)

        GLES20.glVertexAttribPointer(

            texCoordHandle,

            2,

            GLES20.GL_FLOAT,

            false,

            4 * 4,

            vertexBuffer
        )

        GLES20.glEnableVertexAttribArray(
            texCoordHandle
        )

        GLES20.glActiveTexture(
            GLES20.GL_TEXTURE0
        )

        GLES20.glBindTexture(

            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,

            textureId
        )

        GLES20.glUniform1i(

            samplerHandle,

            0
        )

        GLES20.glUniformMatrix4fv(

            texMatrixHandle,

            1,

            false,

            textureMatrix,

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

        overlayRenderer?.draw(
            holder.surfaceFrame.width(),
            holder.surfaceFrame.height()
        )
        
        egl.swapBuffers()
    }

    fun getVideoSurface(): Surface {

        return videoSurface
            ?: throw IllegalStateException(
                "VideoSurface no inicializada"
            )
    }

    fun release() {

        videoSurface?.release()
        videoSurface = null

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

        if (program != 0) {

            GLES20.glDeleteProgram(
                program
            )

            program = 0
        }

        vertexBuffer = null
        
        overlayRenderer?.release()
        overlayRenderer = null

        egl.release()
    }
}