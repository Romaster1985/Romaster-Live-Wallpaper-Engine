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

package com.romaster.livewallengine.gallery

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.graphics.Typeface
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.textfield.TextInputEditText
import com.romaster.livewallengine.R
import com.romaster.livewallengine.font.IconFontStorage
import com.romaster.livewallengine.project.ProjectManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Galería de fuentes de íconos (carpeta Icons del repo de temas).
 * Preview: primeros glifos del JSON + typeface del TTF.
 */
class IconFontGalleryActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_ICON_FONT_NAME = "icon_font_name"
        const val EXTRA_WIDGET_LAYER_ID = "widget_layer_id"

        /** Claves de ícono climático (KLWP / Open-Meteo mapeado). */
        val WEATHER_ICON_KEYS = listOf(
            "CLEAR", "PCLOUDY", "MCLOUDY", "FOG", "WINDY",
            "RAIN", "SHOWER", "SLEET", "SNOW", "LSNOW", "HAIL",
            "TSTORM", "TSHOWER", "TORNADO", "UNKNOWN"
        )
    }

    private lateinit var recycler: RecyclerView
    private lateinit var progress: ProgressBar
    private lateinit var textEmpty: TextView
    private lateinit var textError: TextView
    private lateinit var editSearch: TextInputEditText
    private lateinit var adapter: GalleryIconFontAdapter

    private var allItems: List<GalleryIconFontItem> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_font_gallery)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbarGallery)
        toolbar.title = "Galería de íconos"
        toolbar.setNavigationOnClickListener { finish() }

        recycler = findViewById(R.id.recyclerGallery)
        progress = findViewById(R.id.progressGallery)
        textEmpty = findViewById(R.id.textGalleryEmpty)
        textError = findViewById(R.id.textGalleryError)
        editSearch = findViewById(R.id.editFontSearch)
        editSearch.hint = "Buscar fuente de íconos…"

        val cacheDir = File(cacheDir, "icon_font_gallery")
        cacheDir.mkdirs()

        adapter = GalleryIconFontAdapter(
            scope = lifecycleScope,
            cacheDir = cacheDir
        ) { item ->
            confirmInstall(item, cacheDir)
        }

        recycler.layoutManager = GridLayoutManager(this, 2)
        recycler.adapter = adapter

        editSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                applyFilter(s?.toString().orEmpty())
            }
        })

        loadGallery()
    }

    private fun loadGallery() {
        progress.visibility = View.VISIBLE
        textEmpty.visibility = View.GONE
        textError.visibility = View.GONE
        recycler.visibility = View.GONE
        editSearch.isEnabled = false

        lifecycleScope.launch {
            try {
                val list = withContext(Dispatchers.IO) {
                    GitHubGalleryRepository.listIconFonts().map {
                        GalleryIconFontItem(
                            name = it.name,
                            ttfFileName = it.ttfFileName,
                            ttfUrl = it.ttfUrl,
                            jsonFileName = it.jsonFileName,
                            jsonUrl = it.jsonUrl
                        )
                    }
                }
                progress.visibility = View.GONE
                allItems = list
                editSearch.isEnabled = true
                if (list.isEmpty()) {
                    textEmpty.visibility = View.VISIBLE
                    textEmpty.text = "No hay pares TTF+JSON en la carpeta Icons del repositorio."
                    recycler.visibility = View.GONE
                } else {
                    applyFilter(editSearch.text?.toString().orEmpty())
                }
            } catch (e: Exception) {
                progress.visibility = View.GONE
                textError.visibility = View.VISIBLE
                textError.text = "Error: ${e.message ?: e.javaClass.simpleName}"
            }
        }
    }

    private fun applyFilter(query: String) {
        val q = query.trim()
        val filtered = if (q.isEmpty()) {
            allItems
        } else {
            allItems.filter {
                it.name.contains(q, ignoreCase = true) ||
                    it.ttfFileName.contains(q, ignoreCase = true)
            }
        }
        if (filtered.isEmpty()) {
            recycler.visibility = View.GONE
            textEmpty.visibility = View.VISIBLE
            textEmpty.text = if (q.isEmpty()) {
                "No hay fuentes de íconos en Icons/."
            } else {
                "Ninguna coincide con \"$q\"."
            }
        } else {
            textEmpty.visibility = View.GONE
            recycler.visibility = View.VISIBLE
            adapter.submit(filtered)
        }
    }

    private fun confirmInstall(item: GalleryIconFontItem, cacheDir: File) {
        val density = resources.displayMetrics.density
        val loading = AlertDialog.Builder(this)
            .setTitle(item.name)
            .setMessage("Cargando glifos…")
            .setCancelable(true)
            .create()
        loading.show()

        lifecycleScope.launch {
            try {
                val ttfLocal = File(cacheDir, item.ttfFileName)
                val jsonLocal = File(cacheDir, item.jsonFileName)
                val (iconTypeface, glyphs) = withContext(Dispatchers.IO) {
                    if (!ttfLocal.exists() || ttfLocal.length() == 0L) {
                        GitHubGalleryRepository.downloadFile(item.ttfUrl, ttfLocal)
                    }
                    if (!jsonLocal.exists() || jsonLocal.length() == 0L) {
                        GitHubGalleryRepository.downloadFile(item.jsonUrl, jsonLocal)
                    }
                    val tf = try {
                        Typeface.createFromFile(ttfLocal)
                    } catch (_: Exception) {
                        Typeface.DEFAULT
                    }
                    val map = try {
                        IconFontStorage.parseIcoMoon(jsonLocal.readText())
                    } catch (_: Exception) {
                        emptyMap()
                    }
                    // Orden estable por nombre
                    tf to map.toList().sortedBy { it.first.lowercase() }
                }
                loading.dismiss()

                val scroll = ScrollView(this@IconFontGalleryActivity)
                val cols = 4
                val grid = GridLayout(this@IconFontGalleryActivity).apply {
                    columnCount = cols
                    setPadding(
                        (6 * density).toInt(),
                        (4 * density).toInt(),
                        (6 * density).toInt(),
                        (4 * density).toInt()
                    )
                }

                if (glyphs.isEmpty()) {
                    grid.addView(TextView(this@IconFontGalleryActivity).apply {
                        text = "No se encontraron glifos en el JSON."
                        setPadding((8 * density).toInt(), (16 * density).toInt(), 0, 0)
                    })
                } else {
                    // Ancho usable ≈ diálogo (~88% pantalla) menos paddings/márgenes
                    val margin = (2 * density).toInt()
                    val gridHPad = (12 * density).toInt()
                    val dialogUsable = (resources.displayMetrics.widthPixels * 0.88f).toInt()
                    val cellSize = ((dialogUsable - gridHPad) / cols) - margin * 2
                    for ((name, char) in glyphs) {
                        val cell = LinearLayout(this@IconFontGalleryActivity).apply {
                            orientation = LinearLayout.VERTICAL
                            gravity = android.view.Gravity.CENTER
                            setPadding(
                                (2 * density).toInt(),
                                (4 * density).toInt(),
                                (2 * density).toInt(),
                                (4 * density).toInt()
                            )
                            setBackgroundColor(0xFF2A2A2A.toInt())
                            layoutParams = GridLayout.LayoutParams().apply {
                                width = cellSize
                                height = cellSize // cuadrado
                                setMargins(margin, margin, margin, margin)
                            }
                        }
                        cell.addView(TextView(this@IconFontGalleryActivity).apply {
                            text = char
                            typeface = iconTypeface
                            textSize = 22f
                            gravity = android.view.Gravity.CENTER
                            setTextColor(0xFFFFFFFF.toInt())
                            layoutParams = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                0,
                                1f
                            )
                        })
                        cell.addView(TextView(this@IconFontGalleryActivity).apply {
                            text = name
                            textSize = 9f
                            gravity = android.view.Gravity.CENTER
                            setTextColor(0xFFCCCCCC.toInt())
                            maxLines = 1
                            ellipsize = android.text.TextUtils.TruncateAt.END
                            layoutParams = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                            )
                        })
                        val glyphName = name
                        cell.isClickable = true
                        cell.setOnClickListener {
                            showAssignWeatherIconDialog(glyphName)
                        }
                        grid.addView(cell)
                    }
                }

                scroll.addView(grid)
                // Altura máxima ~60% de pantalla para no tapar botones
                val maxH = (resources.displayMetrics.heightPixels * 0.55f).toInt()
                scroll.layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    maxH
                )

                val container = LinearLayout(this@IconFontGalleryActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(TextView(this@IconFontGalleryActivity).apply {
                        text = "${glyphs.size} íconos — toca un glifo para asignar a un estado climático, o Instalar"
                        textSize = 13f
                        setPadding(
                            (16 * density).toInt(),
                            (8 * density).toInt(),
                            (16 * density).toInt(),
                            (4 * density).toInt()
                        )
                    })
                    addView(scroll)
                }

                AlertDialog.Builder(this@IconFontGalleryActivity)
                    .setTitle(item.name)
                    .setView(container)
                    .setNegativeButton("Cancelar", null)
                    .setPositiveButton("Instalar") { _, _ ->
                        install(item, cacheDir)
                    }
                    .show()
            } catch (e: Exception) {
                loading.dismiss()
                Toast.makeText(
                    this@IconFontGalleryActivity,
                    "Error: ${e.message ?: e.javaClass.simpleName}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }


    private fun showAssignWeatherIconDialog(glyphName: String) {
        val layerId = intent.getStringExtra(EXTRA_WIDGET_LAYER_ID)
        if (layerId.isNullOrBlank()) {
            Toast.makeText(
                this,
                "Abrí la galería desde un widget para asignar nombres",
                Toast.LENGTH_LONG
            ).show()
            return
        }
        val layer = ProjectManager.getProject().widgetLayers.find { it.id == layerId }
        if (layer == null) {
            Toast.makeText(this, "Widget no encontrado", Toast.LENGTH_SHORT).show()
            return
        }
        val keys = WEATHER_ICON_KEYS.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Asignar nombre de ícono a:")
            .setItems(keys) { _, which ->
                val weatherKey = keys[which]
                val updated = layer.iconGlyphMap.toMutableMap()
                updated[weatherKey] = glyphName
                layer.iconGlyphMap = updated
                ProjectManager.saveProject(ProjectManager.getProject())
                Toast.makeText(
                    this,
                    "$weatherKey → $glyphName",
                    Toast.LENGTH_SHORT
                ).show()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun install(item: GalleryIconFontItem, cacheDir: File) {
        val wait = AlertDialog.Builder(this)
            .setMessage("Instalando íconos…")
            .setCancelable(false)
            .create()
        wait.show()

        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val ttfLocal = File(cacheDir, item.ttfFileName)
                    val jsonLocal = File(cacheDir, item.jsonFileName)
                    if (!ttfLocal.exists() || ttfLocal.length() == 0L) {
                        GitHubGalleryRepository.downloadFile(item.ttfUrl, ttfLocal)
                    }
                    if (!jsonLocal.exists() || jsonLocal.length() == 0L) {
                        GitHubGalleryRepository.downloadFile(item.jsonUrl, jsonLocal)
                    }
                    // Copiar al almacenamiento permanente icons/
                    IconFontStorage.saveFromBytes(
                        this@IconFontGalleryActivity,
                        item.ttfFileName,
                        ttfLocal.readBytes()
                    )
                    IconFontStorage.saveFromBytes(
                        this@IconFontGalleryActivity,
                        item.jsonFileName,
                        jsonLocal.readBytes()
                    )
                }
                wait.dismiss()
                // Asignar fuente al widget desde el que se abrió la galería
                val layerId = intent.getStringExtra(EXTRA_WIDGET_LAYER_ID)
                if (!layerId.isNullOrBlank()) {
                    ProjectManager.getProject().widgetLayers
                        .find { it.id == layerId }
                        ?.let { w ->
                            w.iconFontName = item.name
                            w.useIconFont = true
                        }
                }
                Toast.makeText(
                    this@IconFontGalleryActivity,
                    "Íconos instalados: ${item.name}",
                    Toast.LENGTH_SHORT
                ).show()
                setResult(
                    Activity.RESULT_OK,
                    Intent().putExtra(EXTRA_ICON_FONT_NAME, item.name)
                )
                finish()
            } catch (e: Exception) {
                wait.dismiss()
                Toast.makeText(
                    this@IconFontGalleryActivity,
                    "Error: ${e.message ?: e.javaClass.simpleName}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}
