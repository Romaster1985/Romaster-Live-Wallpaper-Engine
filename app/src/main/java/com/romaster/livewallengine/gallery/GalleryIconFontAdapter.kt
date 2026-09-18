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

import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.romaster.livewallengine.R
import com.romaster.livewallengine.font.IconFontStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Grilla de fuentes de íconos: preview con los primeros glifos del JSON.
 */
class GalleryIconFontAdapter(
    private val scope: CoroutineScope,
    private val cacheDir: File,
    private val onClick: (GalleryIconFontItem) -> Unit
) : RecyclerView.Adapter<GalleryIconFontAdapter.VH>() {

    private val items = mutableListOf<GalleryIconFontItem>()
    private val jobs = mutableMapOf<Int, Job>()

    fun submit(list: List<GalleryIconFontItem>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_gallery_font, parent, false)
        return VH(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.name.text = item.name
        holder.preview.typeface = Typeface.DEFAULT
        holder.preview.text = "…"
        holder.progress.visibility = View.VISIBLE
        holder.itemView.setOnClickListener { onClick(item) }

        jobs[position]?.cancel()
        jobs[position] = scope.launch {
            try {
                val ttfLocal = File(cacheDir, item.ttfFileName)
                val jsonLocal = File(cacheDir, item.jsonFileName)
                withContext(Dispatchers.IO) {
                    if (!ttfLocal.exists() || ttfLocal.length() == 0L) {
                        GitHubGalleryRepository.downloadFile(item.ttfUrl, ttfLocal)
                    }
                    if (!jsonLocal.exists() || jsonLocal.length() == 0L) {
                        GitHubGalleryRepository.downloadFile(item.jsonUrl, jsonLocal)
                    }
                }
                val (tf, sample) = withContext(Dispatchers.IO) {
                    val typeface = try {
                        Typeface.createFromFile(ttfLocal)
                    } catch (_: Exception) {
                        Typeface.DEFAULT
                    }
                    val glyphs = try {
                        IconFontStorage.parseIcoMoon(jsonLocal.readText())
                    } catch (_: Exception) {
                        emptyMap()
                    }
                    // Primeros 8–10 glifos para el preview
                    val sampleText = glyphs.values
                        .distinct()
                        .take(10)
                        .joinToString(" ")
                        .ifBlank { "?" }
                    typeface to sampleText
                }
                if (holder.bindingAdapterPosition == position) {
                    holder.preview.typeface = tf
                    holder.preview.text = sample
                    holder.preview.textSize = 22f
                    holder.progress.visibility = View.GONE
                }
            } catch (_: Exception) {
                if (holder.bindingAdapterPosition == position) {
                    holder.preview.text = "Error"
                    holder.progress.visibility = View.GONE
                }
            }
        }
    }

    override fun onViewRecycled(holder: VH) {
        super.onViewRecycled(holder)
        val pos = holder.bindingAdapterPosition
        if (pos != RecyclerView.NO_POSITION) {
            jobs[pos]?.cancel()
        }
    }

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val preview: TextView = view.findViewById(R.id.textFontPreview)
        val name: TextView = view.findViewById(R.id.textFontName)
        val progress: ProgressBar = view.findViewById(R.id.progressFontPreview)
    }
}
