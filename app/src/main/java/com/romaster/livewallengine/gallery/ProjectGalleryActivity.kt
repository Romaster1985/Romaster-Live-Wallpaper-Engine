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
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.romaster.livewallengine.R
import com.romaster.livewallengine.storage.AppDirectories
import com.romaster.livewallengine.storage.ProjectImporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class ProjectGalleryActivity : AppCompatActivity() {

    private lateinit var recycler: RecyclerView
    private lateinit var progress: ProgressBar
    private lateinit var textEmpty: TextView
    private lateinit var textError: TextView
    private lateinit var adapter: GalleryProjectAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gallery)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbarGallery)
        toolbar.setNavigationOnClickListener { finish() }

        recycler = findViewById(R.id.recyclerGallery)
        progress = findViewById(R.id.progressGallery)
        textEmpty = findViewById(R.id.textGalleryEmpty)
        textError = findViewById(R.id.textGalleryError)

        adapter = GalleryProjectAdapter { project ->
            showProjectDialog(project)
        }

        recycler.layoutManager = GridLayoutManager(this, 2)
        recycler.adapter = adapter

        loadGallery()
    }

    private fun loadGallery() {
        progress.visibility = View.VISIBLE
        textEmpty.visibility = View.GONE
        textError.visibility = View.GONE
        recycler.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val projects = withContext(Dispatchers.IO) {
                    GitHubGalleryRepository.listProjects()
                }

                progress.visibility = View.GONE

                if (projects.isEmpty()) {
                    textEmpty.visibility = View.VISIBLE
                } else {
                    recycler.visibility = View.VISIBLE
                    adapter.submit(projects)
                }
            } catch (e: Exception) {
                progress.visibility = View.GONE
                textError.visibility = View.VISIBLE
                textError.text =
                    "No se pudo cargar la galería.\n\n${e.message ?: e.javaClass.simpleName}\n\nComprobá la conexión e intentá de nuevo."
            }
        }
    }

    private fun showProjectDialog(project: GalleryProject) {
        val view = LayoutInflater.from(this)
            .inflate(R.layout.dialog_gallery_project, null)

        val image = view.findViewById<ImageView>(R.id.imageDialogPreview)
        val name = view.findViewById<TextView>(R.id.textDialogName)
        val status = view.findViewById<TextView>(R.id.textDialogStatus)
        val progressBar = view.findViewById<ProgressBar>(R.id.progressDialogDownload)
        val buttonCancel = view.findViewById<MaterialButton>(R.id.buttonDialogCancel)
        val buttonDownload = view.findViewById<MaterialButton>(R.id.buttonDialogDownload)
        val buttonApply = view.findViewById<MaterialButton>(R.id.buttonDialogApply)

        name.text = project.name
        buttonApply.isEnabled = false

        // Si el ZIP ya está en privado o en Documentos → se puede aplicar
        // (mismo espíritu que Importar: abrir stream, no exigir re-descarga)
        val alreadyPresent = AppDirectories.galleryZipPresent(this, project.zipFileName)
        if (alreadyPresent) {
            status.text = "Ya descargado. Podés aplicar el proyecto."
            buttonApply.isEnabled = true
            buttonDownload.text = "Re-descargar"
        } else {
            status.text = "Pendiente de descarga"
            buttonApply.isEnabled = false
        }

        // Preview del diálogo
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val connection =
                    URL(project.previewUrl).openConnection() as HttpURLConnection
                connection.connectTimeout = 15_000
                connection.readTimeout = 20_000
                connection.instanceFollowRedirects = true
                connection.setRequestProperty(
                    "User-Agent",
                    "Romaster-LiveWall-Engine"
                )
                val bitmap = connection.inputStream.use {
                    BitmapFactory.decodeStream(it)
                }
                connection.disconnect()
                if (bitmap != null) {
                    withContext(Dispatchers.Main) {
                        image.setImageBitmap(bitmap)
                    }
                }
            } catch (_: Exception) {
            }
        }

        val dialog = AlertDialog.Builder(this)
            .setView(view)
            .setCancelable(true)
            .create()

        buttonCancel.setOnClickListener {
            dialog.dismiss()
        }

        buttonDownload.setOnClickListener {
            buttonDownload.isEnabled = false
            buttonApply.isEnabled = false
            progressBar.visibility = View.VISIBLE
            status.text = "Descargando…"

            lifecycleScope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        val privateDir = AppDirectories.galleryDownloads(this@ProjectGalleryActivity)
                        val temp = File(privateDir, "${project.zipFileName}.part")
                        if (temp.exists()) temp.delete()

                        GitHubGalleryRepository.downloadZip(
                            project.zipUrl,
                            temp
                        )

                        AppDirectories.saveGalleryZip(
                            this@ProjectGalleryActivity,
                            project.zipFileName,
                            temp
                        )
                    }

                    progressBar.visibility = View.GONE
                    status.text = "Descarga completa. Ya podés aplicar el proyecto."
                    buttonApply.isEnabled = true
                    buttonDownload.isEnabled = true
                    buttonDownload.text = "Re-descargar"
                    Toast.makeText(
                        this@ProjectGalleryActivity,
                        "Proyecto guardado",
                        Toast.LENGTH_SHORT
                    ).show()
                } catch (e: Exception) {
                    progressBar.visibility = View.GONE
                    buttonDownload.isEnabled = true
                    // Si ya había un ZIP usable, reactivar Aplicar
                    if (AppDirectories.galleryZipPresent(
                            this@ProjectGalleryActivity,
                            project.zipFileName
                        )
                    ) {
                        buttonApply.isEnabled = true
                    }
                    status.text =
                        "Error al descargar: ${e.message ?: e.javaClass.simpleName}"
                    Toast.makeText(
                        this@ProjectGalleryActivity,
                        "No se pudo descargar",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

        buttonApply.setOnClickListener {
            if (!AppDirectories.galleryZipPresent(this, project.zipFileName)) {
                Toast.makeText(
                    this,
                    "Primero descargá el proyecto",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            buttonApply.isEnabled = false
            status.text = "Aplicando proyecto…"

            lifecycleScope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        // Mismo mecanismo que Importar: abrir stream y aplicar
                        val stream = AppDirectories.openGalleryZipStream(
                            this@ProjectGalleryActivity,
                            project.zipFileName
                        ) ?: throw IllegalStateException(
                            "No se pudo abrir el ZIP (permiso o archivo dañado)"
                        )
                        stream.use { input ->
                            ProjectImporter.importFromInputStream(
                                this@ProjectGalleryActivity,
                                input
                            )
                        }
                    }

                    Toast.makeText(
                        this@ProjectGalleryActivity,
                        "Proyecto aplicado: ${project.name}",
                        Toast.LENGTH_SHORT
                    ).show()

                    setResult(Activity.RESULT_OK)
                    dialog.dismiss()
                    finish()
                } catch (e: Exception) {
                    buttonApply.isEnabled = true
                    status.text =
                        "Error al aplicar: ${e.message ?: e.javaClass.simpleName}"
                    Toast.makeText(
                        this@ProjectGalleryActivity,
                        "No se pudo aplicar el proyecto",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

        dialog.show()
    }
}
