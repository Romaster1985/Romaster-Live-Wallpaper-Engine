package com.romaster.livewallengine.render

import android.opengl.GLES20
import android.opengl.Matrix
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

class GLExternalQuadRenderer {

    private val shader =
        GLExternalShader()

    private lateinit var vertexBuffer:
        FloatBuffer

    // Matrices de cálculo OpenGLES
    private val mvpMatrix = FloatArray(16)
    private val projectionMatrix = FloatArray(16)
    private val modelMatrix = FloatArray(16)

    private var rotation = 0f
    private var centerX = 0f
    private var centerY = 0f
    private var width = 1f
    private var height = 1f
    private var screenAspectRatio = 1f

    // CORREGIDO: Volvemos al tamaño base original de -1 a 1 (ancho/alto total = 2.0)
    // Esto asegura compatibilidad total con la escala nativa del Viewport de OpenGLES
    private val quadVertices =
        floatArrayOf(
            // X       Y      U     V
            -1.0f, -1.0f,   0f,   1f,
             1.0f, -1.0f,   1f,   1f,
            -1.0f,  1.0f,   0f,   0f,

            -1.0f,  1.0f,   0f,   0f,
             1.0f, -1.0f,   1f,   1f,
             1.0f,  1.0f,   1f,   0f
        )

    /**
     * Configura el aspecto de la pantalla y arma la proyección ortográfica.
     */
    fun setSize(screenWidth: Int, screenHeight: Int) {
        screenAspectRatio = screenWidth.toFloat() / screenHeight.toFloat()
        
        // Matriz ortográfica que compensa el estiramiento de pantalla
        Matrix.orthoM(
            projectionMatrix, 0,
            -screenAspectRatio, screenAspectRatio,
            -1f, 1f,
            -1f, 1f
        )
        updateMVPMatrix()
    }

    /**
     * Define la posición y tamaño del rectángulo empleando matrices en GPU.
     */
    fun setRect(
        centerX: Float,
        centerY: Float,
        width: Float,
        height: Float,
        rotation: Float = this.rotation
    ) {
        this.centerX = centerX
        this.centerY = centerY
        this.width = width
        this.height = height
        this.rotation = rotation

        updateMVPMatrix()
    }

    fun setRotation(
        rotation: Float
    ) {
        this.rotation = rotation
        updateMVPMatrix()
    }

    /**
     * CORREGIDO: Ajuste matemático del orden y multiplicador del escalado.
     * Como el Quad base ahora mide 2.0f y el Viewport ortográfico mide 2.0f,
     * dividimos por 2.0f la escala del modelo para normalizarla perfectamente al slider de la UI.
     */
    private fun updateMVPMatrix() {
        Matrix.setIdentityM(modelMatrix, 0)

        // 1. Mover al centro espacial definido por la interfaz del usuario
        Matrix.translateM(modelMatrix, 0, centerX, centerY, 0f)

        // 2. Rotación exacta sobre el eje Z libre de estiramientos
        Matrix.rotateM(modelMatrix, 0, rotation, 0f, 0f, 1f)

        // 3. Escalado corregido (se divide el tamaño por 2.0f ya que los vértices base miden 2 de ancho/alto)
        Matrix.scaleM(modelMatrix, 0, width / 2.0f, height / 2.0f, 1f)

        // 4. Combinar proyección ortográfica con el modelo resultante
        Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, modelMatrix, 0)
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

        // Inicialización base de la matriz de proyección
        Matrix.orthoM(projectionMatrix, 0, -1f, 1f, -1f, 1f, -1f, 1f)

        setRect(
            centerX = 0f,
            centerY = 0f,
            width = 1f,
            height = 1f,
            rotation = 0f
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
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)

        shader.use()

        shader.draw(
            vertexBuffer,
            mvpMatrix,
            textureId,
            textureMatrix,
            alpha,
            chromaEnabled,
            chromaColor,
            threshold,
            softness
        )

        GLES20.glDisable(GLES20.GL_BLEND)
    }

    fun release() {
        shader.release()
    }
}
