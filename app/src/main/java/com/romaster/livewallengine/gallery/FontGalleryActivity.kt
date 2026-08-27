package com.romaster.livewallengine.gallery

import android.app.Activity
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.ProgressBar
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
import com.romaster.livewallengine.font.FontStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class FontGalleryActivity : AppCompatActivity() {

    private lateinit var recycler: RecyclerView
    private lateinit var progress: ProgressBar
    private lateinit var textEmpty: TextView
    private lateinit var textError: TextView
    private lateinit var editSearch: TextInputEditText
    private lateinit var adapter: GalleryFontAdapter

    /** Lista completa del repo (sin filtrar). */
    private var allFonts: List<GalleryFont> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_font_gallery)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbarGallery)
        toolbar.setNavigationOnClickListener { finish() }

        recycler = findViewById(R.id.recyclerGallery)
        progress = findViewById(R.id.progressGallery)
        textEmpty = findViewById(R.id.textGalleryEmpty)
        textError = findViewById(R.id.textGalleryError)
        editSearch = findViewById(R.id.editFontSearch)

        val fontCacheDir = File(cacheDir, "font_gallery")
        fontCacheDir.mkdirs()

        adapter = GalleryFontAdapter(
            scope = lifecycleScope,
            cacheDir = fontCacheDir
        ) { font ->
            confirmInstall(font, fontCacheDir)
        }

        recycler.layoutManager = GridLayoutManager(this, 2)
        recycler.adapter = adapter

        editSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(
                s: CharSequence?,
                start: Int,
                count: Int,
                after: Int
            ) = Unit

            override fun onTextChanged(
                s: CharSequence?,
                start: Int,
                before: Int,
                count: Int
            ) = Unit

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
                val fonts = withContext(Dispatchers.IO) {
                    GitHubGalleryRepository.listFonts()
                }
                progress.visibility = View.GONE
                allFonts = fonts
                editSearch.isEnabled = true

                if (fonts.isEmpty()) {
                    textEmpty.visibility = View.VISIBLE
                    textEmpty.text =
                        "No hay fuentes en la carpeta Fonts del repositorio."
                } else {
                    applyFilter(editSearch.text?.toString().orEmpty())
                }
            } catch (e: Exception) {
                progress.visibility = View.GONE
                textError.visibility = View.VISIBLE
                textError.text =
                    "No se pudo cargar la galería de fuentes.\n\n" +
                        "${e.message ?: e.javaClass.simpleName}\n\n" +
                        "Comprobá la conexión e intentá de nuevo."
            }
        }
    }

    /**
     * Filtra por coincidencia parcial (sin distinguir mayúsculas)
     * en el nombre de visualización o en el nombre de archivo.
     * Texto vacío → lista completa.
     */
    private fun applyFilter(query: String) {
        if (allFonts.isEmpty()) return

        val q = query.trim()
        val filtered =
            if (q.isEmpty()) {
                allFonts
            } else {
                allFonts.filter { font ->
                    font.name.contains(q, ignoreCase = true) ||
                        font.fileName.contains(q, ignoreCase = true)
                }
            }

        if (filtered.isEmpty()) {
            recycler.visibility = View.GONE
            textEmpty.visibility = View.VISIBLE
            textEmpty.text =
                if (q.isEmpty()) {
                    "No hay fuentes en la carpeta Fonts del repositorio."
                } else {
                    "Ninguna fuente coincide con \"$q\"."
                }
        } else {
            textEmpty.visibility = View.GONE
            recycler.visibility = View.VISIBLE
            adapter.submit(filtered)
        }
    }

    private fun confirmInstall(font: GalleryFont, cacheDir: File) {
        AlertDialog.Builder(this)
            .setTitle(font.name)
            .setMessage("¿Instalar esta fuente en la app?")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Instalar") { _, _ ->
                installFont(font, cacheDir)
            }
            .show()
    }

    private fun installFont(font: GalleryFont, cacheDir: File) {
        val wait = AlertDialog.Builder(this)
            .setMessage("Instalando fuente…")
            .setCancelable(false)
            .create()
        wait.show()

        lifecycleScope.launch {
            try {
                val local = File(cacheDir, font.fileName)
                withContext(Dispatchers.IO) {
                    if (!local.exists() || local.length() == 0L) {
                        GitHubGalleryRepository.downloadFile(
                            font.downloadUrl,
                            local
                        )
                    }
                    FontStorage.importFontFile(
                        this@FontGalleryActivity,
                        local
                    )
                    Unit
                }
                wait.dismiss()
                Toast.makeText(
                    this@FontGalleryActivity,
                    "Fuente instalada: ${font.fileName}",
                    Toast.LENGTH_SHORT
                ).show()
                setResult(Activity.RESULT_OK)
                finish()
            } catch (e: Exception) {
                wait.dismiss()
                Toast.makeText(
                    this@FontGalleryActivity,
                    "Error: ${e.message ?: e.javaClass.simpleName}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}
