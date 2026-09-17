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
import java.util.Calendar
import java.util.Locale
import com.romaster.livewallengine.weather.WeatherProvider

/**
 * Motor de fórmulas estilo KLWP.
 *
 * - `$ ... $` delimita un bloque de expresión.
 * - Fuera de los `$` el texto es literal (incluye saltos de línea).
 * - Dentro: se expanden df(...) y bi(...); si queda aritmética pura, se calcula.
 *
 * Ejemplos:
 * - `$df(hh:mm)$` → hora
 * - `$df(EEEE), df(dd) df(MMM) df(yyyy)$` → jueves, 17 sep. 2026
 * - `$df(yyyy)+4$` → 2030
 * - `$bi(level)$%` → 90%
 */
object FormulaEngine {

    fun evaluate(formula: String, context: Context? = null): String {
        if (formula.isEmpty()) return ""
        val out = StringBuilder()
        var i = 0
        val n = formula.length
        while (i < n) {
            if (formula[i] == '$') {
                val end = formula.indexOf('$', i + 1)
                if (end < 0) {
                    out.append(formula, i, n)
                    break
                }
                val inner = formula.substring(i + 1, end)
                out.append(evalBlock(inner, context))
                i = end + 1
            } else {
                out.append(formula[i])
                i++
            }
        }
        return out.toString()
    }

    private fun evalBlock(block: String, context: Context?): String {
        if (block.isEmpty()) return ""

        // Bloque que es solo un literal entre comillas → texto plano (sin calcular)
        // $"20*2+5"$ → 20*2+5
        val onlyLiteral = extractWholeStringLiteral(block.trim())
        if (onlyLiteral != null) return onlyLiteral

        // Expandir df()/bi() respetando comillas; los literales quedan como texto
        val expanded = expandFunctions(block, context)
        val trimmed = expanded.trim()

        // Bloque 100% matemático → un solo resultado
        if (trimmed.isNotEmpty() && looksLikeMath(trimmed) && hasMathOperator(trimmed)) {
            return try {
                formatNumber(evalMath(trimmed))
            } catch (_: Exception) {
                expanded
            }
        }
        // Texto + math: p.ej. 14:03 (2026+4) → 14:03 2030
        return evalInlineMath(expanded)
    }

    /** Si el string completo es `"...."`, devuelve el contenido interior; si no, null. */
    private fun extractWholeStringLiteral(s: String): String? {
        if (s.length >= 2 && s.first() == '"' && s.last() == '"') {
            // Verificar que no haya comilla de cierre antes del final (sin escapes simples)
            val inner = s.substring(1, s.length - 1)
            if (!inner.contains('"')) return inner
        }
        return null
    }

    /**
     * Evalúa paréntesis matemáticos puros de adentro hacia afuera y luego
     * tramos number-op-number contiguos. No toca regiones entre comillas.
     */
    private fun evalInlineMath(input: String): String {
        if (input.isEmpty()) return input
        var s = input
        // 1) Paréntesis puros, repetir hasta que no cambie
        var guard = 0
        while (guard++ < 32) {
            val next = evalOneParenMath(s)
            if (next == s) break
            s = next
        }
        // 2) Tramos math contiguos con operador (sin espacios que fusionen números sueltos)
        s = evalContiguousMath(s)
        return s
    }

    /** Reemplaza el primer `(expr)` matemático puro más interno que encuentre. */
    private fun evalOneParenMath(input: String): String {
        var i = 0
        val n = input.length
        var inQuote = false
        while (i < n) {
            val c = input[i]
            if (c == '"') {
                inQuote = !inQuote
                i++
                continue
            }
            if (!inQuote && c == '(') {
                val close = findMatchingParen(input, i)
                if (close > i) {
                    val inner = input.substring(i + 1, close)
                    // Solo el más interno (sin paréntesis anidados)
                    if (!inner.contains('(') &&
                        looksLikeMath(inner) &&
                        hasMathOperator(inner)
                    ) {
                        try {
                            val value = formatNumber(evalMath(inner.trim()))
                            return input.substring(0, i) + value + input.substring(close + 1)
                        } catch (_: Exception) {
                            // seguir buscando otro par
                        }
                    }
                }
            }
            i++
        }
        return input
    }

    /**
     * Evalúa secuencias type 12+3*4 sin paréntesis, fuera de comillas.
     * No une números separados solo por espacios (evita "03 (2026+4)" ya resuelto).
     */
    private fun evalContiguousMath(input: String): String {
        val out = StringBuilder()
        var i = 0
        val n = input.length
        var inQuote = false
        while (i < n) {
            val c = input[i]
            if (c == '"') {
                inQuote = !inQuote
                out.append(c)
                i++
                continue
            }
            if (inQuote) {
                out.append(c)
                i++
                continue
            }
            if (isMathChar(c) && c != '(' && c != ')') {
                val start = i
                while (i < n && isMathChar(input[i]) && input[i] != '(' && input[i] != ')') i++
                val segment = input.substring(start, i)
                if (hasMathOperator(segment) && looksLikeMath(segment)) {
                    try {
                        out.append(formatNumber(evalMath(segment)))
                    } catch (_: Exception) {
                        out.append(segment)
                    }
                } else {
                    out.append(segment)
                }
            } else {
                out.append(c)
                i++
            }
        }
        // Quitar comillas de literales ya resueltos: "texto" → texto (solo pares completos)
        return stripResolvedQuotes(out.toString())
    }

    private fun stripResolvedQuotes(s: String): String {
        val out = StringBuilder()
        var i = 0
        while (i < s.length) {
            if (s[i] == '"') {
                val close = s.indexOf('"', i + 1)
                if (close > i) {
                    out.append(s, i + 1, close)
                    i = close + 1
                    continue
                }
            }
            out.append(s[i])
            i++
        }
        return out.toString()
    }

    private fun isMathChar(c: Char): Boolean =
        c.isDigit() || c == '.' || c == '+' || c == '-' || c == '*' || c == '/' ||
            c == '(' || c == ')'

    private fun hasMathOperator(s: String): Boolean {
        var i = 0
        while (i < s.length && (s[i].isWhitespace() || s[i] == '+' || s[i] == '-' || s[i] == '(')) i++
        while (i < s.length) {
            val c = s[i]
            if (c == '+' || c == '-' || c == '*' || c == '/') return true
            i++
        }
        return false
    }

    /**
     * Expande df(...) / bi(...). El contenido entre comillas dobles se copia
     * tal cual (sin expandir ni calcular), sin las comillas en la salida final
     * se resuelven después.
     */
    private fun expandFunctions(input: String, context: Context?): String {
        val out = StringBuilder()
        var i = 0
        val n = input.length
        while (i < n) {
            // Literal "..."
            if (input[i] == '"') {
                val close = input.indexOf('"', i + 1)
                if (close > i) {
                    // Mantener comillas para que evalInlineMath no toque el interior
                    out.append(input, i, close + 1)
                    i = close + 1
                    continue
                }
            }
            if (i + 2 < n && isIdentStart(input[i])) {
                val nameStart = i
                var j = i + 1
                while (j < n && isIdentPart(input[j])) j++
                if (j < n && input[j] == '(') {
                    val name = input.substring(nameStart, j).lowercase(Locale.ROOT)
                    val argsEnd = findMatchingParen(input, j)
                    if (argsEnd >= 0 && (name == "df" || name == "bi" || name == "if" || name == "wi")) {
                        val args = input.substring(j + 1, argsEnd)
                        out.append(evalFunction(name, args, context))
                        i = argsEnd + 1
                        continue
                    }
                }
            }
            out.append(input[i])
            i++
        }
        return out.toString()
    }

    private fun evalFunction(fn: String, args: String, context: Context?): String {
        return when (fn) {
            "df" -> evalDateFormat(args.trim())
            "bi" -> evalBattery(args.trim(), context)
            "if" -> evalIf(args, context)
            "wi" -> evalWeather(args.trim(), context)
            else -> "$fn($args)"
        }
    }

    /**
     * $if(condición, valor_si_true, valor_si_false)$
     * Condición: comparaciones == != < > <= >= entre números/texto
     * tras expandir df/bi. Ej: if(bi(level)<20, LOW, OK)
     */
    private fun evalIf(args: String, context: Context?): String {
        val parts = splitArgs(args)
        if (parts.size < 2) return ""
        val condRaw = parts[0]
        val trueRaw = parts.getOrElse(1) { "" }
        val falseRaw = parts.getOrElse(2) { "" }

        val condExpanded = expandFunctions(condRaw, context)
        val result = evalCondition(condExpanded)
        val chosen = if (result) trueRaw else falseRaw
        // El branch elegido puede tener más funciones / math / literales
        return evalBlock(chosen, context)
    }

    /** Parte argumentos por comas respetando comillas y paréntesis. */
    private fun splitArgs(args: String): List<String> {
        val list = mutableListOf<String>()
        val cur = StringBuilder()
        var depth = 0
        var inQuote = false
        var i = 0
        while (i < args.length) {
            val c = args[i]
            when {
                c == '"' -> {
                    inQuote = !inQuote
                    cur.append(c)
                }
                !inQuote && c == '(' -> {
                    depth++
                    cur.append(c)
                }
                !inQuote && c == ')' -> {
                    depth--
                    cur.append(c)
                }
                !inQuote && depth == 0 && c == ',' -> {
                    list += cur.toString().trim()
                    cur.clear()
                }
                else -> cur.append(c)
            }
            i++
        }
        if (cur.isNotEmpty()) list += cur.toString().trim()
        return list
    }

    private fun evalCondition(expr: String): Boolean {
        return evalOrExpr(expr.trim())
    }

    /** OR de menor precedencia: |  or */
    private fun evalOrExpr(s: String): Boolean {
        val parts = splitLogical(s, orMode = true)
        if (parts.size == 1) return evalAndExpr(parts[0])
        return parts.any { evalAndExpr(it) }
    }

    /** AND: &  and */
    private fun evalAndExpr(s: String): Boolean {
        val parts = splitLogical(s, orMode = false)
        if (parts.size == 1) return evalNotExpr(parts[0])
        return parts.all { evalNotExpr(it) }
    }

    /** NOT: !  not */
    private fun evalNotExpr(s: String): Boolean {
        var t = s.trim()
        var negate = false
        while (true) {
            when {
                t.startsWith("!") -> {
                    negate = !negate
                    t = t.substring(1).trim()
                }
                t.startsWith("not ", ignoreCase = true) -> {
                    negate = !negate
                    t = t.substring(4).trim()
                }
                else -> break
            }
        }
        val v = evalComparison(t)
        return if (negate) !v else v
    }

    private fun evalComparison(s: String): Boolean {
        val ops = listOf("<=", ">=", "==", "!=", "=", "<", ">")
        for (op in ops) {
            val idx = indexOfOp(s, op)
            if (idx >= 0) {
                val left = s.substring(0, idx).trim()
                val right = s.substring(idx + op.length).trim()
                return compareValues(left, right, op)
            }
        }
        val n = s.toDoubleOrNull()
        if (n != null) return n != 0.0
        if (s.isEmpty()) return false
        return s.equals("true", true) || s == "1"
    }

    /**
     * Parte por | / or  o  & / and  respetando comillas y paréntesis.
     * orMode=true → separadores OR; false → AND.
     */
    private fun splitLogical(s: String, orMode: Boolean): List<String> {
        val out = mutableListOf<String>()
        val cur = StringBuilder()
        var depth = 0
        var inQuote = false
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '"') {
                inQuote = !inQuote
                cur.append(c)
                i++
                continue
            }
            if (!inQuote) {
                if (c == '(') {
                    depth++
                    cur.append(c)
                    i++
                    continue
                }
                if (c == ')') {
                    depth--
                    cur.append(c)
                    i++
                    continue
                }
                if (depth == 0) {
                    if (orMode) {
                        // "|" o " or " (con espacios)
                        if (c == '|' && !s.regionMatches(i, "||", 0, 2)) {
                            // single | is OR; skip ||
                            out += cur.toString().trim()
                            cur.clear()
                            i++
                            continue
                        }
                        if (matchWord(s, i, "or")) {
                            out += cur.toString().trim()
                            cur.clear()
                            i += 2
                            continue
                        }
                    } else {
                        if (c == '&' && !s.regionMatches(i, "&&", 0, 2)) {
                            out += cur.toString().trim()
                            cur.clear()
                            i++
                            continue
                        }
                        if (matchWord(s, i, "and")) {
                            out += cur.toString().trim()
                            cur.clear()
                            i += 3
                            continue
                        }
                    }
                }
            }
            cur.append(c)
            i++
        }
        if (cur.isNotEmpty()) out += cur.toString().trim()
        return out.filter { it.isNotEmpty() }.ifEmpty { listOf(s) }
    }

    private fun matchWord(s: String, i: Int, word: String): Boolean {
        if (!s.regionMatches(i, word, 0, word.length, ignoreCase = true)) return false
        val before = if (i == 0) ' ' else s[i - 1]
        val after = if (i + word.length >= s.length) ' ' else s[i + word.length]
        return !before.isLetterOrDigit() && !after.isLetterOrDigit()
    }

    private fun evalWeather(field: String, context: Context?): String {
        context?.let { WeatherProvider.ensureFresh(it) }
        return WeatherProvider.field(field)
    }

    private fun indexOfOp(s: String, op: String): Int {
        // No buscar dentro de comillas
        var inQuote = false
        var i = 0
        while (i <= s.length - op.length) {
            val c = s[i]
            if (c == '"') {
                inQuote = !inQuote
                i++
                continue
            }
            if (!inQuote && s.regionMatches(i, op, 0, op.length)) return i
            i++
        }
        return -1
    }

    private fun compareValues(left: String, right: String, op: String): Boolean {
        val ln = left.toDoubleOrNull()
        val rn = right.toDoubleOrNull()
        if (ln != null && rn != null) {
            return when (op) {
                "<" -> ln < rn
                ">" -> ln > rn
                "<=" -> ln <= rn
                ">=" -> ln >= rn
                "==", "=" -> ln == rn
                "!=" -> ln != rn
                else -> false
            }
        }
        // Comparación de texto
        return when (op) {
            "==", "=" -> left == right
            "!=" -> left != right
            else -> false
        }
    }

    private fun evalDateFormat(pattern: String): String {
        if (pattern.isEmpty()) return ""
        return manualDate(pattern, Calendar.getInstance())
    }

    private fun manualDate(pattern: String, cal: Calendar): String {
        fun pad2(n: Int) = n.toString().padStart(2, '0')
        val tokens = linkedMapOf(
            "EEEE" to (cal.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.LONG, Locale.getDefault()) ?: ""),
            "EEE" to (cal.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.SHORT, Locale.getDefault()) ?: ""),
            "MMMM" to (cal.getDisplayName(Calendar.MONTH, Calendar.LONG, Locale.getDefault()) ?: ""),
            "MMM" to (cal.getDisplayName(Calendar.MONTH, Calendar.SHORT, Locale.getDefault()) ?: ""),
            "yyyy" to cal.get(Calendar.YEAR).toString(),
            "yy" to pad2(cal.get(Calendar.YEAR) % 100),
            "hh" to pad2(cal.get(Calendar.HOUR_OF_DAY)),
            "HH" to pad2(cal.get(Calendar.HOUR_OF_DAY)),
            "h" to cal.get(Calendar.HOUR_OF_DAY).toString(),
            "H" to cal.get(Calendar.HOUR_OF_DAY).toString(),
            "mm" to pad2(cal.get(Calendar.MINUTE)),
            "m" to cal.get(Calendar.MINUTE).toString(),
            "ss" to pad2(cal.get(Calendar.SECOND)),
            "s" to cal.get(Calendar.SECOND).toString(),
            "dd" to pad2(cal.get(Calendar.DAY_OF_MONTH)),
            "d" to cal.get(Calendar.DAY_OF_MONTH).toString(),
            "MM" to pad2(cal.get(Calendar.MONTH) + 1),
            "aa" to if (cal.get(Calendar.AM_PM) == Calendar.AM) "AM" else "PM",
            "a" to if (cal.get(Calendar.AM_PM) == Calendar.AM) "am" else "pm",
            "D" to cal.get(Calendar.DAY_OF_YEAR).toString(),
            "w" to cal.get(Calendar.WEEK_OF_YEAR).toString()
        )
        var work = pattern
        val marks = mutableListOf<Pair<String, String>>()
        tokens.entries.forEachIndexed { idx, (from, value) ->
            if (work.contains(from)) {
                val mark = "\uE000$idx\uE001"
                work = work.replace(from, mark)
                marks += mark to value
            }
        }
        marks.forEach { (mark, value) -> work = work.replace(mark, value) }
        return work
    }

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
            "volt" -> intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0).toString()
            "tempc", "temp" -> {
                val t = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)
                (t / 10).toString()
            }
            "fast" -> "0"
            else -> ""
        }
    }

    private fun looksLikeMath(s: String): Boolean {
        if (!s.any { it.isDigit() }) return false
        return s.all { c ->
            c.isDigit() || c == '.' || c == '+' || c == '-' || c == '*' || c == '/' ||
                c == '(' || c == ')' || c == ' ' || c == '\t'
        }
    }

    private fun formatNumber(v: Double): String {
        return if (v % 1.0 == 0.0 && v in -1e15..1e15) {
            v.toLong().toString()
        } else {
            String.format(Locale.US, "%.6f", v).trimEnd('0').trimEnd('.')
        }
    }

    private fun evalMath(expr: String): Double {
        return MathParser(tokenizeMath(expr)).parse()
    }

    private class MathParser(private val tokens: List<String>) {
        private var pos = 0

        private fun peek(): String? = tokens.getOrNull(pos)
        private fun next(): String = tokens[pos++]

        fun parse(): Double {
            val v = parseExpr()
            if (pos != tokens.size) throw IllegalArgumentException("trailing")
            return v
        }

        private fun parseExpr(): Double {
            var v = parseTerm()
            while (true) {
                when (peek()) {
                    "+" -> {
                        next()
                        v += parseTerm()
                    }
                    "-" -> {
                        next()
                        v -= parseTerm()
                    }
                    else -> return v
                }
            }
        }

        private fun parseTerm(): Double {
            var v = parseFactor()
            while (true) {
                when (peek()) {
                    "*" -> {
                        next()
                        v *= parseFactor()
                    }
                    "/" -> {
                        next()
                        val d = parseFactor()
                        v = if (d == 0.0) Double.NaN else v / d
                    }
                    else -> return v
                }
            }
        }

        private fun parseFactor(): Double {
            val t = peek() ?: throw IllegalArgumentException("eof")
            return when {
                t == "+" -> {
                    next()
                    parseFactor()
                }
                t == "-" -> {
                    next()
                    -parseFactor()
                }
                t == "(" -> {
                    next()
                    val v = parseExpr()
                    if (peek() != ")") throw IllegalArgumentException(")")
                    next()
                    v
                }
                else -> {
                    next()
                    t.toDouble()
                }
            }
        }
    }

    private fun tokenizeMath(expr: String): List<String> {
        val list = mutableListOf<String>()
        var i = 0
        while (i < expr.length) {
            val c = expr[i]
            when {
                c.isWhitespace() -> i++
                c in "+-*/()" -> {
                    list += c.toString()
                    i++
                }
                c.isDigit() || c == '.' -> {
                    val start = i
                    i++
                    while (i < expr.length && (expr[i].isDigit() || expr[i] == '.')) i++
                    list += expr.substring(start, i)
                }
                else -> throw IllegalArgumentException("bad char $c")
            }
        }
        return list
    }

    private fun isIdentStart(c: Char) = c.isLetter() || c == '_'
    private fun isIdentPart(c: Char) = c.isLetterOrDigit() || c == '_'

    private fun findMatchingParen(s: String, openIdx: Int): Int {
        var depth = 0
        for (i in openIdx until s.length) {
            when (s[i]) {
                '(' -> depth++
                ')' -> {
                    depth--
                    if (depth == 0) return i
                }
            }
        }
        return -1
    }
}
