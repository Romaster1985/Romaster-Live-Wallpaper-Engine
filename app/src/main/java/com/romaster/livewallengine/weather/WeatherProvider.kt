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

package com.romaster.livewallengine.weather

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.content.ContextCompat
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference

/**
 * Clima vía Open-Meteo (sin API key).
 * Cache en memoria ~20 min; refresh en background.
 */
object WeatherProvider {

    data class Snapshot(
        val tempC: Float = 0f,
        val tempF: Float = 32f,
        val feelsC: Float = 0f,
        val humidity: Int = 0,
        val windKmh: Float = 0f,
        val code: Int = 0,
        val condition: String = "—",
        val iconKey: String = "UNKNOWN",
        val updatedMs: Long = 0L
    )

    private val cache = AtomicReference(Snapshot())
    private val executor = Executors.newSingleThreadExecutor()
    private const val CACHE_MS = 20 * 60 * 1000L
    private const val UA = "Romaster-LiveWall-Engine"

    // Fallback: Buenos Aires
    private const val DEFAULT_LAT = -34.6037
    private const val DEFAULT_LON = -58.3816

    fun get(): Snapshot = cache.get()

    /** Dispara refresh si el cache expiró. Seguro llamar desde cualquier hilo. */
    fun ensureFresh(context: Context) {
        val snap = cache.get()
        if (System.currentTimeMillis() - snap.updatedMs < CACHE_MS && snap.updatedMs > 0L) return
        refreshAsync(context)
    }

    fun refreshAsync(context: Context) {
        val app = context.applicationContext
        executor.execute {
            try {
                val (lat, lon) = resolveLocation(app)
                val url =
                    "https://api.open-meteo.com/v1/forecast" +
                        "?latitude=$lat&longitude=$lon" +
                        "&current=temperature_2m,relative_humidity_2m,weather_code," +
                        "wind_speed_10m,apparent_temperature" +
                        "&wind_speed_unit=kmh&timezone=auto"
                val body = httpGet(url)
                val json = JSONObject(body)
                val cur = json.getJSONObject("current")
                val tempC = cur.optDouble("temperature_2m", 0.0).toFloat()
                val feels = cur.optDouble("apparent_temperature", tempC.toDouble()).toFloat()
                val humidity = cur.optInt("relative_humidity_2m", 0)
                val wind = cur.optDouble("wind_speed_10m", 0.0).toFloat()
                val code = cur.optInt("weather_code", 0)
                val (cond, icon) = mapCode(code)
                cache.set(
                    Snapshot(
                        tempC = tempC,
                        tempF = tempC * 9f / 5f + 32f,
                        feelsC = feels,
                        humidity = humidity,
                        windKmh = wind,
                        code = code,
                        condition = cond,
                        iconKey = icon,
                        updatedMs = System.currentTimeMillis()
                    )
                )
            } catch (_: Exception) {
                // mantener cache anterior
            }
        }
    }

    fun field(name: String): String {
        val s = cache.get()
        return when (name.lowercase(Locale.ROOT)) {
            "temp", "tempc", "temp_c" -> formatNum(s.tempC)
            "tempf", "temp_f" -> formatNum(s.tempF)
            "feels", "feelsc" -> formatNum(s.feelsC)
            "humidity", "hum" -> s.humidity.toString()
            "wind" -> formatNum(s.windKmh)
            "code" -> s.code.toString()
            "cond", "condition" -> s.condition
            "icon" -> s.iconKey
            else -> ""
        }
    }

    private fun formatNum(v: Float): String {
        return if (v % 1f == 0f) v.toInt().toString()
        else String.format(Locale.US, "%.1f", v)
    }

    private fun resolveLocation(context: Context): Pair<Double, Double> {
        val fine = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) return DEFAULT_LAT to DEFAULT_LON
        return try {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val providers = listOf(
                LocationManager.NETWORK_PROVIDER,
                LocationManager.GPS_PROVIDER,
                LocationManager.PASSIVE_PROVIDER
            )
            var best: android.location.Location? = null
            for (p in providers) {
                try {
                    val loc = lm.getLastKnownLocation(p) ?: continue
                    if (best == null || loc.time > best.time) best = loc
                } catch (_: Exception) {
                }
            }
            if (best != null) best.latitude to best.longitude
            else DEFAULT_LAT to DEFAULT_LON
        } catch (_: Exception) {
            DEFAULT_LAT to DEFAULT_LON
        }
    }

    private fun httpGet(urlStr: String): String {
        val conn = URL(urlStr).openConnection() as HttpURLConnection
        conn.connectTimeout = 12000
        conn.readTimeout = 12000
        conn.requestMethod = "GET"
        conn.setRequestProperty("User-Agent", UA)
        conn.inputStream.bufferedReader().use { return it.readText() }
    }

    /**
     * WMO weather codes → (condición legible, clave de ícono KLWP).
     *
     * Claves de ícono exactas de KLWP ($wi(icon)$):
     * UNKNOWN, TORNADO, TSTORM, TSHOWER, SHOWER, RAIN, SLEET,
     * LSNOW, SNOW, HAIL, FOG, WINDY, PCLOUDY, MCLOUDY, CLEAR
     */
    private fun mapCode(code: Int): Pair<String, String> {
        return when (code) {
            0 -> "cielo claro" to "CLEAR"
            1 -> "mayormente despejado" to "PCLOUDY"
            2 -> "parcialmente nublado" to "PCLOUDY"
            3 -> "nublado" to "MCLOUDY"
            45, 48 -> "niebla" to "FOG"
            51, 53, 55 -> "llovizna" to "RAIN"
            56, 57 -> "llovizna helada" to "SLEET"
            61, 63 -> "lluvia" to "RAIN"
            65 -> "lluvia intensa" to "RAIN"
            66, 67 -> "lluvia helada" to "SLEET"
            71, 73 -> "nieve" to "SNOW"
            75, 77 -> "nieve intensa" to "SNOW"
            80 -> "chubascos" to "SHOWER"
            81, 82 -> "chubascos intensos" to "SHOWER"
            85 -> "chubascos de nieve" to "LSNOW"
            86 -> "chubascos de nieve intensos" to "LSNOW"
            95 -> "tormenta" to "TSTORM"
            96, 99 -> "tormenta con granizo" to "HAIL"
            else -> "desconocido" to "UNKNOWN"
        }
    }
}
