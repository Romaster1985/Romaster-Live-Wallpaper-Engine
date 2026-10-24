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
 */

package com.romaster.livewallengine.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.romaster.livewallengine.formula.FormulaEngine
import com.romaster.livewallengine.project.ProjectManager
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Resuelve fórmulas de imagen (p. ej. `$mi(cover)$`) a un archivo local cacheado
 * para que el renderer GL pueda subir texturas como con cualquier PNG/JPG.
 */
object FormulaImageCache {

    private val executor = Executors.newSingleThreadExecutor()
    private val lastSource = ConcurrentHashMap<String, String>()
    private val inFlight = ConcurrentHashMap<String, Boolean>()
    private val dirty = ConcurrentHashMap<String, Boolean>()
    private val busy = AtomicBoolean(false)

    fun cacheFile(context: Context, layerId: String): File {
        val dir = File(context.filesDir, "images_formula")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "formula_$layerId.jpg")
    }

    /** true si el archivo de esa capa cambió desde la última subida GL. */
    fun consumeDirty(layerId: String): Boolean = dirty.remove(layerId) == true

    fun hasCached(context: Context, layerId: String): Boolean =
        cacheFile(context, layerId).exists()

    /**
     * Evalúa la fórmula; si el resultado cambió, descarga/decodifica en background.
     * Seguro llamar desde cualquier hilo (incluye el de GL).
     */
    fun requestRefresh(context: Context, layerId: String, formula: String) {
        if (formula.isBlank()) return
        val app = context.applicationContext
        val source = try {
            FormulaEngine.evaluate(formula, app).trim()
        } catch (_: Exception) {
            ""
        }
        if (source.isEmpty()) return
        val prev = lastSource[layerId]
        val file = cacheFile(app, layerId)
        if (source == prev && file.exists()) return
        if (inFlight[layerId] == true) return
        inFlight[layerId] = true
        executor.execute {
            try {
                val bmp = decodeSource(app, source)
                if (bmp != null && !bmp.isRecycled) {
                    try {
                        FileOutputStream(file).use { out ->
                            bmp.compress(Bitmap.CompressFormat.JPEG, 92, out)
                        }
                        lastSource[layerId] = source
                        dirty[layerId] = true
                    } finally {
                        if (!bmp.isRecycled) bmp.recycle()
                    }
                }
            } catch (_: Exception) {
            } finally {
                inFlight[layerId] = false
            }
        }
    }

    /** Refresca todas las capas del proyecto que tienen imageFormula. */
    fun refreshAllFromProject(context: Context) {
        if (!busy.compareAndSet(false, true)) return
        try {
            val layers = ProjectManager.getProject().imageLayers
            for (layer in layers) {
                val f = layer.imageFormula
                if (f.isNotBlank()) {
                    requestRefresh(context, layer.id, f)
                }
            }
        } catch (_: Exception) {
        } finally {
            busy.set(false)
        }
    }

    private fun decodeSource(context: Context, source: String): Bitmap? {
        val s = source.trim()
        if (s.isEmpty()) return null
        return try {
            when {
                s.startsWith("http://", true) || s.startsWith("https://", true) -> {
                    decodeHttp(s)
                }
                s.startsWith("content://", true) || s.startsWith("file://", true) -> {
                    context.contentResolver.openInputStream(Uri.parse(s))?.use { input ->
                        decodeStreamSampled(input.readBytes())
                    }
                }
                s.startsWith("/") -> {
                    // Ruta absoluta local (p. ej. cache de MusicInfoProvider)
                    BitmapFactory.decodeFile(s, optionsSampled(s))
                }
                else -> {
                    // Intentar como URI genérica o path relativo
                    try {
                        context.contentResolver.openInputStream(Uri.parse(s))?.use { input ->
                            decodeStreamSampled(input.readBytes())
                        }
                    } catch (_: Exception) {
                        val f = File(s)
                        if (f.exists()) BitmapFactory.decodeFile(f.absolutePath, optionsSampled(f.absolutePath))
                        else null
                    }
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun decodeHttp(urlStr: String): Bitmap? {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            connectTimeout = 12000
            readTimeout = 12000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Romaster-LiveWall-Engine")
        }
        return try {
            conn.inputStream.use { input ->
                decodeStreamSampled(input.readBytes())
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun decodeStreamSampled(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        val maxSide = 2048
        while ((bounds.outWidth / sample) > maxSide || (bounds.outHeight / sample) > maxSide) {
            sample *= 2
        }
        return BitmapFactory.decodeByteArray(
            bytes, 0, bytes.size,
            BitmapFactory.Options().apply { inSampleSize = sample }
        )
    }

    private fun optionsSampled(path: String): BitmapFactory.Options {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        var sample = 1
        val maxSide = 2048
        while ((bounds.outWidth / sample) > maxSide || (bounds.outHeight / sample) > maxSide) {
            sample *= 2
        }
        return BitmapFactory.Options().apply { inSampleSize = sample }
    }

    fun clearLayer(context: Context, layerId: String) {
        lastSource.remove(layerId)
        dirty.remove(layerId)
        inFlight.remove(layerId)
        try {
            cacheFile(context, layerId).delete()
        } catch (_: Exception) {
        }
    }
}
