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

package com.romaster.livewallengine.formula

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.regex.Pattern

/**
 * Motor de fórmulas estilo KLWP (fase 1).
 *
 * Soporta:
 * - Segmentos `$función(args)$` intercalados con texto fijo
 * - `$df(formato)$` fecha/hora (tokens tipo KLWP: hh, mm, ss, EEEE, …)
 * - `$bi(campo)$` batería: level, charging, volt, tempc
 *
 * Próximas fases: `$if(...)`, `$wi(...)` (Open-Meteo), math, etc.
 */
object FormulaEngine {

    private val segmentPattern: Pattern =
        Pattern.compile("\\$([a-zA-Z_][a-zA-Z0-9_]*)\\(([^$]*)\\)\\$")

    fun evaluate(formula: String, context: Context? = null): String {
        if (formula.isEmpty()) return ""
        val matcher = segmentPattern.matcher(formula)
        val out = StringBuilder()
        var last = 0
        while (matcher.find()) {
            out.append(formula, last, matcher.start())
            val fn = matcher.group(1)?.lowercase(Locale.ROOT) ?: ""
            val args = matcher.group(2) ?: ""
            out.append(evalFunction(fn, args, context))
            last = matcher.end()
        }
        out.append(formula, last, formula.length)
        return out.toString()
    }

    private fun evalFunction(fn: String, args: String, context: Context?): String {
        return when (fn) {
            "df" -> evalDateFormat(args.trim())
            "bi" -> evalBattery(args.trim(), context)
            else -> "\$$fn($args)$" // desconocida: dejar visible
        }
    }

    // ---- Fecha / hora ($df) ----

    private fun evalDateFormat(pattern: String): String {
        if (pattern.isEmpty()) return ""
        val cal = Calendar.getInstance()
        // Traducir tokens KLWP → SimpleDateFormat (orden importa: largos primero)
        var sdfPattern = pattern
        val replacements = listOf(
            "EEEE" to "EEEE",
            "EEE" to "EEE",
            "MMMM" to "MMMM",
            "MMM" to "MMM",
            "yyyy" to "yyyy",
            "yy" to "yy",
            "hh" to "HH", // 24h con cero; KLWP hh suele ser 24h en muchos themes
            "h" to "H",
            "mm" to "mm",
            "m" to "m",
            "ssa" to "ss", // KLWP a veces usa ssa con am/pm marker aparte
            "ss" to "ss",
            "s" to "s",
            "dd" to "dd",
            "d" to "d",
            "MM" to "MM",
            "aa" to "a",
            "a" to "a",
            "w" to "w",
            "D" to "D"
        )
        // Reemplazo cuidadoso con marcadores temporales para no pisar
        val markers = mutableListOf<Pair<String, String>>()
        var work = sdfPattern
        replacements.forEachIndexed { i, (from, to) ->
            if (work.contains(from)) {
                val mark = "\uE000$i\uE001"
                work = work.replace(from, mark)
                markers += mark to to
            }
        }
        markers.forEach { (mark, to) ->
            work = work.replace(mark, to)
        }
        return try {
            SimpleDateFormat(work, Locale.getDefault()).format(cal.time)
        } catch (_: Exception) {
            // Fallback manual por si el patrón mezcló literales raros
            manualDate(pattern, cal)
        }
    }

    private fun manualDate(pattern: String, cal: Calendar): String {
        var s = pattern
        fun pad2(n: Int) = n.toString().padStart(2, '0')
        s = s.replace("EEEE", cal.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.LONG, Locale.getDefault()) ?: "")
        s = s.replace("EEE", cal.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.SHORT, Locale.getDefault()) ?: "")
        s = s.replace("MMMM", cal.getDisplayName(Calendar.MONTH, Calendar.LONG, Locale.getDefault()) ?: "")
        s = s.replace("MMM", cal.getDisplayName(Calendar.MONTH, Calendar.SHORT, Locale.getDefault()) ?: "")
        s = s.replace("yyyy", cal.get(Calendar.YEAR).toString())
        s = s.replace("hh", pad2(cal.get(Calendar.HOUR_OF_DAY)))
        s = s.replace("mm", pad2(cal.get(Calendar.MINUTE)))
        s = s.replace("ss", pad2(cal.get(Calendar.SECOND)))
        s = s.replace("dd", pad2(cal.get(Calendar.DAY_OF_MONTH)))
        s = s.replace("MM", pad2(cal.get(Calendar.MONTH) + 1))
        return s
    }

    // ---- Batería ($bi) ----

    private fun evalBattery(field: String, context: Context?): String {
        if (context == null) return ""
        val intent = try {
            context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        } catch (_: Exception) {
            null
        } ?: return ""

        return when (field.lowercase(Locale.ROOT)) {
            "level" -> {
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                if (level < 0 || scale <= 0) "0"
                else ((level * 100f) / scale).toInt().toString()
            }
            "charging" -> {
                val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL
                if (charging) "1" else "0"
            }
            "volt" -> {
                val mv = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0)
                mv.toString()
            }
            "tempc", "temp" -> {
                // EXTRA_TEMPERATURE viene en décimas de °C
                val t = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)
                (t / 10).toString()
            }
            "fast" -> "0"
            else -> ""
        }
    }
}
