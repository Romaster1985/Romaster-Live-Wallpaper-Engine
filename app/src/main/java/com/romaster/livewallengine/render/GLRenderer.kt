package com.romaster.livewallengine.render

import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.view.Surface
import android.view.SurfaceHolder
import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.app.KeyguardManager
import java.nio.ByteBuffer
import java.nio.ByteOrder
import com.romaster.livewallengine.model.VideoFitMode
import com.romaster.livewallengine.model.VideoAspectMode
import com.romaster.livewallengine.project.ProjectManager
import com.romaster.livewallengine.model.VideoLayer
import com.romaster.livewallengine.debug.FileLogger

class GLRenderer(
    private val context: Context,
    private val holder: SurfaceHolder
) {

    private val egl = EGLHelper()
    private var program = 0
    private var textureId = 0
    private var surfaceTexture: SurfaceTexture? = null
    private var videoSurface: Surface? = null

    private var overlayRenderer: GLOverlayRenderer? = null
    private var videoOverlayRenderer: GLVideoOverlayRenderer? = null

    private var positionHandle = 0
    private var texCoordHandle = 0
    private var samplerHandle = 0
    private var texMatrixHandle = 0
    private val textureMatrix = FloatArray(16)
    private var vertexBuffer: java.nio.FloatBuffer? = null

    private var width = 0
    private var height = 0
    private var videoPlayerWidth = 0
    private var videoPlayerHeight = 0
    private var virtualWidth = 0
    private var virtualHeight = 0

    private val renderWidth: Int
        get() = if (virtualWidth > 0) virtualWidth else width

    private val renderHeight: Int
        get() = if (virtualHeight > 0) virtualHeight else height

    private val fadeDurationMs: Long
        get() = ProjectManager.getProject().videoFadeDurationMs

    private var fadeStartTime = 0L
    private var fadeAlpha = 0f
    private var alphaHandle = 0

    private var videoVertexBuffer: java.nio.FloatBuffer? = null
    private var videoVertices = FloatArray(24)
    private var transformHandle = 0
    private val transformMatrix = FloatArray(16)

    private val quadVertices = floatArrayOf(
        // X      Y      U     V
        -1f, -1f, 0f, 0f,
         1f, -1f, 1f, 0f,
        -1f,  1f, 0f, 1f,

        -1f,  1f, 0f, 1f,
         1f, -1f, 1f, 0f,
         1f,  1f, 1f, 1f
    )

    private val vertexShaderCode = """
    attribute vec2 aPosition;
    attribute vec2 aTexCoord;
    uniform mat4 uTexMatrix;
    uniform mat4 uTransform;
    varying vec2 vTexCoord;
    void main() {
        vec4 tex = uTexMatrix * vec4(aTexCoord, 0.0, 1.0);
        vTexCoord = tex.xy;
        gl_Position = uTransform * vec4(aPosition, 0.0, 1.0);
    }
    """.trimIndent()

    private val fragmentShaderCode = """
    #extension GL_OES_EGL_image_external : require
    precision mediump float;
    varying vec2 vTexCoord;
    uniform samplerExternalOES uTexture;
    uniform float uAlpha;
    void main() {
        vec4 color = texture2D(uTexture, vTexCoord);
        color.a *= uAlpha;
        gl_FragColor = color;
    }
    """.trimIndent()

    fun onSurfaceChanged(width: Int, height: Int) {
        this.width = width
        this.height = height
        videoOverlayRenderer?.setSize(width, height)
    }

    fun initialize() {
        if (!egl.initialize(holder)) {
            throw RuntimeException("No se pudo inicializar EGL")
        }

        initializeTriangle()
        textureId = createExternalTexture()
        surfaceTexture = SurfaceTexture(textureId)
        videoSurface = Surface(surfaceTexture)

        android.opengl.Matrix.setIdentityM(textureMatrix, 0)
        android.opengl.Matrix.setIdentityM(transformMatrix, 0)

        overlayRenderer = GLOverlayRenderer()
        overlayRenderer!!.initialize(context)

        val keyguard = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        val locked = keyguard.isKeyguardLocked
        val clock = ProjectManager.getProject().clock

        if (locked) {
            overlayRenderer!!.setLockScreenVisible(visible = clock.enabledOnLockScreen, fadeIn = false)
        } else {
            overlayRenderer!!.setLockScreenVisible(visible = true, fadeIn = false)
        }

        // 1. Instanciamos el renderizador del overlay de video
        videoOverlayRenderer = GLVideoOverlayRenderer(context)

        // CORREGIDO: Capturamos el ancho y alto del surface holder REAL de Android
        val realWidth = holder.surfaceFrame.width()
        val realHeight = holder.surfaceFrame.height()

        // 2. FUNDAMENTAL: Le inyectamos las dimensiones reales de la pantalla ANTES de inicializar
        videoOverlayRenderer!!.setSize(realWidth, realHeight)

        // 3. Ahora sí inicializamos con matrices ortográficas válidas
        videoOverlayRenderer!!.initialize()

        // Sincronizamos las variables globales del renderizador principal
        onSurfaceChanged(realWidth, realHeight)
    }

    private fun initializeTriangle() {
        val vertexShader = compileShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode)
        val fragmentShader = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode)

        program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vertexShader)
        GLES20.glAttachShader(program, fragmentShader)
        GLES20.glLinkProgram(program)

        val status = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, status, 0)
        if (status[0] == 0) {
            throw RuntimeException(GLES20.glGetProgramInfoLog(program))
        }

        positionHandle = GLES20.glGetAttribLocation(program, "aPosition")
        texCoordHandle = GLES20.glGetAttribLocation(program, "aTexCoord")
        samplerHandle = GLES20.glGetUniformLocation(program, "uTexture")
        texMatrixHandle = GLES20.glGetUniformLocation(program, "uTexMatrix")
        transformHandle = GLES20.glGetUniformLocation(program, "uTransform")
        alphaHandle = GLES20.glGetUniformLocation(program, "uAlpha")

        vertexBuffer = ByteBuffer.allocateDirect(quadVertices.size * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer()
        vertexBuffer!!.put(quadVertices)
        vertexBuffer!!.position(0)

        updateVideoRect(-1f, 1f, -1f, 1f)
    }

    fun startFadeIn() {
        fadeStartTime = SystemClock.elapsedRealtime()
        fadeAlpha = 0f
        FileLogger.log(context, "GLRenderer -> Fade-in iniciado")
    }

    private fun updateFade() {
        if (fadeStartTime <= 0L) {
            fadeAlpha = 1f
            return
        }
        val elapsed = SystemClock.elapsedRealtime() - fadeStartTime
        fadeAlpha = (elapsed.toFloat() / fadeDurationMs.toFloat()).coerceIn(0f, 1f)
        if (fadeAlpha >= 1f) {
            fadeStartTime = 0L
        }
    }

    private fun createExternalTexture(): Int {
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textures[0])
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        return textures[0]
    }

    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        val status = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) {
            throw RuntimeException(GLES20.glGetShaderInfoLog(shader))
        }
        return shader
    }

    fun drawFrame() {
        if (width <= 0 || height <= 0) return
        try {
            surfaceTexture?.updateTexImage()
            surfaceTexture?.getTransformMatrix(textureMatrix)
        } catch (_: IllegalStateException) {}

        GLES20.glViewport(0, 0, width, height)
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        GLES20.glUseProgram(program)
        updateFade()

        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)

        videoVertexBuffer!!.position(0)
        GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false, 16, videoVertexBuffer)
        GLES20.glEnableVertexAttribArray(positionHandle)

        videoVertexBuffer!!.position(2)
        GLES20.glVertexAttribPointer(texCoordHandle, 2, GLES20.GL_FLOAT, false, 16, videoVertexBuffer)
        GLES20.glEnableVertexAttribArray(texCoordHandle)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)

        GLES20.glUniform1i(samplerHandle, 0)
        GLES20.glUniformMatrix4fv(texMatrixHandle, 1, false, textureMatrix, 0)

        updateVideoTransform()

        GLES20.glUniformMatrix4fv(transformHandle, 1, false, transformMatrix, 0)
        GLES20.glUniform1f(alphaHandle, fadeAlpha)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 6)

        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(texCoordHandle)
        GLES20.glDisable(GLES20.GL_BLEND)

        val clockBehind =
            ProjectManager.getProject().clock.behindVideoOverlay

        val vw = if (virtualWidth > 0) virtualWidth else width
        val vh = if (virtualHeight > 0) virtualHeight else height

        // Orden de capas:
        // - Normal:  BG → Video-OL → Reloj
        // - behind:  BG → Reloj → Video-OL
        if (clockBehind) {
            overlayRenderer?.draw(vw, vh)
            videoOverlayRenderer?.update()
            videoOverlayRenderer?.draw()
        } else {
            videoOverlayRenderer?.update()
            videoOverlayRenderer?.draw()
            overlayRenderer?.draw(vw, vh)
        }

        egl.swapBuffers()
    }

    private fun updateVideoTransform() {
        android.opengl.Matrix.setIdentityM(transformMatrix, 0)
        val layer = ProjectManager.getProject().layers.firstOrNull() ?: return

        val screenRatio = renderWidth.toFloat() / renderHeight.toFloat()
        val projectionMatrix = FloatArray(16)
        android.opengl.Matrix.orthoM(projectionMatrix, 0, -screenRatio, screenRatio, -1f, 1f, -1f, 1f)

        val modelMatrix = FloatArray(16)
        android.opengl.Matrix.setIdentityM(modelMatrix, 0)

        when (layer.fitMode) {
            VideoFitMode.STRETCH -> {
                android.opengl.Matrix.setIdentityM(transformMatrix, 0)
                return
            }
            VideoFitMode.FREE -> {
                val zoom = layer.scale / 100f
                val targetRatio = getTargetAspectRatio(layer.aspectMode)
                android.opengl.Matrix.translateM(modelMatrix, 0, (layer.x / 100f) * screenRatio, layer.y / 100f, 0f)
                android.opengl.Matrix.scaleM(modelMatrix, 0, targetRatio * zoom, 1f * zoom, 1f)
            }
            VideoFitMode.FIT -> {
                val videoRatio = videoPlayerWidth.toFloat() / videoPlayerHeight.toFloat()
                if (videoRatio > screenRatio) {
                    android.opengl.Matrix.scaleM(modelMatrix, 0, screenRatio, screenRatio / videoRatio, 1f)
                } else {
                    android.opengl.Matrix.scaleM(modelMatrix, 0, videoRatio, 1f, 1f)
                }
            }
            VideoFitMode.FILL -> {
                val videoRatio = videoPlayerWidth.toFloat() / videoPlayerHeight.toFloat()
                if (videoRatio > screenRatio) {
                    android.opengl.Matrix.scaleM(modelMatrix, 0, videoRatio, 1f, 1f)
                } else {
                    android.opengl.Matrix.scaleM(modelMatrix, 0, screenRatio, screenRatio / videoRatio, 1f)
                }
            }
        }

        android.opengl.Matrix.multiplyMM(transformMatrix, 0, projectionMatrix, 0, modelMatrix, 0)
    }

    fun setVideoSize(width: Int, height: Int) {
        videoPlayerWidth = width
        videoPlayerHeight = height
    }

    private fun getTargetAspectRatio(aspectMode: VideoAspectMode): Float {
        return when (aspectMode) {
            VideoAspectMode.ORIGINAL -> {
                if (videoPlayerWidth > 0 && videoPlayerHeight > 0) {
                    videoPlayerWidth.toFloat() / videoPlayerHeight.toFloat()
                } else {
                    renderWidth.toFloat() / renderHeight.toFloat()
                }
            }
            VideoAspectMode.R16_9 -> 9f / 16f
            VideoAspectMode.R16_10 -> 10f / 16f
            VideoAspectMode.R18_9 -> 9f / 18f
            VideoAspectMode.R20_9 -> 9f / 20f
            VideoAspectMode.R4_3 -> 3f / 4f
            VideoAspectMode.R3_2 -> 2f / 3f
        }
    }

    fun getVideoSurface(): Surface {
        return videoSurface ?: throw IllegalStateException("VideoSurface no inicializada")
    }

    fun getVideoOverlayRenderer(): GLVideoOverlayRenderer? {
        return videoOverlayRenderer
    }

    fun setClockLockScreenState(visible: Boolean, fadeIn: Boolean) {
        overlayRenderer?.setLockScreenVisible(visible = visible, fadeIn = fadeIn)
    }

    fun clearSurface() {
        if (width <= 0 || height <= 0) return
        try {
            GLES20.glViewport(0, 0, width, height)
            GLES20.glDisable(GLES20.GL_BLEND)
            GLES20.glClearColor(0f, 0f, 0f, 1f)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
            egl.swapBuffers()
            FileLogger.log(context, "GLRenderer -> Surface limpiada")
        } catch (e: Exception) {
            FileLogger.logException(context, "GLRenderer.clearSurface()", e)
        }
    }

    fun release() {
        videoSurface?.release()
        videoSurface = null
        surfaceTexture?.release()
        surfaceTexture = null

        if (textureId != 0) {
            GLES20.glDeleteTextures(1, intArrayOf(textureId), 0)
            textureId = 0
        }
        if (program != 0) {
            GLES20.glDeleteProgram(program)
            program = 0
        }
        vertexBuffer = null
        videoOverlayRenderer?.release()
        videoOverlayRenderer = null
        overlayRenderer?.release()
        overlayRenderer = null
        egl.release()
    }

    fun setVirtualScreenSize(width: Int, height: Int) {
        virtualWidth = width
        virtualHeight = height
    }

    fun captureBitmap(): Bitmap {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val buffer = ByteBuffer.allocateDirect(width * height * 4).order(ByteOrder.nativeOrder())
        GLES20.glReadPixels(0, 0, width, height, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buffer)
        buffer.rewind()
        bmp.copyPixelsFromBuffer(buffer)
        return flipVertical(bmp)
    }

    private fun flipVertical(src: Bitmap): Bitmap {
        val matrix = android.graphics.Matrix()
        matrix.preScale(1f, -1f)
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, false)
    }

    private fun updateVideoRect(left: Float, right: Float, bottom: Float, top: Float) {
        videoVertices = floatArrayOf(
            left,  bottom, 0f, 0f,
            right, bottom, 1f, 0f,
            left,  top,    0f, 1f,
            left,  top,    0f, 1f,
            right, bottom, 1f, 0f,
            right, top,    1f, 1f
        )
        videoVertexBuffer = ByteBuffer.allocateDirect(videoVertices.size * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer()
        videoVertexBuffer!!.put(videoVertices)
        videoVertexBuffer!!.position(0)
    }
}
