package com.romaster.livewallengine.render

import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.view.Surface

class ExternalTexture {

    private var textureId = 0

    private var surfaceTexture: SurfaceTexture? = null

    private var surface: Surface? = null

    @Volatile
    private var frameAvailable = false

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

        surfaceTexture!!.setOnFrameAvailableListener {

            frameAvailable = true
        }

        surface =
            Surface(surfaceTexture)

        android.opengl.Matrix.setIdentityM(
            textureMatrix,
            0
        )
    }

    fun update() {

        if (!frameAvailable) {
            return
        }

        surfaceTexture?.updateTexImage()

        surfaceTexture?.getTransformMatrix(
            textureMatrix
        )

        frameAvailable = false
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