package com.romaster.livewallengine.formula

import android.graphics.Typeface
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.romaster.livewallengine.R

/**
 * Diálogo centralizado de fórmulas estilo KLWP:
 * - Vista previa en vivo
 * - Editor multilínea
 * - Categorías en grilla 2 columnas con icono + título + descripción
 * - Al tocar categoría → lista de ejemplos (insertan en el cursor)
 *
 * Usado por widgets, progreso y animaciones.
 */
object FormulaEditorDialog {

    data class Preset(
        val title: String,
        val formula: String,
        val desc: String = ""
    )

    data class Category(
        val icon: String,
        val title: String,
        val description: String,
        val presets: List<Preset>
    )

    fun show(
        activity: AppCompatActivity,
        title: String,
        initial: String,
        onResult: (String) -> Unit
    ) {
        val density = activity.resources.displayMetrics.density
        var savedSelStart = initial.length
        var savedSelEnd = initial.length

        val input = EditText(activity).apply {
            setText(initial)
            setSelection(text.length)
            minLines = 3
            maxLines = 6
            gravity = Gravity.TOP or Gravity.START
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            setHorizontallyScrolling(false)
            textSize = 15f
            setOnClickListener {
                savedSelStart = selectionStart.coerceAtLeast(0)
                savedSelEnd = selectionEnd.coerceAtLeast(0)
            }
            setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    post {
                        savedSelStart = selectionStart.coerceAtLeast(0)
                        savedSelEnd = selectionEnd.coerceAtLeast(0)
                    }
                }
            }
        }

        fun captureCursor() {
            try {
                savedSelStart = input.selectionStart.coerceAtLeast(0)
                savedSelEnd = input.selectionEnd.coerceAtLeast(0)
            } catch (_: Exception) {
            }
        }

        val livePreview = TextView(activity).apply {
            text = try {
                FormulaEngine.evaluate(initial, activity)
            } catch (_: Exception) {
                initial
            }
            setPadding(0, (6 * density).toInt(), 0, (4 * density).toInt())
            textSize = 16f
            setTypeface(typeface, Typeface.BOLD)
        }

        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val fx = s?.toString().orEmpty()
                livePreview.text = try {
                    FormulaEngine.evaluate(fx, activity)
                } catch (_: Exception) {
                    fx
                }
            }
        })

        fun insertAtCursor(formula: String) {
            captureCursor()
            val start = savedSelStart.coerceIn(0, input.text.length)
            val end = savedSelEnd.coerceIn(0, input.text.length)
            val editable = input.text
            editable.replace(start, end, formula)
            val newPos = (start + formula.length).coerceIn(0, input.text.length)
            input.setSelection(newPos)
            savedSelStart = newPos
            savedSelEnd = newPos
            input.requestFocus()
        }

        val categories = buildCategories(activity)

        // Contenedor de categorías / detalle
        val contentHost = FrameLayout(activity)

        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                (16 * density).toInt(),
                (8 * density).toInt(),
                (16 * density).toInt(),
                0
            )
            addView(TextView(activity).apply {
                text = activity.getString(R.string.formula_preview)
                textSize = 12f
                alpha = 0.7f
            })
            addView(livePreview)
            addView(TextView(activity).apply {
                text = activity.getString(R.string.formula_editor)
                textSize = 12f
                alpha = 0.7f
                setPadding(0, (8 * density).toInt(), 0, (4 * density).toInt())
            })
            addView(input, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ))
            addView(TextView(activity).apply {
                text = activity.getString(R.string.formula_examples)
                textSize = 12f
                alpha = 0.7f
                setPadding(0, (10 * density).toInt(), 0, (6 * density).toInt())
            })
            addView(contentHost, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (320 * density).toInt()
            ))
        }

        fun showCategoryGrid() {
            contentHost.removeAllViews()
            val scroll = ScrollView(activity)
            val grid = GridLayout(activity).apply {
                columnCount = 2
                useDefaultMargins = false
            }
            val gap = (6 * density).toInt()
            val colW = (
                activity.resources.displayMetrics.widthPixels -
                    (48 * density).toInt() - gap
                ) / 2

            categories.forEachIndexed { index, cat ->
                val card = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(
                        (10 * density).toInt(),
                        (10 * density).toInt(),
                        (10 * density).toInt(),
                        (10 * density).toInt()
                    )
                    setBackgroundResource(android.R.drawable.dialog_holo_dark_frame)
                    // Fondo más limpio Material-ish
                    background = android.graphics.drawable.GradientDrawable().apply {
                        setColor(0xFF2C2C2C.toInt())
                        cornerRadius = 10 * density
                    }
                    isClickable = true
                    isFocusable = true
                    setOnClickListener { showCategoryDetail(cat, ::showCategoryGrid, ::insertAtCursor, contentHost, activity, density) }
                }
                card.addView(TextView(activity).apply {
                    text = "${cat.icon}  ${cat.title}"
                    textSize = 14f
                    setTypeface(typeface, Typeface.BOLD)
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                })
                card.addView(TextView(activity).apply {
                    text = cat.description
                    textSize = 11f
                    alpha = 0.75f
                    maxLines = 3
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    setPadding(0, (4 * density).toInt(), 0, 0)
                })

                val lp = GridLayout.LayoutParams().apply {
                    width = colW
                    height = GridLayout.LayoutParams.WRAP_CONTENT
                    columnSpec = GridLayout.spec(index % 2)
                    rowSpec = GridLayout.spec(index / 2)
                    setMargins(gap / 2, gap / 2, gap / 2, gap / 2)
                }
                grid.addView(card, lp)
            }
            scroll.addView(grid)
            contentHost.addView(scroll)
        }

        showCategoryGrid()

        MaterialAlertDialogBuilder(activity)
            .setTitle(title)
            .setView(root)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                onResult(input.text.toString())
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showCategoryDetail(
        cat: Category,
        onBack: () -> Unit,
        insertAtCursor: (String) -> Unit,
        host: FrameLayout,
        activity: AppCompatActivity,
        density: Float
    ) {
        host.removeAllViews()
        val col = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
        }
        col.addView(
            MaterialButton(
                activity, null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle
            ).apply {
                text = activity.getString(R.string.formula_back_categories)
                isAllCaps = false
                setOnClickListener { onBack() }
            }
        )
        val scroll = ScrollView(activity)
        val list = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, (6 * density).toInt(), 0, 0)
        }
        cat.presets.forEach { item ->
            val row = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(
                    (10 * density).toInt(),
                    (10 * density).toInt(),
                    (10 * density).toInt(),
                    (10 * density).toInt()
                )
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(0xFF2C2C2C.toInt())
                    cornerRadius = 8 * density
                }
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = (6 * density).toInt()
                }
                layoutParams = lp
                isClickable = true
                isFocusable = true
                setOnClickListener { insertAtCursor(item.formula) }
            }
            row.addView(TextView(activity).apply {
                text = item.title
                textSize = 14f
                setTypeface(typeface, Typeface.BOLD)
            })
            row.addView(TextView(activity).apply {
                text = buildString {
                    append(item.formula)
                    if (item.desc.isNotBlank()) append("  ·  ").append(item.desc)
                }
                textSize = 12f
                alpha = 0.8f
                setPadding(0, (2 * density).toInt(), 0, 0)
            })
            list.addView(row)
        }
        scroll.addView(list)
        col.addView(
            scroll,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        )
        host.addView(col)
    }

    private fun buildCategories(activity: AppCompatActivity): List<Category> {
        // Títulos con fallbacks si falta string
        fun sOr(id: Int, fallback: String) = try {
            activity.getString(id)
        } catch (_: Exception) {
            fallback
        }

        return listOf(
            Category(
                "📅",
                sOr(R.string.preset_section_datetime, "Fecha / Hora"),
                "df() — hora, día, mes, año…",
                listOf(
                    Preset(sOr(R.string.preset_time_hhmm, "Hora HH:MM"), "\$df(hh:mm)\$", "12:30"),
                    Preset(sOr(R.string.preset_time_hhmmss, "Hora con segundos"), "\$df(hh:mm:ss)\$", "12:30:45"),
                    Preset(sOr(R.string.preset_weekday, "Día de la semana"), "\$df(EEEE)\$"),
                    Preset(sOr(R.string.preset_day_month, "Día del mes"), "\$df(dd)\$"),
                    Preset(sOr(R.string.preset_month_short, "Mes corto"), "\$df(MMM)\$"),
                    Preset(sOr(R.string.preset_year, "Año"), "\$df(yyyy)\$"),
                    Preset("Año + 4", "\$df(yyyy)+4\$", "2030"),
                    Preset(sOr(R.string.preset_date_full, "Fecha completa"), "\$df(EEEE), \$df(dd)\$ \$df(MMM)\$ \$df(yyyy)\$")
                )
            ),
            Category(
                "🔋",
                sOr(R.string.preset_section_battery, "Batería"),
                "bi() — nivel, carga, temp…",
                listOf(
                    Preset(sOr(R.string.preset_bat_level, "Nivel %"), "\$bi(level)\$%", "90%"),
                    Preset("Nivel (número)", "\$bi(level)\$"),
                    Preset(sOr(R.string.preset_bat_charging, "Cargando"), "\$bi(charging)\$", "0/1"),
                    Preset(sOr(R.string.preset_bat_temp, "Temperatura"), "\$bi(temp)\$"),
                    Preset(sOr(R.string.preset_bat_volt, "Voltaje"), "\$bi(volt)\$")
                )
            ),
            Category(
                "🌤️",
                sOr(R.string.preset_section_weather, "Clima"),
                "wi() — temp, condición, icono…",
                listOf(
                    Preset(sOr(R.string.preset_temp_c, "Temp °C"), "\$wi(temp)\$°", "21°"),
                    Preset(sOr(R.string.preset_temp_f, "Temp °F"), "\$wi(tempf)\$°F"),
                    Preset(sOr(R.string.preset_cond, "Condición"), "\$wi(cond)\$"),
                    Preset(sOr(R.string.preset_humidity, "Humedad"), "\$wi(humidity)\$%"),
                    Preset(sOr(R.string.preset_wind, "Viento"), "\$wi(wind)\$"),
                    Preset(sOr(R.string.preset_icon_key, "Icono"), "\$wi(icon)\$", "RAIN / CLEAR…"),
                    Preset(sOr(R.string.preset_wmo, "Código WMO"), "\$wi(code)\$")
                )
            ),
            Category(
                "ℹ️",
                sOr(R.string.formula_cat_system, "Sistema"),
                "si() — bloqueo, modelo, orientación…",
                listOf(
                    Preset("Bloqueo (locked)", "\$si(locked)\$", "1=bloqueado"),
                    Preset("Variable locked", "\$locked\$", "0/1"),
                    Preset("Landscape", "\$si(land)\$", "0/1"),
                    Preset("Modo oscuro", "\$si(darkmode)\$", "0/1"),
                    Preset("Ahorro batería", "\$si(powersave)\$", "0/1"),
                    Preset("Modelo", "\$si(model)\$"),
                    Preset("Fabricante", "\$si(man)\$"),
                    Preset("Android", "\$si(aver)\$"),
                    Preset("Volumen timbre %", "\$si(volr)\$"),
                    Preset("Modo timbre", "\$si(ringer)\$", "NORMAL/SILENT/VIBRATE"),
                    Preset("Alarma activa", "\$si(alarmon)\$", "0/1"),
                    Preset("Hora alarma", "\$si(alarmt)\$", "HH:MM"),
                    Preset("Ancho pantalla", "\$si(swidth)\$"),
                    Preset("Alto pantalla", "\$si(sheight)\$")
                )
            ),
            Category(
                "Aa",
                sOr(R.string.formula_cat_text, "Texto"),
                "tc() — mayúsculas, cortar, capitalizar…",
                listOf(
                    Preset("Mayúsculas", "\$tc(upper, df(EEEE))\$"),
                    Preset("Minúsculas", "\$tc(lower, df(EEEE))\$"),
                    Preset("Capitalizar", "\$tc(cap, df(EEEE))\$"),
                    Preset("Longitud", "\$tc(len, df(EEEE))\$"),
                    Preset("Recortar", "\$tc(cut, df(EEEE), 0, 3)\$"),
                    Preset("Invertir", "\$tc(rev, df(hh:mm))\$"),
                    Preset("Trim", "\$tc(trim, \"  hola  \")\$")
                )
            ),
            Category(
                "🔢",
                sOr(R.string.formula_cat_math, "Matemática"),
                "mu() — floor, ceil, min, max, sqrt…",
                listOf(
                    Preset("floor", "\$mu(floor, bi(level)/3)\$"),
                    Preset("ceil", "\$mu(ceil, 3.2)\$"),
                    Preset("round", "\$mu(round, 3.6)\$"),
                    Preset("abs", "\$mu(abs, -5)\$"),
                    Preset("min", "\$mu(min, 3, 7)\$"),
                    Preset("max", "\$mu(max, 3, 7)\$"),
                    Preset("sqrt", "\$mu(sqrt, 16)\$"),
                    Preset("pow", "\$mu(pow, 2, 10)\$")
                )
            ),
            Category(
                "⏱️",
                sOr(R.string.formula_cat_timespan, "Intervalo"),
                "tf() — segundos → Xh Ym Zs",
                listOf(
                    Preset("1 hora", "\$tf(3600)\$", "1h 0m 0s"),
                    Preset("90 minutos", "\$tf(5400)\$"),
                    Preset("Segundos actuales * 60", "\$tf(df(ss)*60)\$")
                )
            ),
            Category(
                "⎇",
                sOr(R.string.preset_section_conditionals, "Condicionales"),
                "if() — Si / Entonces / Si no",
                listOf(
                    Preset(
                        sOr(R.string.preset_bat_low, "Batería baja"),
                        "\$if(bi(level)<20, LOW, OK)\$"
                    ),
                    Preset(
                        sOr(R.string.preset_charging_txt, "Texto cargando"),
                        "\$if(bi(charging)=1, Charging, Battery)\$"
                    ),
                    Preset(
                        "Bloqueado",
                        "\$if(si(locked)=1, LOCKED, UNLOCKED)\$"
                    ),
                    Preset(
                        sOr(R.string.preset_and, "AND"),
                        "\$if(bi(level)<20 & bi(charging)=0, Plug in!, OK)\$"
                    ),
                    Preset(
                        sOr(R.string.preset_or, "OR"),
                        "\$if(si(locked)=1 | si(land)=1, Special, Normal)\$"
                    )
                )
            )
        )
    }
}
