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

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Movie
import android.opengl.GLES20
import android.opengl.Matrix
import com.romaster.livewallengine.image.ImageStorage
import com.romaster.livewallengine.model.ImageLayer
import com.romaster.livewallengine.project.ProjectManager
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * Dibuja las capas de imagen (Pics-OL) con posición, zoom, rotación y alpha.
 * Soporta GIF animados vía [Movie].
 */
class GLImageLayersRenderer {

    private var program = 0
    private var positionHandle = 0
    private var texCoordHandle = 0
    private var samplerHandle = 0
    private var alphaHandle = 0
    private var mvpHandle = 0

    private lateinit var vertexBuffer: FloatBuffer
    private val textures = mutableMapOf<String, GLTexture>()
    private val movies = mutableMapOf<String, Movie>()
    private val movieStart = mutableMapOf<String, Long>()
    private val bitmapSize = mutableMapOf<String, Pair<Int, Int>>()
    private val fadeStartTimes = mutableMapOf<String, Long>()
    private val forceHidden = mutableMapOf<String, Boolean>()

    private var screenW = 1
    private var screenH = 1
    private var context: Context? = null

    private val quad = floatArrayOf(
        -1f, -1f, 0f, 1f,
         1f, -1f, 1f, 1f,
        -1f,  1f, 0f, 0f,
        -1f,  1f, 0f, 0f,
         1f, -1f, 1f, 1f,
         1f,  1f, 1f, 0f
    )

    private val vShader = """
        attribute vec2 aPosition;
        attribute vec2 aTexCoord;
        uniform mat4 uMVP;
        varying vec2 vTexCoord;
        void main() {
            gl_Position = uMVP * vec4(aPosition, 0.0, 1.0);
            vTexCoord = aTexCoord;
        }
    """.trimIndent()

    private val fShader = """
        precision mediump float;
        varying vec2 vTexCoord;
        uniform sampler2D uTexture;
        uniform float uAlpha;
        void main() {
            vec4 c = texture2D(uTexture, vTexCoord);
            c.a *= uAlpha;
            gl_FragColor = c;
        }
    """.trimIndent()

    fun initialize(context: Context, width: Int, height: Int) {
        this.context = context.applicationContext
        screenW = width.coerceAtLeast(1)
        screenH = height.coerceAtLeast(1)

        val vs = compile(GLES20.GL_VERTEX_SHADER, vShader)
        val fs = compile(GLES20.GL_FRAGMENT_SHADER, fShader)
        program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vs)
        GLES20.glAttachShader(program, fs)
        GLES20.glLinkProgram(program)

        positionHandle = GLES20.glGetAttribLocation(program, "aPosition")
        texCoordHandle = GLES20.glGetAttribLocation(program, "aTexCoord")
        samplerHandle = GLES20.glGetUniformLocation(program, "uTexture")
        alphaHandle = GLES20.glGetUniformLocation(program, "uAlpha")
        mvpHandle = GLES20.glGetUniformLocation(program, "uMVP")

        vertexBuffer = ByteBuffer.allocateDirect(quad.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
        vertexBuffer.put(quad)
        vertexBuffer.position(0)

        reloadFromProject()
    }

    fun setSize(width: Int, height: Int) {
        screenW = width.coerceAtLeast(1)
        screenH = height.coerceAtLeast(1)
    }

    /** Fuerza recarga de una capa (nuevo archivo). */
    fun forceReloadLayer(id: String) {
        textures.remove(id)?.release()
        movies.remove(id)
        movieStart.remove(id)
        bitmapSize.remove(id)
        reloadFromProject()
    }

    fun reloadFromProject(force: Boolean = false) {
        val ctx = context ?: return
        val layers = ProjectManager.getProject().imageLayers
        val ids = layers.map { it.id }.toSet()

        // Liberar capas eliminadas o forzar recarga total
        textures.keys.filter { force || it !in ids }.forEach { id ->
            textures.remove(id)?.release()
            movies.remove(id)
            movieStart.remove(id)
            bitmapSize.remove(id)
        }

        for (layer in layers) {
            val name = layer.fileName ?: continue
            // Si ya hay textura y no es force, no recargar
            if (!force && textures.containsKey(layer.id)) continue
            val file = ImageStorage.getImageFile(ctx, name)
            if (!file.exists()) continue

            if (name.endsWith(".gif", ignoreCase = true)) {
                try {
                    FileInputStream(file).use { fis ->
                        val movie = Movie.decodeStream(fis)
                        if (movie != null && movie.width() > 0 && movie.height() > 0) {
                            movies[layer.id] = movie
                            movieStart[layer.id] = System.currentTimeMillis()
                            bitmapSize[layer.id] = movie.width() to movie.height()
                            val tex = GLTexture()
                            tex.initialize()
                            textures[layer.id] = tex
                            uploadMovieFrame(layer.id, movie, 0)
                        }
                    }
                } catch (_: Exception) {
                }
            } else {
                try {
                    val opts = BitmapFactory.Options().apply {
                        inJustDecodeBounds = true
                    }
                    BitmapFactory.decodeFile(file.absolutePath, opts)
                    var sample = 1
                    val maxSide = 2048
                    while ((opts.outWidth / sample) > maxSide || (opts.outHeight / sample) > maxSide) {
                        sample *= 2
                    }
                    val bmp = BitmapFactory.decodeFile(
                        file.absolutePath,
                        BitmapFactory.Options().apply { inSampleSize = sample }
                    ) ?: continue
                    bitmapSize[layer.id] = bmp.width to bmp.height
                    val tex = GLTexture()
                    tex.initialize()
                    tex.upload(bmp)
                    textures[layer.id] = tex
                    bmp.recycle()
                } catch (_: Exception) {
                }
            }
        }
    }

    fun startSoftStartAll() {
        val layers = ProjectManager.getProject().imageLayers
        val now = android.os.SystemClock.elapsedRealtime()
        for (layer in layers) {
            if (forceHidden[layer.id] == true) continue
            fadeStartTimes[layer.id] = now
        }
    }

    fun startSoftStart(id: String) {
        if (forceHidden[id] == true) return
        fadeStartTimes[id] = android.os.SystemClock.elapsedRealtime()
    }

    fun setLockScreenVisible(deviceLocked: Boolean) {
        val layers = ProjectManager.getProject().imageLayers
        for (layer in layers) {
            val show = !deviceLocked || layer.enabledOnLockScreen
            forceHidden[layer.id] = !show
            if (!show) {
                fadeStartTimes.remove(layer.id)
            }
        }
    }

    /** Dibuja una capa de imagen por id (orden lo decide layerStack). */
    fun drawById(id: String) {
        val layer = ProjectManager.getProject().imageLayers
            .find { it.id == id && it.fileName != null } ?: return
        drawLayer(layer)
    }

    private fun drawLayer(layer: ImageLayer) {
        if (forceHidden[layer.id] == true) return
        val tex = textures[layer.id] ?: return

        val fadeAlpha = layerFadeAlpha(layer)
        if (fadeAlpha <= 0.001f) return

        // Actualizar frame de GIF
        movies[layer.id]?.let { movie ->
            val start = movieStart[layer.id] ?: System.currentTimeMillis()
            val dur = movie.duration().takeIf { it > 0 } ?: 1000
            val t = ((System.currentTimeMillis() - start) % dur).toInt()
            uploadMovieFrame(layer.id, movie, t)
        }

        val (bw, bh) = bitmapSize[layer.id] ?: (1 to 1)
        val screenRatio = screenW.toFloat() / screenH.toFloat()
        val imgRatio = bw.toFloat() / bh.toFloat().coerceAtLeast(1f)

        // Quad unitario -1..1 cubre altura completa; escalar por aspect
        val baseScaleX: Float
        val baseScaleY: Float
        if (imgRatio > screenRatio) {
            // más ancha: limitar por ancho de pantalla
            baseScaleX = screenRatio
            baseScaleY = screenRatio / imgRatio
        } else {
            baseScaleX = imgRatio
            baseScaleY = 1f
        }

        val zoom = layer.zoom.coerceIn(0.05f, 10f)
        val scaleX = baseScaleX * zoom
        val scaleY = baseScaleY * zoom

        // x,y 0..1 → NDC (centro de pantalla = 0.5)
        val tx = (layer.x.coerceIn(0f, 1f) - 0.5f) * 2f * screenRatio
        val ty = (0.5f - layer.y.coerceIn(0f, 1f)) * 2f

        val mvp = FloatArray(16)
        val proj = FloatArray(16)
        val model = FloatArray(16)
        Matrix.orthoM(proj, 0, -screenRatio, screenRatio, -1f, 1f, -1f, 1f)
        Matrix.setIdentityM(model, 0)
        Matrix.translateM(model, 0, tx, ty, 0f)
        Matrix.rotateM(model, 0, layer.rotation, 0f, 0f, 1f)
        Matrix.scaleM(model, 0, scaleX, scaleY, 1f)
        Matrix.multiplyMM(mvp, 0, proj, 0, model, 0)

        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        GLES20.glUseProgram(program)

        vertexBuffer.position(0)
        GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false, 16, vertexBuffer)
        GLES20.glEnableVertexAttribArray(positionHandle)
        vertexBuffer.position(2)
        GLES20.glVertexAttribPointer(texCoordHandle, 2, GLES20.GL_FLOAT, false, 16, vertexBuffer)
        GLES20.glEnableVertexAttribArray(texCoordHandle)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        tex.bind(0)
        GLES20.glUniform1i(samplerHandle, 0)
        GLES20.glUniform1f(alphaHandle, (layer.opacity * fadeAlpha).coerceIn(0f, 1f))
        GLES20.glUniformMatrix4fv(mvpHandle, 1, false, mvp, 0)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 6)

        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(texCoordHandle)
        tex.unbind()
        GLES20.glDisable(GLES20.GL_BLEND)
    }

    private fun uploadMovieFrame(id: String, movie: Movie, timeMs: Int) {
        val w = movie.width()
        val h = movie.height()
        if (w <= 0 || h <= 0) return
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.TRANSPARENT)
        movie.setTime(timeMs)
        movie.draw(canvas, 0f, 0f)
        textures[id]?.upload(bmp)
        bmp.recycle()
    }

    fun release() {
        textures.values.forEach { it.release() }
        textures.clear()
        movies.clear()
        if (program != 0) {
            GLES20.glDeleteProgram(program)
            program = 0
        }
    }

    private fun layerFadeAlpha(layer: ImageLayer): Float {
        val start = fadeStartTimes[layer.id] ?: return 1f
        val dur = layer.fadeDurationMs.coerceAtLeast(1L)
        val elapsed = android.os.SystemClock.elapsedRealtime() - start
        val a = (elapsed.toFloat() / dur.toFloat()).coerceIn(0f, 1f)
        if (a >= 1f) fadeStartTimes.remove(layer.id)
        return a
    }

    private fun compile(type: Int, code: String): Int {
        val s = GLES20.glCreateShader(type)
        GLES20.glShaderSource(s, code)
        GLES20.glCompileShader(s)
        return s
    }
}
