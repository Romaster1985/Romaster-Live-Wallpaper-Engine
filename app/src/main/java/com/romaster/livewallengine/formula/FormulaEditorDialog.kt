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
        fun sOr(id: Int, fallback: String) = try {
            activity.getString(id)
        } catch (_: Exception) {
            fallback
        }

        return listOf(
            Category(
                "📅",
                sOr(R.string.preset_section_datetime, "Fecha / Hora"),
                sOr(R.string.formula_cat_desc_datetime, "df() — hour, day, month, year…"),
                listOf(
                    Preset(sOr(R.string.preset_time_hhmm, "Time HH:MM"), "\$df(hh:mm)\$", "12:30"),
                    Preset(sOr(R.string.preset_time_hhmmss, "Time HH:MM:SS"), "\$df(hh:mm:ss)\$", "12:30:45"),
                    Preset(sOr(R.string.preset_weekday, "Weekday"), "\$df(EEEE)\$"),
                    Preset(sOr(R.string.preset_day_month, "Day of month"), "\$df(dd)\$"),
                    Preset(sOr(R.string.preset_month_short, "Short month"), "\$df(MMM)\$"),
                    Preset(sOr(R.string.preset_year, "Year"), "\$df(yyyy)\$"),
                    Preset(sOr(R.string.preset_year_plus4, "Year + 4"), "\$df(yyyy)+4\$", "2030"),
                    Preset(sOr(R.string.preset_date_full, "Full date (1 line)"), "\$df(EEEE), \$df(dd)\$ \$df(MMM)\$ \$df(yyyy)\$")
                )
            ),
            Category(
                "🔋",
                sOr(R.string.preset_section_battery, "Battery"),
                sOr(R.string.formula_cat_desc_battery, "bi() — level, charging, temp…"),
                listOf(
                    Preset(sOr(R.string.preset_bat_level, "Level %"), "\$bi(level)\$%", "90%"),
                    Preset(sOr(R.string.preset_bat_level_num, "Level (number)"), "\$bi(level)\$"),
                    Preset(sOr(R.string.preset_bat_charging, "Charging (0/1)"), "\$bi(charging)\$", "0/1"),
                    Preset(sOr(R.string.preset_bat_temp, "Temperature °C"), "\$bi(temp)\$"),
                    Preset(sOr(R.string.preset_bat_volt, "Voltage mV"), "\$bi(volt)\$")
                )
            ),
            Category(
                "🌤️",
                sOr(R.string.preset_section_weather, "Weather"),
                sOr(R.string.formula_cat_desc_weather, "wi() — temp, condition, icon…"),
                listOf(
                    Preset(sOr(R.string.preset_temp_c, "Temperature °C"), "\$wi(temp)\$°", "21°"),
                    Preset(sOr(R.string.preset_temp_f, "Temperature °F"), "\$wi(tempf)\$°F"),
                    Preset(sOr(R.string.preset_cond, "Condition"), "\$wi(cond)\$"),
                    Preset(sOr(R.string.preset_humidity, "Humidity %"), "\$wi(humidity)\$%"),
                    Preset(sOr(R.string.preset_wind, "Wind km/h"), "\$wi(wind)\$"),
                    Preset(sOr(R.string.preset_icon_key, "Icon key"), "\$wi(icon)\$", "RAIN / CLEAR…"),
                    Preset(sOr(R.string.preset_wmo, "WMO code"), "\$wi(code)\$")
                )
            ),
            Category(
                "ℹ️",
                sOr(R.string.formula_cat_system, "System"),
                sOr(R.string.formula_cat_desc_system, "si() — lock, model, orientation…"),
                listOf(
                    Preset(sOr(R.string.preset_si_locked, "Lock state (locked)"), "\$si(locked)\$", sOr(R.string.preset_si_locked_hint, "1=locked")),
                    Preset(sOr(R.string.preset_si_locked_var, "Variable locked"), "\$locked\$", "0/1"),
                    Preset(sOr(R.string.preset_si_land, "Landscape"), "\$si(land)\$", "0/1"),
                    Preset(sOr(R.string.preset_si_darkmode, "Dark mode"), "\$si(darkmode)\$", "0/1"),
                    Preset(sOr(R.string.preset_si_powersave, "Battery saver"), "\$si(powersave)\$", "0/1"),
                    Preset(sOr(R.string.preset_si_model, "Model"), "\$si(model)\$"),
                    Preset(sOr(R.string.preset_si_man, "Manufacturer"), "\$si(man)\$"),
                    Preset(sOr(R.string.preset_si_aver, "Android version"), "\$si(aver)\$"),
                    Preset(sOr(R.string.preset_si_volr, "Ringer volume %"), "\$si(volr)\$"),
                    Preset(sOr(R.string.preset_si_ringer, "Ringer mode"), "\$si(ringer)\$", "NORMAL/SILENT/VIBRATE"),
                    Preset(sOr(R.string.preset_si_alarmon, "Alarm active"), "\$si(alarmon)\$", "0/1"),
                    Preset(sOr(R.string.preset_si_alarmt, "Alarm time"), "\$si(alarmt)\$", "HH:MM"),
                    Preset(sOr(R.string.preset_si_swidth, "Screen width"), "\$si(swidth)\$"),
                    Preset(sOr(R.string.preset_si_sheight, "Screen height"), "\$si(sheight)\$")
                )
            ),
            Category(
                "Aa",
                sOr(R.string.formula_cat_text, "Text"),
                sOr(R.string.formula_cat_desc_text, "tc() — upper, lower, capitalize, cut…"),
                listOf(
                    Preset(sOr(R.string.preset_tc_up, "Uppercase"), "\$tc(up, df(EEEE))\$"),
                    Preset(sOr(R.string.preset_tc_low, "Lowercase"), "\$tc(low, df(EEEE))\$"),
                    Preset(sOr(R.string.preset_tc_cap, "Capitalize"), "\$tc(cap, df(EEEE))\$"),
                    Preset(sOr(R.string.preset_tc_len, "Length"), "\$tc(len, df(EEEE))\$"),
                    Preset(sOr(R.string.preset_tc_cut3, "Cut (first 3)"), "\$tc(cut, df(EEEE), 3)\$"),
                    Preset(sOr(R.string.preset_tc_cut_last2, "Cut (last 2)"), "\$tc(cut, df(EEEE), -2)\$"),
                    Preset(sOr(R.string.preset_tc_ell, "Ellipsis"), "\$tc(ell, df(EEEE), 6)\$"),
                    Preset(sOr(R.string.preset_tc_count, "Count"), "\$tc(count, df(EEEE), a)\$")
                )
            ),
            Category(
                "🔢",
                sOr(R.string.formula_cat_math, "Math"),
                sOr(R.string.formula_cat_desc_math, "mu() — floor, ceil, min, max, sqrt…"),
                listOf(
                    Preset("floor", "\$mu(floor, bi(level)/3)\$"),
                    Preset("ceil", "\$mu(ceil, 3.2)\$"),
                    Preset("round", "\$mu(round, 3.6)\$"),
                    Preset("abs", "\$mu(abs, -5)\$"),
                    Preset("min", "\$mu(min, 3, 7)\$"),
                    Preset("max", "\$mu(max, 3, 7)\$"),
                    Preset("sqrt", "\$mu(sqrt, 16)\$"),
                    Preset("pow", "\$mu(pow, 2, 8)\$")
                )
            ),
            Category(
                "⏱",
                sOr(R.string.formula_cat_timespan, "Time span"),
                sOr(R.string.formula_cat_desc_timespan, "tf() — seconds → Xh Ym Zs"),
                listOf(
                    Preset(sOr(R.string.preset_tf_1h, "1 hour"), "\$tf(3600)\$", "1h 0m 0s"),
                    Preset(sOr(R.string.preset_tf_90m, "90 minutes"), "\$tf(5400)\$"),
                    Preset(sOr(R.string.preset_tf_ss60, "Current seconds × 60"), "\$tf(df(ss)*60)\$")
                )
            ),
            Category(
                "🎵",
                sOr(R.string.formula_cat_music, "Music"),
                sOr(R.string.formula_cat_desc_music, "mi() — artist, title, state, position…"),
                listOf(
                    Preset(sOr(R.string.preset_mi_artist, "Artist"), "\$mi(artist)\$"),
                    Preset(sOr(R.string.preset_mi_title, "Title"), "\$mi(title)\$"),
                    Preset(sOr(R.string.preset_mi_album, "Album"), "\$mi(album)\$"),
                    Preset(sOr(R.string.preset_mi_state, "State"), "\$mi(state)\$", "PLAYING/PAUSED/…"),
                    Preset(sOr(R.string.preset_mi_len, "Duration (s)"), "\$mi(len)\$"),
                    Preset(sOr(R.string.preset_mi_pos, "Position (s)"), "\$mi(pos)\$"),
                    Preset(sOr(R.string.preset_mi_len_mmss, "Duration mm:ss"), "\$tf(mi(len), mm:ss)\$"),
                    Preset(sOr(R.string.preset_mi_pos_mmss, "Position mm:ss"), "\$tf(mi(pos), mm:ss)\$"),
                    Preset(sOr(R.string.preset_mi_percent, "Progress %"), "\$mi(percent)\$"),
                    Preset(sOr(R.string.preset_mi_vol, "Music volume"), "\$mi(vol)\$"),
                    Preset(sOr(R.string.preset_mi_package, "Package"), "\$mi(package)\$"),
                    Preset(sOr(R.string.preset_mi_cover, "Cover art (URI)"), "\$mi(cover)\$"),
                    Preset(sOr(R.string.preset_mi_playing, "Playing"), "\$mi(playing)\$", "0/1")
                )
            ),
            Category(
                "📶",
                sOr(R.string.formula_cat_network, "Network"),
                sOr(R.string.formula_cat_desc_network, "nc() — wifi, type, connected…"),
                listOf(
                    Preset(sOr(R.string.preset_nc_on, "Connected"), "\$nc(on)\$", "0/1"),
                    Preset(sOr(R.string.preset_nc_type, "Network type"), "\$nc(type)\$", "WIFI/MOBILE/NONE"),
                    Preset(sOr(R.string.preset_nc_wifi, "Wi‑Fi name"), "\$nc(wifi)\$"),
                    Preset(sOr(R.string.preset_nc_csig, "Signal (approx.)"), "\$nc(csig)\$", "0–4")
                )
            ),
            Category(
                "💾",
                sOr(R.string.formula_cat_resources, "Resources"),
                sOr(R.string.formula_cat_desc_resources, "rm() — memory and storage"),
                listOf(
                    Preset(sOr(R.string.preset_rm_memf, "Free RAM (MB)"), "\$rm(memf)\$"),
                    Preset(sOr(R.string.preset_rm_memt, "Total RAM (MB)"), "\$rm(memt)\$"),
                    Preset(sOr(R.string.preset_rm_memp, "Used RAM %"), "\$rm(memp)\$"),
                    Preset(sOr(R.string.preset_rm_storf, "Free storage (MB)"), "\$rm(storf)\$"),
                    Preset(sOr(R.string.preset_rm_stort, "Total storage (MB)"), "\$rm(stort)\$")
                )
            ),
            Category(
                "📆",
                sOr(R.string.formula_cat_forecast, "Forecast"),
                sOr(R.string.formula_cat_desc_forecast, "wf() — min/max/cond per day (0=today)"),
                listOf(
                    Preset(sOr(R.string.preset_wf_max0, "Max today °C"), "\$wf(max, 0)\$"),
                    Preset(sOr(R.string.preset_wf_min0, "Min today °C"), "\$wf(min, 0)\$"),
                    Preset(sOr(R.string.preset_wf_max1, "Max tomorrow °C"), "\$wf(max, 1)\$"),
                    Preset(sOr(R.string.preset_wf_min1, "Min tomorrow °C"), "\$wf(min, 1)\$"),
                    Preset(sOr(R.string.preset_wf_cond0, "Condition today"), "\$wf(cond, 0)\$"),
                    Preset(sOr(R.string.preset_wf_icon1, "Icon tomorrow"), "\$wf(icon, 1)\$"),
                    Preset(sOr(R.string.preset_wf_temp0, "Avg temp today"), "\$wf(temp, 0)\$")
                )
            ),
            Category(
                "⎇",
                sOr(R.string.preset_section_conditionals, "Conditionals"),
                sOr(R.string.formula_cat_desc_conditionals, "if() — If / Then / Else"),
                listOf(
                    Preset(
                        sOr(R.string.preset_bat_low, "Low battery"),
                        "\$if(bi(level)<20, LOW, OK)\$"
                    ),
                    Preset(
                        sOr(R.string.preset_charging_txt, "Charging"),
                        "\$if(bi(charging)=1, Charging, Battery)\$"
                    ),
                    Preset(
                        sOr(R.string.preset_if_locked, "Locked"),
                        "\$if(si(locked)=1, LOCKED, UNLOCKED)\$"
                    ),
                    Preset(
                        sOr(R.string.preset_and, "AND (battery and time)"),
                        "\$if(bi(level)<20 & bi(charging)=0, Plug in!, OK)\$"
                    ),
                    Preset(
                        sOr(R.string.preset_or, "OR (charging or low)"),
                        "\$if(si(locked)=1 | si(land)=1, Special, Normal)\$"
                    )
                )
            )
        )
    }

}
