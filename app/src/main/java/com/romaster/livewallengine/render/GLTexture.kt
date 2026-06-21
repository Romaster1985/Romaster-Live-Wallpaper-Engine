package com.romaster.livewallengine.render

import android.graphics.Bitmap
import android.opengl.GLES20
import android.opengl.GLUtils

class GLTexture {

    private var textureId = 0

    fun initialize() {

        if (textureId != 0) {
            return
        }

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
            GLES20.GL_TEXTURE_2D,
            textureId
        )

        GLES20.glTexParameteri(
            GLES20.GL_TEXTURE_2D,
            GLES20.GL_TEXTURE_MIN_FILTER,
            GLES20.GL_LINEAR
        )

        GLES20.glTexParameteri(
            GLES20.GL_TEXTURE_2D,
            GLES20.GL_TEXTURE_MAG_FILTER,
            GLES20.GL_LINEAR
        )

        GLES20.glTexParameteri(
            GLES20.GL_TEXTURE_2D,
            GLES20.GL_TEXTURE_WRAP_S,
            GLES20.GL_CLAMP_TO_EDGE
        )

        GLES20.glTexParameteri(
            GLES20.GL_TEXTURE_2D,
            GLES20.GL_TEXTURE_WRAP_T,
            GLES20.GL_CLAMP_TO_EDGE
        )

        GLES20.glBindTexture(
            GLES20.GL_TEXTURE_2D,
            0
        )
    }

    fun upload(
        bitmap: Bitmap
    ) {

        if (textureId == 0) {
            initialize()
        }

        GLES20.glBindTexture(
            GLES20.GL_TEXTURE_2D,
            textureId
        )

        GLUtils.texImage2D(
            GLES20.GL_TEXTURE_2D,
            0,
            bitmap,
            0
        )

        GLES20.glBindTexture(
            GLES20.GL_TEXTURE_2D,
            0
        )
    }

    /**
     * Activa esta textura en la unidad indicada.
     */
    fun bind(
        textureUnit: Int = 0
    ) {

        GLES20.glActiveTexture(
            GLES20.GL_TEXTURE0 + textureUnit
        )

        GLES20.glBindTexture(
            GLES20.GL_TEXTURE_2D,
            textureId
        )
    }

    /**
     * Desactiva la textura.
     */
    fun unbind() {

        GLES20.glBindTexture(
            GLES20.GL_TEXTURE_2D,
            0
        )
    }

    fun getTextureId(): Int {

        return textureId
    }

    fun release() {

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