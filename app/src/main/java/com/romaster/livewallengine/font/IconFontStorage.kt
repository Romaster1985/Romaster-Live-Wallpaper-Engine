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

package com.romaster.livewallengine.font

import android.content.Context
import android.graphics.Typeface
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Almacena pares TTF+JSON de fuentes de íconos (IcoMoon y similares).
 * Directorio: filesDir/icon_fonts/
 */
object IconFontStorage {

    private const val DIR = "icon_fonts"

    fun getDir(context: Context): File {
        val d = File(context.filesDir, DIR)
        if (!d.exists()) d.mkdirs()
        return d
    }

    /** Nombres base instalados (sin extensión), ordenados. */
    fun listInstalled(context: Context): List<String> {
        val dir = getDir(context)
        val bases = mutableSetOf<String>()
        dir.listFiles()?.forEach { f ->
            when {
                f.name.endsWith(".ttf", true) ||
                    f.name.endsWith(".otf", true) ->
                    bases += f.name.substringBeforeLast('.')
                f.name.endsWith(".json", true) ->
                    bases += f.name.substringBeforeLast('.')
            }
        }
        return bases.sorted()
    }

    fun getTtf(context: Context, baseName: String): File? {
        val dir = getDir(context)
        listOf("ttf", "otf").forEach { ext ->
            val f = File(dir, "$baseName.$ext")
            if (f.exists()) return f
        }
        // buscar case-insensitive
        dir.listFiles()?.firstOrNull {
            it.name.substringBeforeLast('.').equals(baseName, true) &&
                (it.name.endsWith(".ttf", true) || it.name.endsWith(".otf", true))
        }?.let { return it }
        return null
    }

    fun getJson(context: Context, baseName: String): File? {
        val dir = getDir(context)
        val f = File(dir, "$baseName.json")
        if (f.exists()) return f
        return dir.listFiles()?.firstOrNull {
            it.name.substringBeforeLast('.').equals(baseName, true) &&
                it.name.endsWith(".json", true)
        }
    }

    fun loadTypeface(context: Context, baseName: String): Typeface? {
        val file = getTtf(context, baseName) ?: return null
        return try {
            Typeface.createFromFile(file)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Mapa nombre de glifo → carácter unicode.
     * Soporta JSON de IcoMoon (selection.json / icons).
     */
    fun loadGlyphMap(context: Context, baseName: String): Map<String, String> {
        val jsonFile = getJson(context, baseName) ?: return emptyMap()
        return try {
            parseIcoMoon(jsonFile.readText())
        } catch (_: Exception) {
            emptyMap()
        }
    }

    fun parseIcoMoon(jsonText: String): Map<String, String> {
        val map = mutableMapOf<String, String>()
        val root = JSONObject(jsonText)
        // Formato selection.json: icons[]
        val icons: JSONArray? = when {
            root.has("icons") -> root.getJSONArray("icons")
            root.has("iconSets") -> {
                // algunos exports anidan
                val sets = root.getJSONArray("iconSets")
                if (sets.length() > 0) sets.getJSONObject(0).optJSONArray("icons") else null
            }
            else -> null
        }
        if (icons != null) {
            for (i in 0 until icons.length()) {
                val icon = icons.getJSONObject(i)
                val props = icon.optJSONObject("properties") ?: icon
                val name = props.optString("name").ifBlank {
                    props.optString("ligature")
                }
                val code = when {
                    props.has("code") -> props.optInt("code")
                    props.has("codes") -> {
                        val arr = props.optJSONArray("codes")
                        if (arr != null && arr.length() > 0) arr.optInt(0) else -1
                    }
                    else -> -1
                }
                if (name.isNotBlank() && code >= 0) {
                    map[name.lowercase()] = String(Character.toChars(code))
                    // aliases separados por coma
                    name.split(',').forEach { alias ->
                        val a = alias.trim().lowercase()
                        if (a.isNotEmpty()) map[a] = String(Character.toChars(code))
                    }
                }
            }
        }
        return map
    }

    /** Resuelve texto: cada palabra clave de glifo se reemplaza; resto se deja. */
    fun resolveGlyphText(text: String, glyphMap: Map<String, String>): String {
        if (glyphMap.isEmpty() || text.isEmpty()) return text
        // Intento 1: match exacto de toda la cadena
        glyphMap[text.trim().lowercase()]?.let { return it }
        // Intento 2: tokenizar por espacios / separadores
        val parts = text.split(Regex("(\\s+)"))
        return parts.joinToString("") { token ->
            if (token.isBlank()) token
            else glyphMap[token.lowercase()] ?: token
        }
    }

    fun saveFromBytes(context: Context, fileName: String, data: ByteArray) {
        val out = File(getDir(context), fileName)
        FileOutputStream(out).use { it.write(data) }
    }

    fun downloadPair(context: Context, baseName: String, ttfUrl: String, jsonUrl: String) {
        downloadTo(context, "$baseName.ttf", ttfUrl)
        downloadTo(context, "$baseName.json", jsonUrl)
    }

    private fun downloadTo(context: Context, fileName: String, urlStr: String) {
        val conn = URL(urlStr).openConnection() as HttpURLConnection
        conn.connectTimeout = 20000
        conn.readTimeout = 30000
        conn.setRequestProperty("User-Agent", "Romaster-LiveWall-Engine")
        conn.inputStream.use { input ->
            FileOutputStream(File(getDir(context), fileName)).use { output ->
                input.copyTo(output)
            }
        }
    }
}
