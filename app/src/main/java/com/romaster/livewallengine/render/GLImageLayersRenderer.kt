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
import com.romaster.livewallengine.image.FormulaImageCache
import com.romaster.livewallengine.model.ImageLayer
import com.romaster.livewallengine.project.ProjectManager
import com.romaster.livewallengine.animation.AnimationEngine
import com.romaster.livewallengine.effect.EffectEngine
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
    private var modelHandle = 0

    private lateinit var vertexBuffer: FloatBuffer
    private val textures = mutableMapOf<String, GLTexture>()
    /** Último chequeo de fórmula por capa (ms elapsed). */
    private val lastFormulaCheckMs = mutableMapOf<String, Long>()
    private val movies = mutableMapOf<String, Movie>()
    private val movieStart = mutableMapOf<String, Long>()
    private val bitmapSize = mutableMapOf<String, Pair<Int, Int>>()
    private val fadeStartTimes = mutableMapOf<String, Long>()
    /** Fade-out al ocultar en launcher (desbloqueo) cuando enableLauncherFadeOut. */
    private val fadeOutStartTimes = mutableMapOf<String, Long>()
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
        uniform mat4 uModel;
        varying vec2 vTexCoord;
        varying vec2 vOrtho;
        void main() {
            vec4 o = uModel * vec4(aPosition, 0.0, 1.0);
            vOrtho = o.xy;
            gl_Position = uMVP * vec4(aPosition, 0.0, 1.0);
            vTexCoord = aTexCoord;
        }
    """.trimIndent()

    private val fShader = """
        precision mediump float;
        varying vec2 vTexCoord;
        uniform sampler2D uTexture;
        uniform float uAlpha;

        uniform float uFxSat;
        uniform float uFxHue;
        uniform float uFxInvert;
        uniform float uFxInvertLuma;
        uniform float uFxSolar;
        uniform vec3  uFxTint;
        uniform float uFxTintStr;
        uniform float uFxTintMode;
uniform sampler2D uMask;
        uniform float uMaskEnabled;
        uniform vec2 uMaskOffset;
        uniform float uMaskScale;
        uniform float uMaskRot;
        uniform float uMaskInvert;
        uniform float uMaskAspect;
        uniform float uLayerAspect;
        uniform float uMaskFit;
        float fxMaskAlpha() {
            if (uMaskEnabled < 0.5) return 1.0;
            vec2 p = vTexCoord - 0.5;
            p -= uMaskOffset;
            float la = max(uLayerAspect, 0.01);
            float ma = max(uMaskAspect, 0.01);
            vec2 s = vec2(p.x * la, p.y);
            float c0 = cos(-uMaskRot);
            float s0 = sin(-uMaskRot);
            s = vec2(c0 * s.x - s0 * s.y, s0 * s.x + c0 * s.y);
            float sc = max(uMaskScale, 0.001);
            s /= sc;
            vec2 uv;
            if (uMaskFit > 0.5) {
                uv = vec2(s.x / la, s.y) + 0.5;
            } else {
                uv = vec2(s.x / ma, s.y) + 0.5;
            }
            float m = 0.0;
            if (uv.x >= 0.0 && uv.x <= 1.0 && uv.y >= 0.0 && uv.y <= 1.0) {
                vec4 mt = texture2D(uMask, uv);
                float lum = max(mt.r, max(mt.g, mt.b));
                m = mt.a * lum;
            }
            if (uMaskInvert > 0.5) m = 1.0 - m;
            return m;
        }

        vec3 fxRgb2Hsv(vec3 c) {
            vec4 K = vec4(0.0, -1.0 / 3.0, 2.0 / 3.0, -1.0);
            vec4 p = mix(vec4(c.bg, K.wz), vec4(c.gb, K.xy), step(c.b, c.g));
            vec4 q = mix(vec4(p.xyw, c.r), vec4(c.r, p.yzx), step(p.x, c.r));
            float d = q.x - min(q.w, q.y);
            float e = 1.0e-10;
            return vec3(abs(q.z + (q.w - q.y) / (6.0 * d + e)), d / (q.x + e), q.x);
        }
        vec3 fxHsv2Rgb(vec3 c) {
            vec4 K = vec4(1.0, 2.0 / 3.0, 1.0 / 3.0, 3.0);
            vec3 p = abs(fract(c.xxx + K.xyz) * 6.0 - K.www);
            return c.z * mix(K.xxx, clamp(p - K.xxx, 0.0, 1.0), c.y);
        }
        vec3 fxApplyColor(vec3 c) {
            float gray = dot(c, vec3(0.299, 0.587, 0.114));
            c = mix(vec3(gray), c, uFxSat);
            if (abs(uFxHue) > 0.01) {
                vec3 hsv = fxRgb2Hsv(clamp(c, 0.0, 1.0));
                hsv.x = fract(hsv.x + uFxHue / 360.0);
                c = fxHsv2Rgb(hsv);
            }
            if (uFxTintStr > 0.001) {
                if (uFxTintMode > 0.5) c = mix(c, c * uFxTint, uFxTintStr);
                else c = mix(c, uFxTint, uFxTintStr);
            }
            if (uFxSolar >= 0.0) {
                float l = dot(c, vec3(0.299, 0.587, 0.114));
                if (l > uFxSolar) c = 1.0 - c;
            }
            if (uFxInvert > 0.5) {
                if (uFxInvertLuma > 0.5) {
                    vec3 hsv = fxRgb2Hsv(clamp(c, 0.0, 1.0));
                    hsv.x = fract(hsv.x + 0.5);
                    c = fxHsv2Rgb(hsv);
                } else {
                    c = 1.0 - c;
                }
            }
            return clamp(c, 0.0, 1.0);
        }


        void main() {
            vec4 c = texture2D(uTexture, vTexCoord);
            c.rgb = fxApplyColor(c.rgb);
            c.a *= uAlpha * fxMaskAlpha();
            gl_FragColor = c;
        }
    """.trimIndent()

    fun initialize(context: Context, width: Int, height: Int) {
        this.context = context.applicationContext
        EffectEngine.setContext(context)
        EffectEngine.clearTextureCache()
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
        modelHandle = GLES20.glGetUniformLocation(program, "uModel")

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

        // Solicitar actualización de fórmulas (mi(cover), http, content://…)
        try {
            FormulaImageCache.refreshAllFromProject(ctx)
        } catch (_: Exception) {
        }

        for (layer in layers) {
            val formula = layer.imageFormula
            val useFormula = formula.isNotBlank()
            if (useFormula) {
                FormulaImageCache.requestRefresh(ctx, layer.id, formula)
            }
            val file = when {
                useFormula -> FormulaImageCache.cacheFile(ctx, layer.id)
                else -> {
                    val name = layer.fileName ?: continue
                    ImageStorage.getImageFile(ctx, name)
                }
            }
            if (!file.exists()) continue
            // Recargar si force, no hay textura, o el cache de fórmula cambió
            val formulaDirty = useFormula && FormulaImageCache.consumeDirty(layer.id)
            if (!force && !formulaDirty && textures.containsKey(layer.id)) continue
            if (formulaDirty) {
                textures.remove(layer.id)?.release()
                movies.remove(layer.id)
                movieStart.remove(layer.id)
                bitmapSize.remove(layer.id)
            }
            val name = file.name

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
            if (fadeOutStartTimes.containsKey(layer.id)) continue
            fadeStartTimes[layer.id] = now + layer.delayStartMs.coerceAtLeast(0L)
        }
    }

    fun startSoftStart(id: String) {
        if (forceHidden[id] == true) return
        if (fadeOutStartTimes.containsKey(id)) return
        val layer = ProjectManager.getProject().imageLayers.find { it.id == id }
        val delay = layer?.delayStartMs?.coerceAtLeast(0L) ?: 0L
        fadeStartTimes[id] = android.os.SystemClock.elapsedRealtime() + delay
    }

    /** Visible según flags de bloqueo / launcher (igual que Widgets-OL). */
    private fun shouldShow(layer: ImageLayer, deviceLocked: Boolean): Boolean {
        if (deviceLocked && layer.disableOnLockScreen) return false
        if (!deviceLocked && layer.disableOnLauncher) return false
        return true
    }

    /**
     * Sincroniza visibilidad con el estado de bloqueo actual (sin animación).
     * Usar al recrear surface / cambio de proyecto. El fade-out de desbloqueo
     * se hace en [revealAfterUnlock].
     */
    fun applyLockScreenState(deviceLocked: Boolean) {
        val layers = ProjectManager.getProject().imageLayers
        for (layer in layers) {
            val show = shouldShow(layer, deviceLocked)
            forceHidden[layer.id] = !show
            if (!show) {
                fadeStartTimes.remove(layer.id)
                fadeOutStartTimes.remove(layer.id)
            } else {
                // Visible: cancelar cualquier fade-out pendiente
                fadeOutStartTimes.remove(layer.id)
            }
        }
    }

    /** Soft Start de capas visibles en pantalla de bloqueo. */
    fun startSoftStartOnLockScreen() {
        val layers = ProjectManager.getProject().imageLayers
        val now = android.os.SystemClock.elapsedRealtime()
        for (layer in layers) {
            if (shouldShow(layer, deviceLocked = true)) {
                forceHidden[layer.id] = false
                fadeOutStartTimes.remove(layer.id)
                fadeStartTimes[layer.id] = now + layer.delayStartMs.coerceAtLeast(0L)
            } else {
                forceHidden[layer.id] = true
                fadeStartTimes.remove(layer.id)
                fadeOutStartTimes.remove(layer.id)
            }
        }
    }

    /**
     * Al desbloquear:
     * - visible en lock y launcher → sin re-fade
     * - oculto en lock (disableOnLockScreen) y visible en launcher → Soft Start
     * - oculto en launcher (disableOnLauncher):
     *     - enableLauncherFadeOut → fade-out con [ImageLayer.fadeDurationMs]
     *     - si no → ocultar de inmediato
     */
    fun revealAfterUnlock() {
        val layers = ProjectManager.getProject().imageLayers
        val now = android.os.SystemClock.elapsedRealtime()
        for (layer in layers) {
            if (!shouldShow(layer, deviceLocked = false)) {
                fadeStartTimes.remove(layer.id)
                if (layer.disableOnLauncher && layer.enableLauncherFadeOut) {
                    // Seguir dibujando mientras hace fade-out
                    forceHidden[layer.id] = false
                    fadeOutStartTimes[layer.id] = now
                } else {
                    forceHidden[layer.id] = true
                    fadeOutStartTimes.remove(layer.id)
                }
            } else {
                forceHidden[layer.id] = false
                fadeOutStartTimes.remove(layer.id)
                if (layer.disableOnLockScreen) {
                    fadeStartTimes[layer.id] = now + layer.delayStartMs.coerceAtLeast(0L)
                } else {
                    fadeStartTimes.remove(layer.id) // ya visible en lock
                }
            }
        }
    }

    /** Dibuja una capa de imagen por id (orden lo decide layerStack). */
    fun drawById(id: String) {
        val layer = ProjectManager.getProject().imageLayers
            .find {
                it.id == id && (it.fileName != null || it.imageFormula.isNotBlank())
            } ?: return
        // Actualizar carátula / fórmula ~3 veces por segundo (detecta cambio de tema)
        if (layer.imageFormula.isNotBlank()) {
            context?.let { ctx ->
                val now = android.os.SystemClock.elapsedRealtime()
                val last = lastFormulaCheckMs[layer.id] ?: 0L
                if (now - last >= 350L) {
                    lastFormulaCheckMs[layer.id] = now
                    FormulaImageCache.requestRefresh(ctx, layer.id, layer.imageFormula)
                }
                if (FormulaImageCache.consumeDirty(layer.id) || !textures.containsKey(layer.id)) {
                    textures.remove(layer.id)?.release()
                    movies.remove(layer.id)
                    movieStart.remove(layer.id)
                    bitmapSize.remove(layer.id)
                    reloadFromProject(force = false)
                }
            }
        }
        drawLayer(layer)
    }

    private fun drawLayer(layer: ImageLayer) {
        // Durante fade-out forceHidden se mantiene false hasta alpha≈0
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

        val anim = AnimationEngine.stateForImage(layer.id)
        val zoom = (layer.zoom * anim.zoomMul).coerceIn(0.05f, 20f)
        val scaleX = baseScaleX * zoom
        val scaleY = baseScaleY * zoom

        // x,y: centro=0 en % de pantalla (-200..200); offset anim en fracción de pantalla
        val tx2 = (layer.x / 100f) * screenRatio + anim.offsetXNorm * 2f * screenRatio
        val ty2 = -(layer.y / 100f) - anim.offsetYNorm * 2f

        val mvp = FloatArray(16)
        val proj = FloatArray(16)
        val model = FloatArray(16)
        Matrix.orthoM(proj, 0, -screenRatio, screenRatio, -1f, 1f, -1f, 1f)
        Matrix.setIdentityM(model, 0)
        Matrix.translateM(model, 0, tx2, ty2, 0f)
        Matrix.rotateM(model, 0, layer.rotation + anim.rotationDeg, 0f, 0f, 1f)
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
        GLES20.glUniform1f(
            alphaHandle,
            (layer.opacity * fadeAlpha * anim.alphaMul).coerceIn(0f, 1f)
        )
        EffectEngine.bindColorFx(program, EffectEngine.paramsFor("img:" + layer.id))
        if (modelHandle >= 0) {
            GLES20.glUniformMatrix4fv(modelHandle, 1, false, model, 0)
        }
        EffectEngine.setScreenRatio(screenRatio)
        EffectEngine.bindMaskFx(program, "img:" + layer.id, imgRatio)
        // Restaurar textura de la capa en unidad 0 tras bind de máscara
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        tex.bind(0)
        GLES20.glUniform1i(samplerHandle, 0)

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

    fun isFadeComplete(): Boolean =
        fadeStartTimes.isEmpty() && fadeOutStartTimes.isEmpty()

    private fun layerFadeAlpha(layer: ImageLayer): Float {
        val now = android.os.SystemClock.elapsedRealtime()
        // Fade-out (launcher): 1 → 0 y luego forceHidden
        fadeOutStartTimes[layer.id]?.let { start ->
            val dur = layer.fadeDurationMs.coerceAtLeast(1L)
            if (now < start) return 1f
            val elapsed = now - start
            val a = 1f - (elapsed.toFloat() / dur.toFloat()).coerceIn(0f, 1f)
            if (a <= 0.001f) {
                fadeOutStartTimes.remove(layer.id)
                forceHidden[layer.id] = true
                return 0f
            }
            return a
        }
        val start = fadeStartTimes[layer.id] ?: return 1f
        // Delay Start: start guarda el instante en que debe comenzar el fade-in
        if (now < start) return 0f
        val dur = layer.fadeDurationMs.coerceAtLeast(1L)
        val elapsed = now - start
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
