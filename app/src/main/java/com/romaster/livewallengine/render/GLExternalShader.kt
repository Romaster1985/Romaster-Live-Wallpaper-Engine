package com.romaster.livewallengine.render

import android.opengl.GLES11Ext
import android.opengl.GLES20
import java.nio.FloatBuffer

class GLExternalShader {

    private var program = 0

    private var positionHandle = 0

    private var texCoordHandle = 0

    private var samplerHandle = 0
    
    private var alphaHandle = 0
    
    private var chromaEnabledHandle = 0

    private var chromaColorHandle = 0
    
    private var thresholdHandle = 0
    
    private var softnessHandle = 0

    private var texMatrixHandle = 0

    private val vertexShaderCode =
        """
        attribute vec2 aPosition;
        attribute vec2 aTexCoord;

        uniform mat4 uTexMatrix;

        varying vec2 vTexCoord;

        void main() {

            gl_Position =
                vec4(
                    aPosition,
                    0.0,
                    1.0
                );

            vec4 tex =
                uTexMatrix *
                vec4(
                    aTexCoord,
                    0.0,
                    1.0
                );

            vTexCoord =
                tex.xy;
        }
        """.trimIndent()

    private val fragmentShaderCode =
        """
        #extension GL_OES_EGL_image_external : require
    
        precision mediump float;
    
        varying vec2 vTexCoord;
    
        uniform samplerExternalOES uTexture;
    
        uniform float uAlpha;
    
        uniform bool uChromaEnabled;
    
        uniform vec3 uChromaColor;
    
        uniform float uThreshold;
    
        uniform float uSoftness;
    
        void main() {
    
            vec4 color =
                texture2D(
                    uTexture,
                    vTexCoord
                );
    
            color.a *= uAlpha;
    
            if (uChromaEnabled && uThreshold > 0.0) {

                vec3 diff =
                    color.rgb - uChromaColor;
            
                float distanceColor =
                    length(diff);
            
                float feather = 0.0;
            
                if (uSoftness > 0.0) {
            
                    feather =
                        max(
                            uSoftness,
                            0.0001
                        );
            
                }
            
                float alphaMask =
                    smoothstep(
                        uThreshold - feather,
                        uThreshold + feather,
                        distanceColor
                    );
            
                color.a *= alphaMask;
            }
    
            gl_FragColor = color;
        }
        """.trimIndent()

    fun initialize() {

        program =
            ShaderProgram.create(

                vertexShaderCode,

                fragmentShaderCode
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
        
        chromaEnabledHandle =
            GLES20.glGetUniformLocation(
                program,
                "uChromaEnabled"
            )
        
        chromaColorHandle =
            GLES20.glGetUniformLocation(
                program,
                "uChromaColor"
            )
        
        thresholdHandle =
            GLES20.glGetUniformLocation(
                program,
                "uThreshold"
            )
        
        softnessHandle =
            GLES20.glGetUniformLocation(
                program,
                "uSoftness"
            )

        texMatrixHandle =
            GLES20.glGetUniformLocation(
                program,
                "uTexMatrix"
            )
    }

    fun use() {

        GLES20.glUseProgram(
            program
        )
    }

    fun draw(
        vertexBuffer: FloatBuffer,
        textureId: Int,
        textureMatrix: FloatArray,
        alpha: Float,
        chromaEnabled: Boolean,
        chromaColor: Int,
        threshold: Float,
        softness: Float
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
        
        GLES20.glUniform1f(
            alphaHandle,
            alpha
        )
        
        GLES20.glUniform1i(
            chromaEnabledHandle,
            if (chromaEnabled) 1 else 0
        )
        
        val r =
            ((chromaColor shr 16) and 0xFF) / 255f
        
        val g =
            ((chromaColor shr 8) and 0xFF) / 255f
        
        val b =
            (chromaColor and 0xFF) / 255f
        
        GLES20.glUniform3f(
            chromaColorHandle,
            r,
            g,
            b
        )
        
        GLES20.glUniform1f(
            thresholdHandle,
            threshold / 100f
        )
        
        GLES20.glUniform1f(
            softnessHandle,
            softness / 100f
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